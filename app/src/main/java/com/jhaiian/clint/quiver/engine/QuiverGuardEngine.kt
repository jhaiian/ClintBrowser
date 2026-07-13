package com.jhaiian.clint.quiver.engine

import android.content.Context
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Owns the single active adblock-rust engine handle for the process.
 *
 * The native `Engine` is safe for concurrent reads from multiple threads (WebView dispatches
 * `shouldInterceptRequest` off a pool, not just the UI thread), but swapping the pointer on
 * reload is not - a query mid-call on the old handle while it's destroyed is a use-after-free.
 * A [ReentrantReadWriteLock] makes that swap safe: queries take the read lock (fully concurrent
 * with each other), [activate] takes the write lock (waits for in-flight queries, then swaps).
 */
object QuiverGuardEngine {

    private val lock = ReentrantReadWriteLock()
    private var handle: Long = 0L

    val isLoaded: Boolean
        get() = lock.read { handle != 0L }

    /** Loads the on-disk compiled engine, if one exists and nothing is loaded yet. */
    fun preload(context: Context) {
        lock.read { if (handle != 0L) return }
        val file = QuiverGuardPaths.databaseFile(context)
        if (!file.exists()) return
        val newHandle = QuiverGuardNative.nativeLoadEngine(file.absolutePath)
        if (newHandle == 0L) return
        lock.write {
            if (handle != 0L) {
                // Lost a race with another preload/activate; drop what we just loaded.
                QuiverGuardNative.nativeDestroyEngine(newHandle)
            } else {
                handle = newHandle
            }
        }
    }

    /** Atomically activates a freshly compiled engine file, replacing whatever was active. */
    fun activate(path: String): Boolean {
        val newHandle = QuiverGuardNative.nativeLoadEngine(path)
        if (newHandle == 0L) return false
        lock.write {
            val old = handle
            handle = newHandle
            if (old != 0L) QuiverGuardNative.nativeDestroyEngine(old)
        }
        return true
    }

    data class NetworkCheck(
        val matched: Boolean,
        /** A `data:<mime>;base64,<content>` URI, present only for `$redirect=`-matched rules. */
        val redirectDataUrl: String?,
        val rewrittenUrl: String?,
        val csp: String?,
    )

    fun checkNetworkRequest(url: String, sourceUrl: String, requestType: String, method: String): NetworkCheck? =
        lock.read {
            if (handle == 0L) return@read null
            parseNetworkCheck(
                QuiverGuardNative.nativeCheckNetworkRequest(handle, url, sourceUrl, requestType, method)
            )
        }

    data class CosmeticResources(
        val hideSelectors: List<String>,
        /** JSON-encoded operator chains - see quiver_guard_cosmetic.js for the interpreter. */
        val proceduralActions: List<String>,
        val genericHide: Boolean,
        val injectedScript: String,
        /** Feed into [hiddenClassIdSelectors]; meaningless on its own. */
        val exceptions: List<String>,
    )

    fun urlCosmeticResources(url: String): CosmeticResources? =
        lock.read {
            if (handle == 0L) return@read null
            parseCosmeticResources(QuiverGuardNative.nativeUrlCosmeticResources(handle, url))
        }

    /**
     * Generic (non-hostname-specific) hiding rules matching the given class/id tokens, which
     * should be ones actually observed in the current page's DOM - see the kdoc on the native
     * declaration for why this can't just be folded into [urlCosmeticResources]. Returns null
     * (rather than an empty list) if the engine isn't loaded, same as the other query methods.
     */
    fun hiddenClassIdSelectors(classes: List<String>, ids: List<String>, exceptions: List<String>): List<String>? =
        lock.read {
            if (handle == 0L) return@read null
            try {
                JSONArray(
                    QuiverGuardNative.nativeHiddenClassIdSelectors(
                        handle,
                        JSONArray(classes).toString(),
                        JSONArray(ids).toString(),
                        JSONArray(exceptions).toString(),
                    )
                ).toStringList()
            } catch (e: JSONException) {
                null
            }
        }

    /**
     * Raw passthrough for [QuiverGuardJsBridge] - unlike [urlCosmeticResources] and
     * [checkNetworkRequest], this hands the native JSON straight back rather than parsing it
     * into a Kotlin object, since the bridge's only job is to relay it to page JS, which parses
     * it itself. Always returns well-formed JSON (an `{"error": "..."}` object rather than null
     * when nothing is loaded), since - unlike Kotlin call sites - page JS has no equivalent of a
     * nullable return to fall back on.
     */
    fun urlCosmeticResourcesJson(url: String): String =
        lock.read {
            if (handle == 0L) return@read """{"error":"engine not loaded"}"""
            QuiverGuardNative.nativeUrlCosmeticResources(handle, url)
        }

    /** Same idea as [urlCosmeticResourcesJson], for the network-check side (used for the page's
     * own address-bar $removeparam= cleanup - see [QuiverGuardJsBridge]). */
    fun checkNetworkRequestJson(url: String, sourceUrl: String, requestType: String, method: String): String =
        lock.read {
            if (handle == 0L) return@read """{"error":"engine not loaded"}"""
            QuiverGuardNative.nativeCheckNetworkRequest(handle, url, sourceUrl, requestType, method)
        }

    private fun parseNetworkCheck(json: String): NetworkCheck? = try {
        val obj = JSONObject(json)
        if (obj.has("error")) {
            null
        } else {
            NetworkCheck(
                matched = obj.optBoolean("matched", false),
                redirectDataUrl = obj.stringOrNull("redirect"),
                rewrittenUrl = obj.stringOrNull("rewrittenUrl"),
                csp = obj.stringOrNull("csp"),
            )
        }
    } catch (e: JSONException) {
        null
    }

    private fun parseCosmeticResources(json: String): CosmeticResources? = try {
        val obj = JSONObject(json)
        if (obj.has("error")) {
            null
        } else {
            CosmeticResources(
                hideSelectors = obj.optJSONArray("hideSelectors").toStringList(),
                proceduralActions = obj.optJSONArray("proceduralActions").toStringList(),
                genericHide = obj.optBoolean("genericHide", false),
                injectedScript = obj.optString("injectedScript", ""),
                exceptions = obj.optJSONArray("exceptions").toStringList(),
            )
        }
    } catch (e: JSONException) {
        null
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, null)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }
    }
}

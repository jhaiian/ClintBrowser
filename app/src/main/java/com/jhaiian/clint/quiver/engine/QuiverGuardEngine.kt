package com.jhaiian.clint.quiver.engine

import android.content.Context
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object QuiverGuardEngine {

    private val lock = ReentrantReadWriteLock()
    private var handle: Long = 0L

    val isLoaded: Boolean
        get() = lock.read { handle != 0L }

    enum class PreloadResult {

        LOADED,

        NO_DATABASE,

        FAILED,

        VERSION_MISMATCH,

        BAD_HEADER,

        BAD_CHECKSUM,

        FLATBUFFER_PARSING_ERROR;

        val requiresRecompile: Boolean
            get() = this != LOADED && this != NO_DATABASE && this != FAILED
    }

    fun preload(context: Context): PreloadResult {
        lock.read { if (handle != 0L) return PreloadResult.LOADED }
        val file = QuiverGuardPaths.databaseFile(context)
        if (!file.exists()) return PreloadResult.NO_DATABASE
        val newHandle = QuiverGuardNative.nativeLoadEngine(file.absolutePath)
        val failure = when (newHandle) {
            0L -> PreloadResult.FAILED
            -1L -> PreloadResult.VERSION_MISMATCH
            -2L -> PreloadResult.BAD_HEADER
            -3L -> PreloadResult.BAD_CHECKSUM
            -4L -> PreloadResult.FLATBUFFER_PARSING_ERROR
            else -> null
        }
        if (failure != null) return failure
        lock.write {
            if (handle != 0L) {

                QuiverGuardNative.nativeDestroyEngine(newHandle)
            } else {
                handle = newHandle
            }
        }
        return PreloadResult.LOADED
    }

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

        val proceduralActions: List<String>,
        val genericHide: Boolean,
        val injectedScript: String,

        val exceptions: List<String>,
    )

    fun urlCosmeticResources(url: String): CosmeticResources? =
        lock.read {
            if (handle == 0L) return@read null
            parseCosmeticResources(QuiverGuardNative.nativeUrlCosmeticResources(handle, url))
        }

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

    fun urlCosmeticResourcesJson(url: String): String =
        lock.read {
            if (handle == 0L) return@read """{"error":"engine not loaded"}"""
            QuiverGuardNative.nativeUrlCosmeticResources(handle, url)
        }

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
        if (isNull(key)) null else getString(key)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }
    }
}

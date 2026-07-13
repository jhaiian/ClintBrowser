package com.jhaiian.clint.quiver.engine

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONException

/**
 * A minimal, read-only bridge letting injected JS fetch its own cosmetic-filtering data instead
 * of receiving it precomputed as script arguments.
 *
 * The document-start script (quiver_guard_cosmetic.js) is registered once, statically, the same
 * for every page - see [QuiverGuardWebIntegration] for why: computing a page-specific script in
 * Kotlin ahead of time meant a native engine call had to complete, on a background thread,
 * before the *current* navigation reached its document-start point, which - especially for the
 * very first navigation in a fresh WebView - it systematically couldn't win. Fetching through
 * this bridge instead means the script that ends up running doesn't matter; whichever one is
 * registered when a page starts just looks up *that* page's own URL at the moment it executes.
 *
 * This is registered on every WebView Quiver Guard touches (see
 * [QuiverGuardWebIntegration.ensureBridgeInstalled]), which means every method here is callable
 * by *any* JavaScript running in that WebView, not just Quiver Guard's own injected script -
 * including whatever a malicious or compromised page runs. It's deliberately narrow: every
 * method just answers "what do the user's own (already on-device, already public) filter list
 * rules say about this specific URL/these specific tokens" - the same information a page can
 * already infer by noticing its own ad slots are hidden or its own tracking params get stripped.
 * Nothing here can modify browser state, read other data, or touch the filesystem.
 */
internal object QuiverGuardJsBridge {

    /** Returns the same JSON shape as [QuiverGuardNative.nativeUrlCosmeticResources]. */
    @JavascriptInterface
    fun urlCosmeticResources(url: String): String = QuiverGuardEngine.urlCosmeticResourcesJson(url)

    /**
     * Returns the same JSON shape as [QuiverGuardNative.nativeCheckNetworkRequest], checking the
     * page's own URL against itself (method "GET") purely for the "rewrittenUrl" field - used
     * for `$removeparam=`-style address-bar cleanup. The other fields (matched/redirect/csp etc.)
     * are meaningless for this and should be ignored; subresources are still blocked over the
     * normal network-interception path, not through this bridge.
     */
    @JavascriptInterface
    fun rewrittenUrl(url: String): String = QuiverGuardEngine.checkNetworkRequestJson(url, url, "document", "GET")

    /** See [QuiverGuardNative.nativeHiddenClassIdSelectors]'s kdoc for why this one exists. */
    @JavascriptInterface
    fun hiddenClassIdSelectors(classesJson: String, idsJson: String, exceptionsJson: String): String {
        val classes = parseStringArray(classesJson)
        val ids = parseStringArray(idsJson)
        val exceptions = parseStringArray(exceptionsJson)
        val selectors = QuiverGuardEngine.hiddenClassIdSelectors(classes, ids, exceptions) ?: emptyList()
        return JSONArray(selectors).toString()
    }

    private fun parseStringArray(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: JSONException) {
        emptyList()
    }
}

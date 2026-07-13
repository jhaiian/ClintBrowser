package com.jhaiian.clint.quiver.engine

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.Base64
import java.util.Collections
import java.util.WeakHashMap

// Façade that connects the browser's WebViewClient callbacks to the Quiver Guard
// engine. All methods are safe to call from any thread; they delegate to
// QuiverGuardEngine, which handles the native adblock-rust handle's thread-safety
// internally (see its kdoc for the read/write locking rationale).
//
// Cosmetic filtering (buildCosmeticFilterScript/buildDocumentStartScript) hands out
// the *same* static script for every page, rather than one precomputed per-URL. The
// script fetches its own page-specific data at the moment it runs, through the
// bridge object installed on the WebView (see QuiverGuardJsBridge), instead of
// receiving it as precomputed arguments. That fixed a real bug: the previous design
// computed a page-specific script via a native engine call on a background thread,
// then registered it with WebViewCompat.addDocumentStartJavaScript - but that API
// only takes effect starting with the *next* navigation after it's called, and by
// the time the async computation finished, the current navigation had already
// passed its own document-start point.
//
// That fix alone turned out not to be enough, though - see [installEarly]. Both
// addJavascriptInterface (the bridge) and addDocumentStartJavaScript (the script
// itself) share the same "next navigation" limitation, independent of how fast or
// slow the Kotlin side computing their content is. Registering either one
// reactively, in response to onPageStarted, is *always* one navigation behind - not
// a slow-async-call problem, a fundamentally-too-late problem - which is why a
// WebView's first-ever page load (a fresh tab, or every tab after an app restart)
// kept showing unfiltered content even after the script itself became instant to
// compute. [installEarly] is the actual fix: call it once, when the WebView is
// created, before there's any navigation for "next navigation" to be relative to.
object QuiverGuardWebIntegration {

    // Short and unlabeled so a page probing `window` for recognizable adblock-related
    // property names doesn't get an easy signal from this specifically. This isn't a hard
    // security boundary - see QuiverGuardJsBridge's kdoc - just a small amount of
    // friction against the most naive detection scripts.
    private const val JS_BRIDGE_NAME = "__qgBridge"

    // In-memory cache for the cosmetic-filtering JS bundled as an asset. It never
    // changes at runtime (and is the same script for every page - see the class kdoc),
    // so it's read from the APK's asset store and wrapped exactly once rather than on
    // every single page navigation.
    @Volatile private var bootstrapScript: String? = null

    private fun bootstrapScript(context: Context): String =
        bootstrapScript ?: synchronized(this) {
            bootstrapScript ?: run {
                val body = context.assets.open("JavaScript/quiver_guard_cosmetic.js")
                    .bufferedReader().use { it.readText() }
                // Wrapped in a bare, argument-less IIFE purely for scoping - unlike the old
                // per-navigation version, there's nothing page-specific to pass in as closure
                // arguments anymore, since the script fetches that itself through the bridge.
                "(function(){\n$body\n})();".also { bootstrapScript = it }
            }
        }

    // Ensures the engine is loaded from the compiled database. Called once when
    // Quiver Guard is enabled so the first intercepted request does not incur
    // the cold-start latency of loading the database file. Safe to call from a
    // background thread, and callers should do so, since this can involve real
    // disk I/O on first load.
    fun initialize(context: Context) {
        QuiverGuardEngine.preload(context)
    }

    // Called after a successful compile run to atomically replace the running
    // engine with the one just written to QuiverGuardPaths.databaseFile.
    fun onCompileComplete(context: Context) {
        QuiverGuardEngine.activate(QuiverGuardPaths.databaseFile(context).absolutePath)
    }

    // Evaluates whether a network request should be blocked and returns an empty
    // WebResourceResponse if so, or null to allow the request to proceed normally.
    // Called from WebViewClient.shouldInterceptRequest on a background thread.
    // Non-HTTP(S)/WS(S) scheme requests are always allowed without engine lookup.
    fun shouldInterceptRequest(
        context: Context,
        request: WebResourceRequest,
        pageUrl: String?,
        tabId: String,
        isQuiverGuardEnabled: Boolean
    ): WebResourceResponse? {
        if (!isQuiverGuardEnabled) return null
        if (!QuiverGuardEngine.isLoaded) {
            QuiverGuardEngine.preload(context)
            if (!QuiverGuardEngine.isLoaded) return null
        }

        val requestUrl = request.url ?: return null
        val scheme = requestUrl.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https" && scheme != "ws" && scheme != "wss") return null

        val urlStr = requestUrl.toString()
        val resourceType = ResourceTypeDetector.detect(request)
        // adblock-rust computes third-party status itself from the two URLs, so the
        // page URL (falling back to the request's own URL for a top-level nav with
        // no known referrer yet) is all it needs - no separate isThirdParty helper.
        val sourceUrl = pageUrl ?: urlStr

        val check = QuiverGuardEngine.checkNetworkRequest(urlStr, sourceUrl, resourceType, request.method ?: "GET") ?: return null

        if (check.matched) {
            BlockedRequestCounter.increment(tabId)
            // If the matched rule carries a $redirect= target, serve the resolved
            // stub resource instead of a plain empty response. This allows filter
            // rules like "||html-load.com^$redirect=noopjs" to return valid JavaScript
            // so the page treats the load as successful rather than detecting a failure.
            check.redirectDataUrl?.let { decodeDataUrlResponse(it) }?.let { return it }
            return BlockedResponse.forResourceType(resourceType)
        }

        // The request is otherwise allowed, but a non-blocking modifier ($csp=,
        // $removeparam=) may still apply. shouldInterceptRequest can only change
        // what a request receives by re-issuing it, so this is only attempted for
        // GET/HEAD - PassthroughFetcher.fetch() enforces that itself and falls back
        // to null (letting the WebView load the original request untouched) on any
        // unsupported method or failure, so a modifier can never break a request
        // worse than not applying it at all.
        val addHeaders = LinkedHashMap<String, String>()
        check.csp?.let { addHeaders["Content-Security-Policy"] = it }

        if (check.rewrittenUrl != null || addHeaders.isNotEmpty()) {
            val modification = PassthroughFetcher.Modification(
                newUrl = check.rewrittenUrl,
                addResponseHeaders = addHeaders,
            )
            val modifiedResponse = PassthroughFetcher.fetch(request, modification)
            if (modifiedResponse != null) return modifiedResponse
        }

        return null
    }

    // Decodes a `data:<mime>;base64,<content>` URI - the shape adblock-rust's
    // redirect resources always resolve to - into a WebResourceResponse, using the
    // same CORS-safety headers as an ordinary blocked response so a redirected
    // stub is just as undetectable to page scripts as an empty block.
    private fun decodeDataUrlResponse(dataUrl: String): WebResourceResponse? {
        if (!dataUrl.startsWith("data:")) return null
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex == -1) return null
        val meta = dataUrl.substring(5, commaIndex)
        if (!meta.endsWith(";base64")) return null
        val mime = meta.removeSuffix(";base64").ifBlank { "application/octet-stream" }
        val bytes = try {
            Base64.getDecoder().decode(dataUrl.substring(commaIndex + 1))
        } catch (_: IllegalArgumentException) {
            return null
        }
        return WebResourceResponse(
            mime, "binary", 200, "OK",
            BlockedResponse.BASE_HEADERS + mapOf("Content-Type" to mime),
            bytes.inputStream()
        )
    }

    // Returns the cosmetic-filtering bootstrap script, or null if there's nothing to do.
    // isQuiverGuardEnabled/pageUrl are honored the same as before (pageUrl is otherwise
    // unused now - see the class kdoc for why the script itself looks up the page URL at
    // run time instead - but kept so this doesn't need a signature change at call sites).
    fun buildCosmeticFilterScript(
        context: Context,
        @Suppress("UNUSED_PARAMETER") pageUrl: String,
        isQuiverGuardEnabled: Boolean
    ): String? {
        if (!isQuiverGuardEnabled) return null
        return bootstrapScript(context)
    }

    // Injects the cosmetic-filtering script into a page by calling evaluateJavascript
    // after load. Used as a fallback on API levels that do not support the
    // document-start injection API; the script runs after the page has already
    // rendered, so cosmetic filters may cause a visible flash before they take effect.
    //
    // `script` must be precomputed via [buildCosmeticFilterScript]. This function
    // itself only touches the WebView and must be called on the UI thread.
    fun applyCosmeticFilterScript(webView: WebView, script: String) {
        ensureBridgeInstalled(webView)
        webView.evaluateJavascript(script, null)
    }

    // Returns the cosmetic-filtering bootstrap script for document-start registration,
    // or null if there's nothing to do or the device's WebView doesn't support the
    // document-start API. pageUrl is otherwise unused - see the class kdoc.
    fun buildDocumentStartScript(
        context: Context,
        @Suppress("UNUSED_PARAMETER") pageUrl: String,
        isQuiverGuardEnabled: Boolean
    ): String? {
        if (!isQuiverGuardEnabled) return null
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return null
        return bootstrapScript(context)
    }

    // Registers the cosmetic-filtering script for the current tab using the WebView
    // Jetpack API so it executes before any page script. The previous handler for
    // this tab is removed first - not because the script differs between
    // navigations (it doesn't - see the class kdoc), but so a tab doesn't
    // accumulate a duplicate handler on every navigation.
    //
    // `script` must be precomputed via [buildDocumentStartScript]. This function
    // itself only touches the WebView/tab handler store and must be called on the
    // UI thread.
    fun applyDocumentStartScript(
        webView: WebView,
        tabScriptHandlers: ScriptHandlerStore,
        tabId: String,
        script: String
    ) {
        ensureBridgeInstalled(webView)
        tabScriptHandlers.remove(tabId)
        val handler = WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        tabScriptHandlers.put(tabId, handler)
    }

    /**
     * Call once, from wherever a WebView is actually constructed (see
     * `MainWebViewDelegate.createWebView`, which also gates this on the
     * `quiver_guard_enabled` preference - Quiver Guard is opt-in, so this must not run
     * unconditionally), before its first navigation - this is what makes cosmetic filtering
     * work on a WebView's very first page load, including after an app restart or in a
     * freshly-opened tab, which the rest of this class's reactive, per-navigation registration
     * (triggered from onPageStarted) structurally cannot: both addJavascriptInterface and
     * addDocumentStartJavaScript only take effect starting with the navigation *after* they're
     * called, no matter how early in that navigation's lifecycle you call them - by the time a
     * WebViewClient callback fires, that navigation has already begun. The fix isn't calling
     * this "faster" from onPageStarted; a callback reacting to a navigation can never win a race
     * against that same navigation's own document-start point. It's calling it before there's
     * any navigation to race against.
     *
     * This also happens to be what makes iframes work correctly without any special-casing:
     * addDocumentStartJavaScript's "*" origin rule already applies to every frame, and since the
     * registration now predates *any* navigation in this WebView, that includes frames a page
     * creates dynamically well after its own load - each just runs the same script, at its own
     * document-start, looking up its own location.href.
     */
    fun installEarly(context: Context, webView: WebView) {
        ensureBridgeInstalled(webView)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, bootstrapScript(context), setOf("*"))
        }
    }

    // WebViews that already have the bridge registered - a WeakHashMap-backed set so this
    // never keeps a WebView (or its Activity/Context) alive after it would otherwise be
    // garbage collected. addJavascriptInterface itself is idempotent (re-registering under
    // the same name just replaces the previous binding), so this is purely to skip the
    // redundant call rather than for correctness.
    private val bridgedWebViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

    // The real installation now happens eagerly via [installEarly]; this remains as a
    // defensive fallback for [applyDocumentStartScript]/[applyCosmeticFilterScript] in case
    // some other, currently nonexistent, code path ever constructs a WebView without going
    // through it. Must be called on the UI thread, same as any other WebView method.
    private fun ensureBridgeInstalled(webView: WebView) {
        if (bridgedWebViews.add(webView)) {
            webView.addJavascriptInterface(QuiverGuardJsBridge, JS_BRIDGE_NAME)
        }
    }
}

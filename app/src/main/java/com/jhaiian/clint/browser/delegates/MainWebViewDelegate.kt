package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.webview.*
import com.jhaiian.clint.browser.MainActivity

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration
import com.jhaiian.clint.tabs.BrowserTab
import com.jhaiian.clint.util.registeredDomain
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@SuppressLint("SetJavaScriptEnabled")
internal fun MainActivity.createWebView(isIncognito: Boolean): WebView {
    val webView = WebView(this)
    val settings = webView.settings
    settings.javaScriptEnabled = prefs.getBoolean("javascript_enabled", true)
    settings.domStorageEnabled = !isIncognito
    settings.cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
    settings.setSupportZoom(true)
    settings.setSupportMultipleWindows(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    val dataSaverEnabled = prefs.getBoolean("data_saver_enabled", false)
    settings.mediaPlaybackRequiresUserGesture = dataSaverEnabled && prefs.getBoolean("data_saver_disable_autoplay", true)
    settings.loadsImagesAutomatically = !(dataSaverEnabled && prefs.getBoolean("data_saver_disable_images", true))
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.safeBrowsingEnabled = false
    settings.userAgentString = buildUserAgent()
    applyUserAgentMetadata(webView)
    val cookieManager = CookieManager.getInstance()
    if (isIncognito) {
        cookieManager.setAcceptCookie(false)
    } else {
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, !prefs.getBoolean("block_third_party_cookies", true))
    }
    webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
    webView.addJavascriptInterface(NestedScrollBridge(), "NestedScrollBridge")
    webView.addJavascriptInterface(CanvasTouchBridge(), "CanvasTouchBridge")
    webView.addJavascriptInterface(BottomNavBridge(), "BottomNavBridge")
    webView.addJavascriptInterface(NotificationBridge(webView), "ClintNotificationBridge")
    webView.addJavascriptInterface(BlobDownloadBridge(), "BlobDownloadBridge")
    // Registered here, not reactively per-navigation like the rest of Quiver Guard's
    // integration - addJavascriptInterface/addDocumentStartJavaScript both only take
    // effect starting with the navigation *after* they're called, so doing this in
    // response to onPageStarted is always one navigation too late for a WebView's
    // first-ever page load. See QuiverGuardWebIntegration.installEarly's kdoc.
    if (prefs.getBoolean("quiver_guard_enabled", false)) {
        QuiverGuardWebIntegration.installEarly(this, webView)
    }
    webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
        if (tabManager.activeTab?.webView !== webView) return@setDownloadListener
        if (url.startsWith("blob:")) {
            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val safeUrl = url.replace("\\", "\\\\").replace("'", "\\'")
            val safeFilename = filename.replace("\\", "\\\\").replace("'", "\\'")
            val safeMime = mimetype.replace("\\", "\\\\").replace("'", "\\'")
            webView.evaluateJavascript("""
                (function(){
                    fetch('$safeUrl')
                        .then(function(r){return r.blob();})
                        .then(function(blob){
                            var reader=new FileReader();
                            reader.onloadend=function(){
                                var b64=reader.result.split(',')[1];
                                BlobDownloadBridge.receiveBlob(b64,'$safeFilename',blob.type||'$safeMime');
                            };
                            reader.readAsDataURL(blob);
                        })
                        .catch(function(e){BlobDownloadBridge.onError(e.toString());});
                })();
            """.trimIndent(), null)
            return@setDownloadListener
        }
        var filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
        if (filename.endsWith(".bin")) {
            val urlPath = url.substringBefore("?").substringBefore("#")
            val urlExt = urlPath.substringAfterLast(".", "")
            if (urlExt.isNotEmpty() && urlExt.length in 1..10 && urlExt != "bin" && !urlExt.contains("/")) {
                filename = filename.removeSuffix(".bin") + ".$urlExt"
            }
        }
        val referer = webView.url ?: ""
        val cookies = CookieManager.getInstance().getCookie(url) ?: ""
        val session = refreshLinkSession
        if (session != null && tabManager.activeTab?.isRefreshLinkTab == true && isRelatedToSession(url, referer, session)) {
            showRefreshLinkDownloadDialog(url, filename, userAgent, referer, cookies, session)
        } else {
            showDownloadDialog(url, filename, userAgent, referer, cookies)
        }
    }
    applyWebDarkMode(webView)
    setupImageLongPress(webView)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(webView, loadJsAsset("link_touch_tracker.js"), setOf("*"))
        WebViewCompat.addDocumentStartJavaScript(webView, loadJsAsset("web_notification_bridge.js"), setOf("*"))
    }
    val dataSaverActive = prefs.getBoolean("data_saver_enabled", false)
        && prefs.getBoolean("data_saver_disable_autoplay", true)
    if (dataSaverActive && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(webView, loadJsAsset("disable_autoplay.js"), setOf("*"))
    }
    return webView
}

internal fun MainActivity.buildDesktopHeaders(): Map<String, String>? {
    if (!isDesktopMode) return null
    val defaultUA = WebSettings.getDefaultUserAgent(this)
    val majorVersion = Regex("Chrome/(\\d+)").find(defaultUA)?.groupValues?.get(1) ?: "134"
    val secChUa = "\"Chromium\";v=\"" + majorVersion + "\", \"Not-A.Brand\";v=\"24\", \"Google Chrome\";v=\"" + majorVersion + "\""
    return mapOf(
        "Sec-CH-UA" to secChUa,
        "Sec-CH-UA-Mobile" to "?0",
        "Sec-CH-UA-Platform" to "\"Windows\""
    )
}

internal fun MainActivity.buildUserAgent(): String {
    val defaultUA = WebSettings.getDefaultUserAgent(this)
    val chromeVersion = Regex("Chrome/([\\d.]+)").find(defaultUA)?.groupValues?.get(1) ?: "134.0.0.0"
    val androidVersion = android.os.Build.VERSION.RELEASE
    return when {
        isDesktopMode ->
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chromeVersion + " Safari/537.36"
        prefs.getBoolean("custom_user_agent", true) ->
            "Mozilla/5.0 (Linux; Android " + androidVersion + "; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chromeVersion + " Mobile Safari/537.36"
        else ->
            defaultUA
    }
}

@Suppress("DEPRECATION")
internal fun MainActivity.applyWebDarkMode(webView: WebView) {
    val theme = prefs.getString("app_theme", "dark") ?: "dark"
    val enabled = when (theme) {
        "dark" -> true
        "light" -> false
        else -> prefs.getBoolean("force_dark_web", false)
    }
    val settings = webView.settings
    when {
        WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) ->
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled)
        WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) ->
            WebSettingsCompat.setForceDark(
                settings,
                if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
            )
    }
}

internal fun MainActivity.addAutoplayScript(tab: BrowserTab) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    removeAutoplayScript(tab)
    autoplayScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, loadJsAsset("disable_autoplay.js"), setOf("*"))
}

internal fun MainActivity.removeAutoplayScript(tab: BrowserTab) {
    autoplayScriptHandlers.remove(tab.id)?.remove()
}

internal fun MainActivity.applyDataSaverSettings() {
    val dataSaverEnabled = prefs.getBoolean("data_saver_enabled", false)
    val disableImages = dataSaverEnabled && prefs.getBoolean("data_saver_disable_images", true)
    val disableAutoplay = dataSaverEnabled && prefs.getBoolean("data_saver_disable_autoplay", true)
    tabManager.tabs.forEach { tab ->
        tab.webView.settings.loadsImagesAutomatically = !disableImages
        tab.webView.settings.mediaPlaybackRequiresUserGesture = disableAutoplay
        if (disableAutoplay) addAutoplayScript(tab) else removeAutoplayScript(tab)
    }
    tabManager.activeTab?.webView?.reload()
}

internal fun MainActivity.applyJavaScript() {
    val enabled = prefs.getBoolean("javascript_enabled", true)
    tabManager.tabs.forEach { it.webView.settings.javaScriptEnabled = enabled }
    tabManager.activeTab?.webView?.reload()
}

internal fun MainActivity.applyCookiePolicy() {
    val blockThirdParty = prefs.getBoolean("block_third_party_cookies", true)
    val cookieManager = CookieManager.getInstance()
    tabManager.tabs.forEach { tab ->
        if (!tab.isIncognito) cookieManager.setAcceptThirdPartyCookies(tab.webView, !blockThirdParty)
    }
    tabManager.activeTab?.webView?.reload()
}

internal fun MainActivity.applyUserAgentMetadata(webView: WebView) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
    if (isDesktopMode) {
        val defaultUA = WebSettings.getDefaultUserAgent(this)
        val chromeVersion = Regex("Chrome/([\\d.]+)").find(defaultUA)?.groupValues?.get(1) ?: "134.0.0.0"
        val majorVersion = chromeVersion.substringBefore(".")
        val brands = listOf(
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Chromium").setMajorVersion(majorVersion).setFullVersion(chromeVersion).build(),
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Not-A.Brand").setMajorVersion("24").setFullVersion("24.0.0.0").build(),
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Google Chrome").setMajorVersion(majorVersion).setFullVersion(chromeVersion).build()
        )
        WebSettingsCompat.setUserAgentMetadata(
            webView.settings,
            UserAgentMetadata.Builder()
                .setBrandVersionList(brands)
                .setMobile(false)
                .setPlatform("Windows")
                .build()
        )
    } else {
        WebSettingsCompat.setUserAgentMetadata(
            webView.settings,
            UserAgentMetadata.Builder().build()
        )
    }
}

internal fun MainActivity.applyUserAgent() {
    val ua = buildUserAgent()
    tabManager.tabs.forEach {
        it.webView.settings.userAgentString = ua
        applyUserAgentMetadata(it.webView)
    }
    tabManager.activeTab?.webView?.reload()
}

internal fun MainActivity.addDesktopScript(tab: BrowserTab) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    removeDesktopScript(tab)
    desktopScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, loadJsAsset("desktop_mode.js"), setOf("*"))
}

internal fun MainActivity.removeDesktopScript(tab: BrowserTab) {
    desktopScriptHandlers.remove(tab.id)?.remove()
}

internal fun MainActivity.getSearchEngineHomeUrl(): String {
    return when (prefs.getString("search_engine", "duckduckgo")) {
        "brave" -> "https://search.brave.com"
        "google" -> "https://www.google.com"
        else -> "https://duckduckgo.com"
    }
}

internal fun MainActivity.getSearchQueryUrl(query: String): String {
    val encoded = android.net.Uri.encode(query)
    return when (prefs.getString("search_engine", "duckduckgo")) {
        "brave" -> "https://search.brave.com/search?q=$encoded"
        "google" -> "https://www.google.com/search?q=$encoded"
        else -> "https://duckduckgo.com/?q=$encoded"
    }
}

private fun isRelatedToSession(
    downloadUrl: String,
    pageUrl: String,
    session: MainActivity.RefreshLinkSession
): Boolean {
    val originalDownloadDomain = session.originalUrl.toHttpUrlOrNull()?.host
        ?.let { registeredDomain(it) }
    val originalRefererDomain = session.originalReferer.toHttpUrlOrNull()?.host
        ?.let { registeredDomain(it) }

    if (originalDownloadDomain == null && originalRefererDomain == null) return true

    val newDownloadDomain = downloadUrl.toHttpUrlOrNull()?.host
        ?.let { registeredDomain(it) }
    val newPageDomain = pageUrl.toHttpUrlOrNull()?.host
        ?.let { registeredDomain(it) }

    val originalDomains = setOfNotNull(originalDownloadDomain, originalRefererDomain)
    return newDownloadDomain in originalDomains || newPageDomain in originalDomains
}

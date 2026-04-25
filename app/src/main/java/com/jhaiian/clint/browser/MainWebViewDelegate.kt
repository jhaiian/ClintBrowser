package com.jhaiian.clint.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.tabs.BrowserTab

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
    settings.mediaPlaybackRequiresUserGesture = false
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
    webView.addJavascriptInterface(BottomNavBridge(), "BottomNavBridge")
    webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
        var filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
        // URLUtil maps application/octet-stream to .bin; recover the real extension from the URL
        if (filename.endsWith(".bin")) {
            val urlPath = url.substringBefore("?").substringBefore("#")
            val urlExt = urlPath.substringAfterLast(".", "")
            if (urlExt.isNotEmpty() && urlExt.length in 1..10 && !urlExt.contains("/")) {
                filename = filename.dropLast(4) + ".$urlExt"
            }
        }
        val referer = webView.url ?: ""
        val cookies = CookieManager.getInstance().getCookie(url) ?: ""
        ClintDownloadManager.enqueue(this, url, filename, userAgent, referer, cookies)
    }
    applyWebDarkMode(webView)
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
    val theme = prefs.getString("app_theme", "default") ?: "default"
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

@SuppressLint("SetJavaScriptEnabled")
internal fun MainActivity.reattachWebClients() {
    tabManager.tabs.forEach { tab ->
        tab.webView.webViewClient = com.jhaiian.clint.webview.ClintWebViewClient(
            prefs = prefs,
            isActive = { tabManager.activeTab?.id == tab.id },
            onPageStartedCallback = { url -> if (tabManager.activeTab?.id == tab.id) onPageStarted(url) },
            onPageFinishedCallback = { url -> if (tabManager.activeTab?.id == tab.id) onPageFinished(url) },
            onTabUrlUpdatedCallback = { wv, url -> onTabUrlUpdated(wv, url) },
            getDesktopHeaders = { buildDesktopHeaders() }
        )
    }
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

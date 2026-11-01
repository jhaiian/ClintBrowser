package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.settings.datasaver.DataSaverBridge
import com.jhaiian.clint.settings.datasaver.DataSaverMode
import com.jhaiian.clint.settings.datasaver.DataSaverSiteException
import com.jhaiian.clint.ui.theme.ThemeMode
import com.jhaiian.clint.browser.webview.*
import com.jhaiian.clint.browser.MainActivity

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.downloads.DownloadFilenameResolver
import com.jhaiian.clint.mediacapture.MEDIA_CAPTURE_ENABLED_PREF
import com.jhaiian.clint.mediacapture.MediaCaptureDetector
import com.jhaiian.clint.mediacapture.MediaCaptureStore
import com.jhaiian.clint.mediacapture.MediaScanBridge
import com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration
import com.jhaiian.clint.tabs.BrowserTab
import com.jhaiian.clint.profiles.WebProfiles
import com.jhaiian.clint.userscripts.UserScriptEngine
import com.jhaiian.clint.util.registeredDomain
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@SuppressLint("SetJavaScriptEnabled")
internal fun MainActivity.createWebView(isIncognito: Boolean, profileId: String = WebProfiles.DEFAULT_ID): WebView {
    val webView = ClintWebView(this)
    webView.incognitoTab = isIncognito
    if (!isIncognito) WebProfiles.bind(webView, profileId)
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
    val dataSaverEnabled = DataSaverMode.isActive(prefs)
    settings.mediaPlaybackRequiresUserGesture = dataSaverEnabled && prefs.getBoolean("data_saver_disable_autoplay", true)
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.safeBrowsingEnabled = false
    settings.userAgentString = buildUserAgent()
    applyUserAgentMetadata(webView)
    if (!isIncognito && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
        WebSettingsCompat.setWebAuthenticationSupport(settings, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP)
    }
    if (isIncognito) {
        CookieManager.getInstance().setAcceptCookie(false)
    } else {
        val cookieManager = WebProfiles.cookieManagerFor(webView)
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, !prefs.getBoolean("block_third_party_cookies", true))
    }
    ClintAutofill.apply(webView, isIncognito, prefs.getBoolean(ClintAutofill.PREF_AUTOFILL_ENABLED, ClintAutofill.DEFAULT_AUTOFILL_ENABLED))
    webView.addJavascriptInterface(NestedScrollBridge(), "NestedScrollBridge")
    webView.addJavascriptInterface(CanvasTouchBridge(), "CanvasTouchBridge")
    webView.addJavascriptInterface(BottomNavBridge(), "BottomNavBridge")
    webView.addJavascriptInterface(NotificationBridge(webView), "ClintNotificationBridge")
    webView.addJavascriptInterface(ClipboardBridge(webView), "ClintClipboardBridge")
    webView.addJavascriptInterface(UserScriptBridge(webView), "ClintUserScriptBridge")
    webView.addJavascriptInterface(BlobDownloadBridge(), "BlobDownloadBridge")
    webView.addJavascriptInterface(SelectPickerBridge(webView), "SelectPickerBridge")
    webView.addJavascriptInterface(DateTimePickerBridge(this, webView), "DateTimePickerBridge")
    webView.addJavascriptInterface(ColorPickerBridge(this, webView), "ColorPickerBridge")
    webView.addJavascriptInterface(DataSaverBridge(applicationContext), "ClintDataSaverBridge")
    webView.addJavascriptInterface(MediaScanBridge(prefs) { runCatching { tabManager.tabs.find { it.webView === webView }?.id }.getOrNull() }, MediaScanBridge.NAME)
    webView.addJavascriptInterface(webView.translator.bridge, "ClintTranslateBridge")
    installBackgroundPlaybackBridge(webView)
    webView.destroyListener = { onBackgroundPlaybackWebViewDestroyed(webView) }
    webView.keepVisibleInBackground = { keepsMediaAlive(webView) }

    QuiverGuardWebIntegration.attach(this, webView) {
        runCatching { tabManager.tabs.find { it.webView === webView }?.id }.getOrNull()
    }
    webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
        if (tabManager.activeTab?.webView !== webView) return@setDownloadListener
        if (url.startsWith("blob:")) {
            val filename = DownloadFilenameResolver.resolve(url, contentDisposition, mimetype)
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
        val filename = DownloadFilenameResolver.resolve(url, contentDisposition, mimetype)
        val referer = webView.url ?: ""
        val cookies = WebProfiles.cookieFor(WebProfiles.profileOf(webView), url)
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
        WebViewCompat.addDocumentStartJavaScript(webView, loadJsAsset("web_clipboard_bridge.js"), setOf("*"))
        WebViewCompat.addDocumentStartJavaScript(webView, loadJsAsset("select_picker.js"), setOf("*"))
        WebViewCompat.addDocumentStartJavaScript(webView, loadJsAsset("date_time_picker.js"), setOf("*"))
        WebViewCompat.addDocumentStartJavaScript(webView, loadJsAsset("color_picker.js"), setOf("*"))
        WebViewCompat.addDocumentStartJavaScript(webView, loadJsAsset("fullscreen_popup_guard.js"), setOf("*"))
        WebViewCompat.addDocumentStartJavaScript(webView, loadJsAsset(MediaScanBridge.ASSET), setOf("*"))
    }
    return webView
}

internal fun MainActivity.installServiceWorkerMediaCapture() {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
        !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
    ) return
    ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(object : ServiceWorkerClientCompat() {
        override fun shouldInterceptRequest(request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
            val tabId = runCatching { tabManager.activeTab?.id }.getOrNull()
            val pageUrl = tabId?.let { MediaCaptureStore.pageUrlFor(it) }
            if (prefs.getBoolean(MEDIA_CAPTURE_ENABLED_PREF, true) && tabId != null) {
                MediaCaptureDetector.onRequestObserved(tabId, pageUrl, request)
            }
            return QuiverGuardWebIntegration.shouldInterceptServiceWorkerRequest(
                applicationContext, request, pageUrl, tabId
            )
        }
    })
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

internal fun MainActivity.loadDataSaverJs(filename: String): String =
    "(function(){try{var h=location.hostname;var a=location.ancestorOrigins;if(a&&a.length){h=new URL(a[a.length-1]).hostname;}if(window.ClintDataSaverBridge&&window.ClintDataSaverBridge.isExcepted(h)){return;}}catch(e){}\n" +
        loadJsAsset(filename) + "\n})();"

internal fun MainActivity.isSaveDataActive(url: String? = null): Boolean =
    DataSaverMode.isActive(prefs) && prefs.getBoolean("data_saver_send_header", true) &&
        !DataSaverSiteException.isExceptedUrl(this, url)

internal fun MainActivity.applyDataSaverSiteState(tab: BrowserTab, url: String) {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return
    val active = DataSaverMode.isActive(prefs) && !DataSaverSiteException.isExceptedUrl(this, url)
    tab.webView.settings.mediaPlaybackRequiresUserGesture = active && prefs.getBoolean("data_saver_disable_autoplay", true)
}

internal fun MainActivity.isDataSaverBlockActive(): Boolean =
    DataSaverMode.isActive(prefs) && prefs.getBoolean("data_saver_block_preload", true)

internal fun MainActivity.isDataSaverVideoBlockActive(): Boolean =
    DataSaverMode.isActive(prefs) && prefs.getBoolean("data_saver_block_video", false)

internal fun MainActivity.isDataSaverScriptBlockActive(): Boolean =
    DataSaverMode.isActive(prefs) && prefs.getBoolean("data_saver_block_scripts", false)

internal fun MainActivity.addDataSaverMediaScript(tab: BrowserTab) {
    removeDataSaverMediaScript(tab)
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    if (isDataSaverBlockActive()) {
        dataSaverMediaScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, loadDataSaverJs("data_saver_media.js"), setOf("*"))
    }
    if (isDataSaverVideoBlockActive()) {
        dataSaverVideoScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, loadDataSaverJs("block_video.js"), setOf("*"))
    }
    if (isDataSaverScriptBlockActive()) {
        dataSaverScriptBlockHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, loadDataSaverJs("block_scripts.js"), setOf("*"))
    }
}

internal fun MainActivity.removeDataSaverMediaScript(tab: BrowserTab) {
    dataSaverMediaScriptHandlers.remove(tab.id)?.remove()
    dataSaverVideoScriptHandlers.remove(tab.id)?.remove()
    dataSaverScriptBlockHandlers.remove(tab.id)?.remove()
}

internal fun MainActivity.isDoNotTrackActive(): Boolean =
    prefs.getBoolean("do_not_track", true)

internal fun MainActivity.buildRequestHeaders(url: String? = null): Map<String, String>? {
    val headers = LinkedHashMap<String, String>()
    buildDesktopHeaders()?.let { headers.putAll(it) }
    if (isSaveDataActive(url)) headers["Save-Data"] = "on"
    if (isDoNotTrackActive()) headers["DNT"] = "1"
    if (isGlobalPrivacyControlActive()) headers["Sec-GPC"] = "1"
    return if (headers.isEmpty()) null else headers
}

internal fun MainActivity.addDoNotTrackScript(tab: BrowserTab) {
    removeDoNotTrackScript(tab)
    addPrivacyScripts(tab)
    if (!isDoNotTrackActive()) return
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    doNotTrackScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, loadJsAsset("do_not_track.js"), setOf("*"))
}

internal fun MainActivity.removeDoNotTrackScript(tab: BrowserTab) {
    doNotTrackScriptHandlers.remove(tab.id)?.remove()
    removePrivacyScripts(tab)
}

internal fun MainActivity.reloadActiveTabWithHeaders() {
    val activeWebView = tabManager.activeTab?.webView
    val activeUrl = activeWebView?.url
    val reloadHeaders = buildRequestHeaders(activeUrl)
    if (activeWebView != null && reloadHeaders != null && activeUrl != null && activeUrl.startsWith("http")) {
        activeWebView.loadUrl(activeUrl, reloadHeaders)
    } else {
        activeWebView?.reload()
    }
}

internal fun MainActivity.applyDoNotTrackSettings() {
    tabManager.tabs.forEach { addDoNotTrackScript(it) }
    reloadActiveTabWithHeaders()
}

internal fun MainActivity.addSaveDataScript(tab: BrowserTab) {
    removeSaveDataScript(tab)
    if (!isSaveDataActive()) return
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    saveDataScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, loadDataSaverJs("save_data.js"), setOf("*"))
}

internal fun MainActivity.removeSaveDataScript(tab: BrowserTab) {
    saveDataScriptHandlers.remove(tab.id)?.remove()
}

internal fun MainActivity.buildUserAgent(): String {
    val defaultUA = WebSettings.getDefaultUserAgent(this)
    val chromeVersion = Regex("Chrome/([\\d.]+)").find(defaultUA)?.groupValues?.get(1) ?: "134.0.0.0"
    return when {
        isDesktopMode ->
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chromeVersion + " Safari/537.36"
        prefs.getBoolean("custom_user_agent", true) -> GenericUserAgent.mobile(defaultUA)
        else ->
            defaultUA
    }
}

@Suppress("DEPRECATION")
internal fun MainActivity.applyWebDarkMode(webView: WebView) {
    val theme = prefs.getString("app_theme", "system") ?: "system"
    val enabled = !ThemeMode.isLight(theme)
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

internal fun MainActivity.applyWebDarkModeToAllTabs() {
    tabManager.tabs.forEach { applyWebDarkMode(it.webView) }
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) &&
        !WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
    ) {
        tabManager.activeTab?.webView?.reload()
    }
}

internal fun MainActivity.addAutoplayScript(tab: BrowserTab) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    removeAutoplayScript(tab)
    if (!DataSaverMode.isActive(prefs) || !prefs.getBoolean("data_saver_disable_autoplay", true)) return
    autoplayScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, loadDataSaverJs("disable_autoplay.js"), setOf("*"))
}

internal fun MainActivity.removeAutoplayScript(tab: BrowserTab) {
    autoplayScriptHandlers.remove(tab.id)?.remove()
}

internal fun MainActivity.applyDataSaverSettings() {
    val dataSaverEnabled = DataSaverMode.isActive(prefs)
    val disableAutoplay = dataSaverEnabled && prefs.getBoolean("data_saver_disable_autoplay", true)
    tabManager.tabs.forEach { tab ->
        val excepted = DataSaverSiteException.isExceptedUrl(this, tab.webView.url)
        tab.webView.settings.mediaPlaybackRequiresUserGesture = disableAutoplay && !excepted
        addAutoplayScript(tab)
        addSaveDataScript(tab)
        addDataSaverMediaScript(tab)
    }
    reloadActiveTabWithHeaders()
}

internal fun MainActivity.applyJavaScript() {
    val enabled = prefs.getBoolean("javascript_enabled", true)
    tabManager.tabs.forEach { it.webView.settings.javaScriptEnabled = enabled }
    tabManager.activeTab?.webView?.reload()
}

internal fun MainActivity.applyCookiePolicy() {
    val blockThirdParty = prefs.getBoolean("block_third_party_cookies", true)
    tabManager.tabs.forEach { tab ->
        if (!tab.isIncognito) WebProfiles.cookieManagerFor(tab.webView).setAcceptThirdPartyCookies(tab.webView, !blockThirdParty)
    }
    tabManager.activeTab?.webView?.reload()
}

internal fun MainActivity.applyAutofillSettings() {
    val enabled = prefs.getBoolean(ClintAutofill.PREF_AUTOFILL_ENABLED, ClintAutofill.DEFAULT_AUTOFILL_ENABLED)
    tabManager.tabs.forEach { ClintAutofill.apply(it.webView, it.isIncognito, enabled) }
    ClintAutofill.cancel(this)
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
    } else if (prefs.getBoolean("custom_user_agent", true)) {
        WebSettingsCompat.setUserAgentMetadata(
            webView.settings,
            GenericUserAgent.metadata(WebSettings.getDefaultUserAgent(this))
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

internal fun MainActivity.isDesktopModeSaveStateEnabled(): Boolean =
    prefs.getString(
        com.jhaiian.clint.settings.desktopmode.DesktopModeActivity.PREF_DESKTOP_MODE_SAVE_STATE,
        com.jhaiian.clint.settings.desktopmode.DesktopModeActivity.VALUE_SAVE_STATE
    ) == com.jhaiian.clint.settings.desktopmode.DesktopModeActivity.VALUE_SAVE_STATE

internal fun MainActivity.applyDesktopModeToTabs() {
    val ua = buildUserAgent()
    tabManager.tabs.forEach { tab ->
        tab.webView.settings.userAgentString = ua
        applyUserAgentMetadata(tab.webView)
        if (isDesktopMode) addDesktopScript(tab) else removeDesktopScript(tab)
    }
}

internal fun MainActivity.syncDesktopModeForUrl(url: String): Boolean {
    if (!url.startsWith("http") || com.jhaiian.clint.browser.home.isClintHomeUrl(url)) return false
    val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return false
    val shouldSaveState = isDesktopModeSaveStateEnabled()
    val domain = com.jhaiian.clint.util.registeredDomain(host)
    val optedOut = tabManager.activeTab?.isIncognito == true && domain in desktopModeOptOutDomains
    val isSaved = shouldSaveState && !optedOut &&
        com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.getState(
            this, host, com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_DESKTOP_MODE
        ) != null
    val lockedHost = desktopModeHost
    val enable = when {
        isSaved -> true
        !isDesktopMode -> false
        !shouldSaveState || lockedHost == null || lockedHost == com.jhaiian.clint.browser.home.CLINT_HOME_HOST -> true
        else -> com.jhaiian.clint.util.registeredDomain(lockedHost) == domain
    }
    desktopModeHost = if (enable) host else null
    if (enable == isDesktopMode) return false
    isDesktopMode = enable
    applyDesktopModeToTabs()
    return true
}

internal fun MainActivity.addUserScripts(tab: BrowserTab) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    removeUserScripts(tab)
    val script = UserScriptEngine.buildCombinedScript(this) ?: return
    userScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, script, setOf("*"))
}

internal fun MainActivity.removeUserScripts(tab: BrowserTab) {
    userScriptHandlers.remove(tab.id)?.remove()
}

internal fun MainActivity.applyUserScripts() {
    tabManager.tabs.forEach { addUserScripts(it) }
    tabManager.activeTab?.webView?.reload()
}

internal fun MainActivity.getHomepageUrl(incognito: Boolean = tabManager.activeTab?.isIncognito == true): String {
    val homepage = prefs.getString(com.jhaiian.clint.browser.home.PREF_HOMEPAGE, com.jhaiian.clint.browser.home.HOMEPAGE_CLINT)
    return when (homepage) {
        com.jhaiian.clint.browser.home.HOMEPAGE_SEARCH_ENGINE -> getSearchEngineHomeUrl(incognito)
        com.jhaiian.clint.browser.home.HOMEPAGE_CUSTOM -> com.jhaiian.clint.browser.home.normalizeCustomHomepageUrl(
            prefs.getString(com.jhaiian.clint.browser.home.PREF_HOMEPAGE_CUSTOM_URL, "") ?: ""
        ) ?: com.jhaiian.clint.browser.home.CLINT_HOME_URL
        else -> com.jhaiian.clint.browser.home.CLINT_HOME_URL
    }
}

internal fun MainActivity.getSearchEngineHomeUrl(incognito: Boolean = tabManager.activeTab?.isIncognito == true): String {
    return when (com.jhaiian.clint.browser.searchEngineFor(prefs, incognito)) {
        "brave" -> "https://search.brave.com"
        "ecosia" -> "https://www.ecosia.org"
        "google" -> "https://www.google.com"
        "custom" -> com.jhaiian.clint.browser.customSearchEngineHomeUrl(prefs, incognito)
        else -> "https://duckduckgo.com"
    }
}

internal fun MainActivity.getSearchQueryUrl(query: String, incognito: Boolean = tabManager.activeTab?.isIncognito == true): String {
    val encoded = android.net.Uri.encode(query)
    return when (com.jhaiian.clint.browser.searchEngineFor(prefs, incognito)) {
        "brave" -> "https://search.brave.com/search?q=$encoded"
        "ecosia" -> "https://www.ecosia.org/search?q=$encoded"
        "google" -> "https://www.google.com/search?q=$encoded"
        "custom" -> com.jhaiian.clint.browser.customSearchEngineQueryUrl(prefs, encoded, incognito)
        else -> "https://duckduckgo.com/?q=$encoded"
    }
}

internal fun isRelatedToSession(
    downloadUrl: String,
    pageUrl: String,
    session: MainActivity.RefreshLinkSession
): Boolean {
    val originalDownloadDomain = session.originalUrl.toHttpUrlOrNull()?.host
        ?.let { registeredDomain(it) }
    val originalRefererDomain = session.originalReferer.toHttpUrlOrNull()?.host
        ?.let { registeredDomain(it) }
    val originalPageDomain = session.pageUrl.toHttpUrlOrNull()?.host
        ?.let { registeredDomain(it) }

    if (originalDownloadDomain == null && originalRefererDomain == null && originalPageDomain == null) return true

    val newDownloadDomain = downloadUrl.toHttpUrlOrNull()?.host
        ?.let { registeredDomain(it) }
    val newPageDomain = pageUrl.toHttpUrlOrNull()?.host
        ?.let { registeredDomain(it) }

    val originalDomains = setOfNotNull(originalDownloadDomain, originalRefererDomain, originalPageDomain)
    return newDownloadDomain in originalDomains || newPageDomain in originalDomains
}

internal fun MainActivity.prepareCacheForNavigation(tab: BrowserTab, url: String?) {
    DataSaverCacheMode.prepare(applicationContext, prefs, tab.webView, url, tab.isIncognito)
}

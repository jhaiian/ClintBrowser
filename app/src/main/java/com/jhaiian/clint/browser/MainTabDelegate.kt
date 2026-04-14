package com.jhaiian.clint.browser

import android.annotation.SuppressLint
import com.jhaiian.clint.tabs.BrowserTab
import com.jhaiian.clint.tabs.TabSwitcherSheet
import com.jhaiian.clint.webview.ClintWebChromeClient
import com.jhaiian.clint.webview.ClintWebViewClient

internal fun MainActivity.saveTabs() {
    val urls = tabManager.tabs
        .filter { !it.isIncognito }
        .mapNotNull { tab ->
            val url = tab.webView.url ?: tab.url
            url.takeIf { it.isNotEmpty() && it != "about:blank" }
        }
    val activeNonIncognito = tabManager.tabs
        .filter { !it.isIncognito }
        .indexOf(tabManager.activeTab)
        .coerceAtLeast(0)
    prefs.edit()
        .putString("saved_tab_urls", urls.joinToString("\n"))
        .putInt("saved_tab_active", activeNonIncognito)
        .apply()
}

internal fun MainActivity.restoreTabs(): Boolean {
    val savedUrls = prefs.getString("saved_tab_urls", null)
        ?.split("\n")
        ?.filter { it.isNotEmpty() }
        ?: return false
    if (savedUrls.isEmpty()) return false
    val activeIdx = prefs.getInt("saved_tab_active", 0).coerceIn(0, savedUrls.lastIndex)
    savedUrls.forEach { url -> openNewTabSilent(url) }
    tabManager.switchTo(activeIdx)
    attachActiveWebView()
    return true
}

@SuppressLint("SetJavaScriptEnabled")
internal fun MainActivity.openNewTabSilent(url: String) {
    val webView = createWebView(false)
    val tab = BrowserTab(url = url, webView = webView)
    tabManager.add(tab)
    if (isDesktopMode) addDesktopScript(tab)
    webView.webViewClient = ClintWebViewClient(
        prefs = prefs,
        isActive = { tabManager.activeTab?.id == tab.id },
        onPageStartedCallback = { url -> if (tabManager.activeTab?.id == tab.id) onPageStarted(url) },
        onPageFinishedCallback = { url -> if (tabManager.activeTab?.id == tab.id) onPageFinished(url) },
        onTabUrlUpdatedCallback = { wv, url -> onTabUrlUpdated(wv, url) },
        getDesktopHeaders = { buildDesktopHeaders() }
    )
    webView.webChromeClient = ClintWebChromeClient(
        isActive = { tabManager.activeTab?.id == tab.id },
        onTitleChanged = { title ->
            tab.title = title
            if (tabManager.activeTab?.id == tab.id) updateTabCount()
        },
        onProgressChanged = { progress -> if (tabManager.activeTab?.id == tab.id) onProgressChanged(progress) },
        onUrlChanged = { url -> if (tabManager.activeTab?.id == tab.id) updateAddressBar(url) },
        onFullscreenShow = { view, cb -> onShowCustomView(view, cb) },
        onFullscreenHide = { exitFullscreen() },
        onFileChooser = { callback, params -> onShowFileChooser(callback, params) },
        onNewWindowRequest = { newUrl ->
            val uri = android.net.Uri.parse(newUrl)
            val scheme = uri.scheme?.lowercase()
            val activeWebView = tabManager.activeTab?.webView
            val client = activeWebView?.webViewClient as? ClintWebViewClient
            if (scheme == "http" || scheme == "https") {
                if (client == null || !client.tryOpenInApp(activeWebView, uri)) {
                    openNewTab(isIncognito = tab.isIncognito, url = newUrl)
                }
            } else {
                openNewTab(isIncognito = tab.isIncognito, url = newUrl)
            }
        }
    )
    webView.loadUrl(url)
}

internal fun MainActivity.openNewTab(isIncognito: Boolean, url: String = getSearchEngineHomeUrl()) {
    val webView = createWebView(isIncognito)
    val tab = BrowserTab(isIncognito = isIncognito, webView = webView)
    val index = tabManager.add(tab)
    if (isDesktopMode) addDesktopScript(tab)
    webView.webViewClient = ClintWebViewClient(
        prefs = prefs,
        isActive = { tabManager.activeTab?.id == tab.id },
        onPageStartedCallback = { url -> if (tabManager.activeTab?.id == tab.id) onPageStarted(url) },
        onPageFinishedCallback = { url -> if (tabManager.activeTab?.id == tab.id) onPageFinished(url) },
        onTabUrlUpdatedCallback = { wv, url -> onTabUrlUpdated(wv, url) },
        getDesktopHeaders = { buildDesktopHeaders() }
    )
    webView.webChromeClient = ClintWebChromeClient(
        isActive = { tabManager.activeTab?.id == tab.id },
        onTitleChanged = { title ->
            tab.title = title
            if (tabManager.activeTab?.id == tab.id) updateTabCount()
        },
        onProgressChanged = { progress -> if (tabManager.activeTab?.id == tab.id) onProgressChanged(progress) },
        onUrlChanged = { url -> if (tabManager.activeTab?.id == tab.id) updateAddressBar(url) },
        onFullscreenShow = { view, cb -> onShowCustomView(view, cb) },
        onFullscreenHide = { exitFullscreen() },
        onFileChooser = { callback, params -> onShowFileChooser(callback, params) },
        onNewWindowRequest = { newUrl ->
            val uri = android.net.Uri.parse(newUrl)
            val scheme = uri.scheme?.lowercase()
            val activeWebView = tabManager.activeTab?.webView
            val client = activeWebView?.webViewClient as? ClintWebViewClient
            if (scheme == "http" || scheme == "https") {
                if (client == null || !client.tryOpenInApp(activeWebView, uri)) {
                    openNewTab(isIncognito = isIncognito, url = newUrl)
                }
            } else {
                openNewTab(isIncognito = isIncognito, url = newUrl)
            }
        }
    )
    tabManager.switchTo(index)
    attachActiveWebView()
    loadUrl(url)
}

internal fun MainActivity.attachActiveWebView() {
    val tab = tabManager.activeTab ?: return
    binding.webContainer.removeAllViews()
    (tab.webView.parent as? android.view.ViewGroup)?.removeView(tab.webView)
    binding.webContainer.addView(tab.webView, android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT
    ))
    updateIncognitoState(tab.isIncognito)
    updateSwipeRefreshColors(tab.isIncognito)
    updateTabCount()
    updateAddressBar(tab.webView.url ?: "")
    updateNavigationState()
    updateBookmarkIcon()
    val cookieManager = android.webkit.CookieManager.getInstance()
    if (tab.isIncognito) {
        cookieManager.setAcceptCookie(false)
    } else {
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(tab.webView, !prefs.getBoolean("block_third_party_cookies", true))
    }
    nestedScrollActive = false
    animateBars(hide = false, animated = false)
    attachScrollListener(tab.webView)
    injectScrollTracker(tab.webView)
}

internal fun MainActivity.showTabSwitcher() {
    val existing = supportFragmentManager.findFragmentByTag("tab_switcher") as? TabSwitcherSheet
    if (existing != null && existing.isAdded) return
    val sheet = TabSwitcherSheet()
    sheet.tabs = tabManager.previews().toMutableList()
    sheet.activeIndex = tabManager.activeIndex
    sheet.show(supportFragmentManager, "tab_switcher")
}

package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.MainActivity

import android.annotation.SuppressLint
import com.jhaiian.clint.tabs.BrowserTab
import com.jhaiian.clint.tabs.SavedTab
import com.jhaiian.clint.tabs.TabSessionManager
import com.jhaiian.clint.tabs.TabSwitcherSheet
import com.jhaiian.clint.browser.webview.ClintWebChromeClient
import com.jhaiian.clint.browser.webview.ClintWebViewClient
import com.jhaiian.clint.quiver.engine.BlockedRequestCounter
import com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration
import com.jhaiian.clint.quiver.engine.ScriptHandlerStore

internal fun MainActivity.saveTabs() {
    val savedTabs = tabManager.tabs
        .filter { !it.isIncognito && !it.isRefreshLinkTab }
        .mapIndexedNotNull { index, tab ->
            val url = tab.webView.url?.takeIf { it.isNotEmpty() && it != "about:blank" }
                ?: tab.url.takeIf { it.isNotEmpty() && it != "about:blank" }
                ?: return@mapIndexedNotNull null
            SavedTab(
                position = index,
                url = url,
                title = tab.title,
                isActive = tab == tabManager.activeTab
            )
        }
    Thread { TabSessionManager.save(this, savedTabs) }.start()
}

internal fun MainActivity.restoreTabs(): Boolean {
    migrateTabsFromPrefsIfNeeded()
    val savedTabs = TabSessionManager.load(this)
    if (savedTabs.isEmpty()) return false
    val activeIndex = savedTabs.indexOfFirst { it.isActive }.coerceAtLeast(0)
    savedTabs.forEach { openNewTabSilent(it.url) }
    tabManager.switchTo(activeIndex)
    attachActiveWebView()
    return true
}

private fun MainActivity.migrateTabsFromPrefsIfNeeded() {
    if (!TabSessionManager.isEmpty(this)) return
    val savedUrls = prefs.getString("saved_tab_urls", null)
        ?.split("\n")
        ?.filter { it.isNotEmpty() }
        ?: return
    if (savedUrls.isEmpty()) return
    val activeIdx = prefs.getInt("saved_tab_active", 0).coerceIn(0, savedUrls.lastIndex)
    val migratedTabs = savedUrls.mapIndexed { index, url ->
        SavedTab(
            position = index,
            url = url,
            title = "",
            isActive = index == activeIdx
        )
    }
    TabSessionManager.save(this, migratedTabs)
    prefs.edit()
        .remove("saved_tab_urls")
        .remove("saved_tab_active")
        .apply()
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
        getDesktopHeaders = { buildDesktopHeaders() },
        getTabId = { tab.id }
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
        onWebPermissionRequest = { request -> onWebPermissionRequest(request) },
        onGeolocationRequest = { origin, callback -> onWebGeolocationRequest(origin, callback) },
        onNewWindowRequest = { newUrl ->
            showPopupAlertDialog(newUrl, tab.isIncognito)
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
        getDesktopHeaders = { buildDesktopHeaders() },
        getTabId = { tab.id }
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
        onWebPermissionRequest = { request -> onWebPermissionRequest(request) },
        onGeolocationRequest = { origin, callback -> onWebGeolocationRequest(origin, callback) },
        onNewWindowRequest = { newUrl ->
            showPopupAlertDialog(newUrl, isIncognito)
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
    BlockedRequestCounter.setActiveTab(tab.id)
    val cookieManager = android.webkit.CookieManager.getInstance()
    if (tab.isIncognito) {
        cookieManager.setAcceptCookie(false)
    } else {
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(tab.webView, !prefs.getBoolean("block_third_party_cookies", true))
    }
    nestedScrollActive = false
    canvasTouchActive = false
    hasWebBottomNav = false
    animateBottomBarTo(0f, animated = false)
    attachScrollListener(tab.webView)
    injectScrollTracker(tab.webView)
}

internal fun MainActivity.showTabSwitcher() {
    val existing = supportFragmentManager.findFragmentByTag("tab_switcher") as? TabSwitcherSheet
    if (existing != null && existing.isAdded) return
    hideKeyboard()
    val sheet = TabSwitcherSheet()
    sheet.tabs = tabManager.previews().toMutableList()
    sheet.activeIndex = tabManager.activeIndex
    sheet.show(supportFragmentManager, "tab_switcher")
}

@SuppressLint("SetJavaScriptEnabled")
internal fun MainActivity.openRefreshLinkTab(url: String) {
    val webView = createWebView(false)
    val tab = BrowserTab(url = url, isRefreshLinkTab = true, webView = webView)
    val index = tabManager.add(tab)
    if (isDesktopMode) addDesktopScript(tab)
    webView.webViewClient = ClintWebViewClient(
        prefs = prefs,
        isActive = { tabManager.activeTab?.id == tab.id },
        onPageStartedCallback = { u -> if (tabManager.activeTab?.id == tab.id) onPageStarted(u) },
        onPageFinishedCallback = { u -> if (tabManager.activeTab?.id == tab.id) onPageFinished(u) },
        onTabUrlUpdatedCallback = { wv, u -> onTabUrlUpdated(wv, u) },
        getDesktopHeaders = { buildDesktopHeaders() },
        getTabId = { tab.id }
    )
    webView.webChromeClient = ClintWebChromeClient(
        isActive = { tabManager.activeTab?.id == tab.id },
        onTitleChanged = { title ->
            tab.title = title
            if (tabManager.activeTab?.id == tab.id) updateTabCount()
        },
        onProgressChanged = { progress -> if (tabManager.activeTab?.id == tab.id) onProgressChanged(progress) },
        onUrlChanged = { u -> if (tabManager.activeTab?.id == tab.id) updateAddressBar(u) },
        onFullscreenShow = { view, cb -> onShowCustomView(view, cb) },
        onFullscreenHide = { exitFullscreen() },
        onFileChooser = { callback, params -> onShowFileChooser(callback, params) },
        onWebPermissionRequest = { request -> onWebPermissionRequest(request) },
        onGeolocationRequest = { origin, callback -> onWebGeolocationRequest(origin, callback) },
        onNewWindowRequest = { newUrl ->
            showPopupAlertDialog(newUrl, false)
        }
    )
    tabManager.switchTo(index)
    attachActiveWebView()
    loadUrl(url)
}

internal fun MainActivity.cleanupRefreshLinkTabs() {
    val previousIndex = refreshLinkSession?.previousTabIndex ?: -1
    val indices = tabManager.tabs.indices.filter { tabManager.tabs[it].isRefreshLinkTab }.reversed()
    for (i in indices) {
        tabManager.closeTab(i)
    }
    if (tabManager.tabs.isEmpty()) {
        openNewTab(isIncognito = false, url = getSearchEngineHomeUrl())
        return
    }
    val targetIndex = when {
        previousIndex in tabManager.tabs.indices -> previousIndex
        else -> (tabManager.tabs.size - 1).coerceAtLeast(0)
    }
    tabManager.switchTo(targetIndex)
    attachActiveWebView()
}

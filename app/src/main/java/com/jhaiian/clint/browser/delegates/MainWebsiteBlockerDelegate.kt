package com.jhaiian.clint.browser.delegates

import androidx.lifecycle.lifecycleScope
import com.jhaiian.clint.blocker.blockedpage.WebsiteBlockedRequest
import com.jhaiian.clint.blocker.engine.WebsiteBlockerWebIntegration
import com.jhaiian.clint.browser.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun MainActivity.initializeWebsiteBlockerEngine() {
    // Loading the compiled domain database is disk + JNI work; keep it off the UI
    // thread at cold start (same pattern as the QuiverGuard engine). The engine
    // itself is queried from WebView background threads, so it is thread-safe.
    lifecycleScope.launch(Dispatchers.IO) {
        WebsiteBlockerWebIntegration.initialize(this@initializeWebsiteBlockerEngine)
    }
}

internal fun MainActivity.onWebsiteBlocked(blockedUrl: String, previousUrl: String, tabId: String) {
    if (tabManager.activeTab?.id != tabId) return
    uiState.websiteBlockedRequest = WebsiteBlockedRequest(
        blockedUrl = blockedUrl,
        previousUrl = previousUrl,
        tabId = tabId
    )
}

internal fun MainActivity.dismissWebsiteBlockedOverlay() {
    val request = uiState.websiteBlockedRequest ?: return
    uiState.websiteBlockedRequest = null
    val tab = tabManager.tabs.find { it.id == request.tabId } ?: return
    if (tab.webView.canGoBack()) {
        tab.webView.goBack()
    } else {
        tab.webView.loadUrl(getHomepageUrl(tab.isIncognito))
    }
}

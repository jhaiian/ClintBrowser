package com.jhaiian.clint.browser.delegates

import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.dialogs.PopupAlertRequest
import com.jhaiian.clint.browser.webview.ClintWebViewClient

internal fun MainActivity.showPopupAlertDialog(newUrl: String, isIncognito: Boolean, sourceTabId: String? = null) {
    if (uiState.isFullscreen) return
    val sourceHost = tabManager.activeTab?.webView?.url
        ?.let { android.net.Uri.parse(it).host?.takeIf { h -> h.isNotEmpty() } ?: it }
        ?: getString(R.string.popup_alert_source_unknown)

    uiState.popupAlertRequest = PopupAlertRequest(
        sourceHost = sourceHost,
        newUrl = newUrl,
        onAllow = {
            val uri = android.net.Uri.parse(newUrl)
            val scheme = uri.scheme?.lowercase()
            val activeWebView = tabManager.activeTab?.webView
            val client = activeWebView?.webViewClient as? ClintWebViewClient
            if (scheme == "http" || scheme == "https") {
                if (client == null || !client.tryOpenInApp(activeWebView, uri)) {
                    openNewTab(isIncognito = isIncognito, url = newUrl, openerTabId = sourceTabId)
                }
            } else {
                openNewTab(isIncognito = isIncognito, url = newUrl, openerTabId = sourceTabId)
            }
        }
    )
}

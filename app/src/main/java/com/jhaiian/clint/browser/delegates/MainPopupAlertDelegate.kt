package com.jhaiian.clint.browser.delegates

import android.text.Html
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.webview.ClintWebViewClient

internal fun MainActivity.showPopupAlertDialog(newUrl: String, isIncognito: Boolean) {
    val sourceHost = tabManager.activeTab?.webView?.url
        ?.let { android.net.Uri.parse(it).host?.takeIf { h -> h.isNotEmpty() } ?: it }
        ?: getString(R.string.popup_alert_source_unknown)

    val view = LayoutInflater.from(this).inflate(R.layout.dialog_popup_alert, null)
    view.findViewById<TextView>(R.id.tvPopupAlertMessage).text =
        Html.fromHtml(getString(R.string.popup_alert_message, sourceHost), Html.FROM_HTML_MODE_COMPACT)
    view.findViewById<TextView>(R.id.tvPopupAlertUrl).text = newUrl

    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.popup_alert_title))
        .setView(view)
        .setNegativeButton(getString(R.string.action_no)) { _, _ -> }
        .setPositiveButton(getString(R.string.action_yes)) { _, _ ->
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
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

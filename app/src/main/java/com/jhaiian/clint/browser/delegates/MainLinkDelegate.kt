package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.webview.*
import com.jhaiian.clint.browser.sheets.*
import com.jhaiian.clint.browser.MainActivity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.widget.Toast
import com.jhaiian.clint.R

internal fun MainActivity.setupLinkLongPress(webView: WebView) {
    webView.setOnLongClickListener {
        val result = webView.hitTestResult
        when (result.type) {
            WebView.HitTestResult.IMAGE_TYPE -> {
                val imageUrl = result.extra ?: return@setOnLongClickListener false
                val currentPageUrl = webView.url ?: ""
                val isStandalone = isStandaloneImagePage(currentPageUrl)
                val escapedUrl = imageUrl.replace("\\", "\\\\").replace("'", "\\'")
                val js = loadJsAsset("image_alt_text.js").replace("%URL%", escapedUrl)
                webView.evaluateJavascript(js) { raw ->
                    val altText = raw?.removeSurrounding("\"")?.trim() ?: ""
                    showImageLongPressSheet(imageUrl, altText, isStandalone, currentPageUrl)
                }
                true
            }
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                val linkUrl = result.extra ?: return@setOnLongClickListener false
                showTrackedLinkLongPressSheet(webView, linkUrl)
                true
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {

                val hrefHandler = Handler(Looper.getMainLooper()) { message ->
                    val linkUrl = message.data.getString("url")
                    if (!linkUrl.isNullOrEmpty()) showTrackedLinkLongPressSheet(webView, linkUrl)
                    true
                }
                webView.requestFocusNodeHref(hrefHandler.obtainMessage())
                true
            }
            else -> false
        }
    }
}

private fun MainActivity.showTrackedLinkLongPressSheet(webView: WebView, linkUrl: String) {
    webView.evaluateJavascript(loadJsAsset("link_text.js")) { raw ->
        val linkText = raw?.removeSurrounding("\"")
            ?.replace("\\n", " ")
            ?.replace("\\t", " ")
            ?.trim() ?: ""
        showLinkLongPressSheet(linkUrl, linkText)
    }
}

internal fun MainActivity.showLinkLongPressSheet(url: String, linkText: String) {
    if (uiState.linkLongPressRequest != null) return
    uiState.linkLongPressRequest = LinkLongPressRequest(url, linkText)
}

internal fun MainActivity.handleLinkOpenInNewTab(url: String) {
    openNewTab(isIncognito = false, url = url, profileId = activeProfileId())
}

internal fun MainActivity.handleLinkOpenIncognito(url: String) {
    openNewTab(isIncognito = true, url = url)
}

internal fun MainActivity.handleLinkOpenNewTabBackground(url: String) {
    openNewTabInBackground(url)
}

internal fun MainActivity.handleLinkPreviewPage(url: String) {
    if (uiState.contentPreviewRequest != null) return
    uiState.contentPreviewRequest = ContentPreviewRequest.forPage(url, isDesktopMode)
}

internal fun MainActivity.handleLinkCopyAddress(url: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.link_copy_address), url))
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, getString(R.string.link_address_copied), Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.handleLinkCopyText(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.link_copy_text), text))
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, getString(R.string.link_text_copied), Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.handleLinkShare(url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.link_share)))
}

package com.jhaiian.clint.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.ClintToast

internal fun MainActivity.setupLinkLongPress(webView: WebView) {
    webView.setOnLongClickListener {
        val result = webView.hitTestResult
        when (result.type) {
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
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
                webView.evaluateJavascript(loadJsAsset("link_text.js")) { raw ->
                    val linkText = raw?.removeSurrounding("\"")
                        ?.replace("\\n", " ")
                        ?.replace("\\t", " ")
                        ?.trim() ?: ""
                    showLinkLongPressSheet(linkUrl, linkText)
                }
                true
            }
            else -> false
        }
    }
}

internal fun MainActivity.showLinkLongPressSheet(url: String, linkText: String) {
    val existing = supportFragmentManager.findFragmentByTag("link_long_press")
    if (existing != null && existing.isAdded) return
    LinkLongPressSheet.newInstance(url, linkText)
        .show(supportFragmentManager, "link_long_press")
}

internal fun MainActivity.handleLinkOpenInNewTab(url: String) {
    openNewTab(isIncognito = false, url = url)
}

internal fun MainActivity.handleLinkOpenIncognito(url: String) {
    openNewTab(isIncognito = true, url = url)
}

internal fun MainActivity.handleLinkPreviewPage(url: String) {
    val existing = supportFragmentManager.findFragmentByTag("link_preview")
    if (existing != null && existing.isAdded) return
    ContentPreviewSheet.newInstanceForPage(url, isDesktopMode)
        .show(supportFragmentManager, "link_preview")
}

internal fun MainActivity.handleLinkCopyAddress(url: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.link_copy_address), url))
    ClintToast.show(this, getString(R.string.link_address_copied), R.drawable.ic_copy_24)
}

internal fun MainActivity.handleLinkCopyText(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.link_copy_text), text))
    ClintToast.show(this, getString(R.string.link_text_copied), R.drawable.ic_copy_24)
}

internal fun MainActivity.handleLinkShare(url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.link_share)))
}

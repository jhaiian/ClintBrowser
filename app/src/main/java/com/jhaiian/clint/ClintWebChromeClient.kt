package com.jhaiian.clint

import android.webkit.WebChromeClient
import android.webkit.WebView

class ClintWebChromeClient(
    private val isActive: () -> Boolean = { true },
    private val onTitleChanged: (String) -> Unit = {}
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if (isActive()) (view.context as? MainActivity)?.onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String) {
        super.onReceivedTitle(view, title)
        onTitleChanged(title)
        if (isActive()) (view.context as? MainActivity)?.updateAddressBar(view.url ?: "")
    }
}

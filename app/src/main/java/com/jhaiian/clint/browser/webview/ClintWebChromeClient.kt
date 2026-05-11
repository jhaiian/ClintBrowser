package com.jhaiian.clint.browser.webview

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class ClintWebChromeClient(
    private val isActive: () -> Boolean = { true },
    private val onTitleChanged: (String) -> Unit = {},
    private val onProgressChanged: (Int) -> Unit = {},
    private val onUrlChanged: (String) -> Unit = {},
    private val onFullscreenShow: (View, CustomViewCallback) -> Unit = { _, _ -> },
    private val onFullscreenHide: () -> Unit = {},
    private val onFileChooser: (ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean = { _, _ -> false },
    private val onNewWindowRequest: (String) -> Unit = {},
    private val onWebPermissionRequest: (PermissionRequest) -> Unit = { it.deny() },
    private val onGeolocationRequest: (String, GeolocationPermissions.Callback) -> Unit = { _, cb -> cb.invoke("", false, false) }
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if (isActive()) onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String) {
        super.onReceivedTitle(view, title)
        onTitleChanged(title)
        if (isActive()) onUrlChanged(view.url ?: "")
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onFullscreenShow(view, callback)
    }

    override fun onHideCustomView() {
        onFullscreenHide()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        return onFileChooser(filePathCallback, fileChooserParams)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        onWebPermissionRequest(request)
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        onGeolocationRequest(origin, callback)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        if (resultMsg == null) return false
        val helperWebView = WebView(view.context)
        helperWebView.settings.javaScriptEnabled = false
        helperWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(wv: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url != "about:blank") {
                    onNewWindowRequest(url)
                }
                return true
            }
            override fun onPageStarted(wv: WebView, url: String, favicon: Bitmap?) {
                if (url != "about:blank") {
                    onNewWindowRequest(url)
                    wv.stopLoading()
                }
            }
        }
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = helperWebView
        resultMsg.sendToTarget()
        return true
    }
}

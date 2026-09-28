package com.jhaiian.clint.browser.webview

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jhaiian.clint.browser.dialogs.JsDialogRequest
import com.jhaiian.clint.browser.dialogs.JsDialogType

class ClintWebChromeClient(
    private val isActive: () -> Boolean = { true },
    private val onTitleChanged: (String) -> Unit = {},
    private val onProgressChanged: (Int) -> Unit = {},
    private val onUrlChanged: (String) -> Unit = {},
    private val onFullscreenShow: (View, CustomViewCallback) -> Unit = { _, _ -> },
    private val onFullscreenHide: () -> Unit = {},
    private val onFileChooser: (ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean = { _, _ -> false },
    private val onNewWindowRequest: (String) -> Unit = {},
    private val isFullscreenActive: () -> Boolean = { false },
    private val onWebPermissionRequest: (PermissionRequest) -> Unit = { it.deny() },
    private val onGeolocationRequest: (String, GeolocationPermissions.Callback) -> Unit = { _, cb -> cb.invoke("", false, false) },
    private val isCustomJsDialogsEnabled: () -> Boolean = { false },
    private val onJsDialog: (JsDialogRequest) -> Unit = {}
) : WebChromeClient() {

    private var fullscreenExitedAtMs = 0L

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
        fullscreenExitedAtMs = SystemClock.elapsedRealtime()
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

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean =
        dispatchJsDialog(JsDialogType.Alert, url, message, "", result)

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean =
        dispatchJsDialog(JsDialogType.Confirm, url, message, "", result)

    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean =
        dispatchJsDialog(JsDialogType.Prompt, url, message, defaultValue.orEmpty(), result)

    override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean =
        dispatchJsDialog(JsDialogType.BeforeUnload, url, message, "", result)

    private fun dispatchJsDialog(type: JsDialogType, url: String, message: String, defaultValue: String, result: JsResult): Boolean {
        if (!isCustomJsDialogsEnabled()) return false
        if (!isActive()) {
            result.cancel()
            return true
        }
        onJsDialog(
            JsDialogRequest(
                type = type,
                url = url,
                message = message,
                defaultValue = defaultValue,
                onConfirm = { value ->
                    if (result is JsPromptResult) result.confirm(value) else result.confirm()
                },
                onCancel = { result.cancel() }
            )
        )
        return true
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val hijackedFullscreenExit = SystemClock.elapsedRealtime() - fullscreenExitedAtMs < FULLSCREEN_EXIT_POPUP_GUARD_MS
        if (resultMsg == null || isFullscreenActive() || hijackedFullscreenExit) return false
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

    companion object {
        private const val FULLSCREEN_EXIT_POPUP_GUARD_MS = 700L
    }
}

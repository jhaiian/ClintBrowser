package com.jhaiian.clint.browser.delegates

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.dialogs.ColorPickerRequest
import java.lang.ref.WeakReference

class ColorPickerBridge(private val activity: MainActivity, private val webView: WebView) {

    @JavascriptInterface
    fun isEnabled(): Boolean = activity.prefs.getBoolean("custom_color_picker_enabled", true)

    @JavascriptInterface
    fun onColorOpen(id: String, value: String) {
        activity.runOnUiThread {
            if (activity.tabManager.activeTab?.webView !== webView) return@runOnUiThread
            activity.uiState.colorPickerRequest = ColorPickerRequest(id, value, WeakReference(webView))
        }
    }
}

package com.jhaiian.clint.browser.webview

import android.content.Context
import android.util.SparseArray
import android.view.ViewStructure
import android.view.autofill.AutofillValue
import android.webkit.WebView

class ClintWebView(context: Context) : WebView(context) {

    @Volatile
    var autofillAllowed: Boolean = true

    override fun loadUrl(url: String) {
        super.loadUrl(upgradeForHttpsOnly(url))
    }

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        super.loadUrl(upgradeForHttpsOnly(url), additionalHttpHeaders)
    }

    private fun upgradeForHttpsOnly(url: String): String =
        (webViewClient as? ClintWebViewClient)?.upgradeForHttpsOnly(url) ?: url

    override fun onProvideAutofillVirtualStructure(structure: ViewStructure?, flags: Int) {
        if (!autofillAllowed) return
        super.onProvideAutofillVirtualStructure(structure, flags)
    }

    override fun autofill(values: SparseArray<AutofillValue>) {
        if (!autofillAllowed) return
        super.autofill(values)
    }
}

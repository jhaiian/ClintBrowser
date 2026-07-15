package com.jhaiian.clint.tabs

import android.webkit.WebView
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "",
    val isIncognito: Boolean = false,
    val isRefreshLinkTab: Boolean = false,
    val openerTabId: String? = null,
    val webView: WebView
)

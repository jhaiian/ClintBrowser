package com.jhaiian.clint.browser.webview

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebSettings
import android.webkit.WebView
import com.jhaiian.clint.settings.datasaver.DataSaverSiteException
import com.jhaiian.clint.settings.datasaver.DataSaverMode

internal object DataSaverCacheMode {

    const val PREF_CACHE_FIRST = "data_saver_cache_first"

    fun prepare(context: Context, prefs: SharedPreferences, view: WebView, url: String?, isIncognito: Boolean) {
        if (isIncognito) return
        view.settings.cacheMode = if (shouldPreferCache(context, prefs, url)) {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        } else {
            WebSettings.LOAD_DEFAULT
        }
    }

    fun reset(view: WebView, isIncognito: Boolean) {
        if (isIncognito) return
        if (view.settings.cacheMode != WebSettings.LOAD_DEFAULT) {
            view.settings.cacheMode = WebSettings.LOAD_DEFAULT
        }
    }

    private fun shouldPreferCache(context: Context, prefs: SharedPreferences, url: String?): Boolean {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return false
        if (!DataSaverMode.isActive(prefs)) return false
        if (!prefs.getBoolean(PREF_CACHE_FIRST, true)) return false
        return !DataSaverSiteException.isExceptedUrl(context, url)
    }
}

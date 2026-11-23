package com.jhaiian.clint.browser.delegates

import android.content.Intent
import android.net.Uri
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.home.isClintHomeUrl
import com.jhaiian.clint.browser.webview.WebAppearance
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
import com.jhaiian.clint.settings.webtheme.WebThemeActivity
import com.jhaiian.clint.tabs.BrowserTab
import com.jhaiian.clint.util.registeredDomain

private fun webThemeHostOf(url: String?): String? {
    if (url.isNullOrEmpty() || !url.startsWith("http") || isClintHomeUrl(url)) return null
    return runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotEmpty() }
}

internal fun MainActivity.isWebThemeSaveStateEnabled(): Boolean =
    prefs.getString(WebThemeActivity.PREF_WEB_THEME_SAVE_STATE, WebThemeActivity.VALUE_DO_NOT_SAVE) ==
        WebThemeActivity.VALUE_SAVE_STATE

internal fun MainActivity.resolveWebThemeFollow(url: String?, incognito: Boolean): Boolean {
    if (isWebThemeSaveStateEnabled()) {
        val host = webThemeHostOf(url)
        if (host != null) {
            if (incognito) incognitoWebThemeOverrides[registeredDomain(host)]?.let { return it }
            when (SitePermissionManager.getState(this, host, SitePermissionDatabase.TYPE_WEB_THEME)) {
                SitePermissionDatabase.STATE_ALLOW -> return true
                SitePermissionDatabase.STATE_DENY -> return false
            }
        }
    }
    return webThemeSessionFollow ?: WebAppearance.followsAppTheme(prefs)
}

internal fun MainActivity.applyWebTheme(tab: BrowserTab) {
    val follow = resolveWebThemeFollow(tab.pendingUrl ?: tab.webView.url ?: tab.url, tab.isIncognito)
    WebAppearance.applyDarkMode(tab.webView.settings, prefs, follow)
    WebAppearance.syncContentScheme(tab.webView, prefs, follow)
}

internal fun MainActivity.applyWebThemeToAllTabs() {
    tabManager.tabs.forEach { applyWebTheme(it) }
}

internal fun MainActivity.syncWebThemeForUrl(url: String) {
    val tab = tabManager.activeTab ?: return
    val follow = resolveWebThemeFollow(url, tab.isIncognito)
    WebAppearance.applyDarkMode(tab.webView.settings, prefs, follow)
    WebAppearance.syncContentScheme(tab.webView, prefs, follow)
}

internal fun MainActivity.toggleWebTheme() {
    val tab = tabManager.activeTab ?: return
    val url = tab.webView.url
    val host = webThemeHostOf(url)
    val newFollow = !resolveWebThemeFollow(url, tab.isIncognito)
    if (host != null && isWebThemeSaveStateEnabled()) {
        if (tab.isIncognito) {
            incognitoWebThemeOverrides[registeredDomain(host)] = newFollow
        } else {
            SitePermissionManager.setState(
                this, host, SitePermissionDatabase.TYPE_WEB_THEME,
                if (newFollow) SitePermissionDatabase.STATE_ALLOW else SitePermissionDatabase.STATE_DENY
            )
        }
    } else {
        webThemeSessionFollow = newFollow
    }
    applyWebTheme(tab)
    if (!WebAppearance.hasNativeDarkening()) tab.webView.reload()
}

internal fun MainActivity.openWebThemeSettings() {
    startActivity(Intent(this, WebThemeActivity::class.java))
}

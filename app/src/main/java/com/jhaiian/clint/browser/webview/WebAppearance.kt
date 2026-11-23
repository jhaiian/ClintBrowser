package com.jhaiian.clint.browser.webview

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.theme.ThemeMode

class WebContentContext(base: Context, private var dark: Boolean) :
    ContextThemeWrapper(base, WebAppearance.styleFor(dark)) {

    fun update(webView: WebView, newDark: Boolean) {
        if (newDark == dark) return
        dark = newDark
        theme.applyStyle(WebAppearance.styleFor(newDark), true)
        val config = Configuration(webView.resources.configuration)
        val night = if (newDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        runCatching { webView.dispatchConfigurationChanged(config) }
    }
}

object WebAppearance {
    const val PREF_FOLLOW_APP_THEME = "web_follow_app_theme"
    const val DEFAULT_FOLLOW_APP_THEME = true
    private const val PREF_APP_THEME = "app_theme"

    fun followsAppTheme(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_FOLLOW_APP_THEME, DEFAULT_FOLLOW_APP_THEME)

    internal fun styleFor(dark: Boolean): Int =
        if (dark) R.style.ClintWebContentDark else R.style.ClintWebContentLight

    private fun appDark(prefs: SharedPreferences, systemDark: Boolean): Boolean =
        when (prefs.getString(PREF_APP_THEME, ThemeMode.SYSTEM)) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            else -> systemDark
        }

    fun webDark(prefs: SharedPreferences, follow: Boolean, systemDark: Boolean = ThemeMode.isSystemDark()): Boolean =
        if (follow) appDark(prefs, systemDark) else systemDark

    fun darkensPages(prefs: SharedPreferences, follow: Boolean = followsAppTheme(prefs)): Boolean =
        follow && appDark(prefs, ThemeMode.isSystemDark())

    fun signature(prefs: SharedPreferences): String = when {
        followsAppTheme(prefs) -> "app"
        ThemeMode.isSystemDark() -> "system-dark"
        else -> "system-light"
    }

    fun contentContext(base: Context, prefs: SharedPreferences): Context =
        if (followsAppTheme(prefs)) base else trackedContentContext(base, prefs, false)

    fun trackedContentContext(base: Context, prefs: SharedPreferences, follow: Boolean): Context =
        WebContentContext(base, webDark(prefs, follow))

    fun syncContentScheme(webView: WebView, prefs: SharedPreferences, follow: Boolean) {
        (webView.context as? WebContentContext)?.update(webView, webDark(prefs, follow))
    }

    @Suppress("DEPRECATION")
    fun applyDarkMode(
        settings: WebSettings,
        prefs: SharedPreferences,
        follow: Boolean = followsAppTheme(prefs),
        systemDark: Boolean = ThemeMode.isSystemDark()
    ) {
        val appIsDark = appDark(prefs, systemDark)
        when {
            WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) ->
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, follow && appIsDark)
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) -> {
                val dark = if (follow) appIsDark else systemDark
                WebSettingsCompat.setForceDark(
                    settings,
                    if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    WebSettingsCompat.setForceDarkStrategy(
                        settings,
                        if (follow) WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                        else WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun hasNativeDarkening(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) ||
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
}

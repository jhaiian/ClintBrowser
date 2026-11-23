package com.jhaiian.clint.browser.webview

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.webkit.WebView
import androidx.preference.PreferenceManager
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jhaiian.clint.customtheme.resolveThemeChrome
import com.jhaiian.clint.settings.accessibility.AccessibilityPrefs
import com.jhaiian.clint.ui.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.WeakHashMap

object WebAccessibility {

    private const val COLOR_FILTER_PLACEHOLDER = "__CLINT_CFX__"
    private const val COLOR_FILTER_OFF_CONFIG = "{}"

    private val forceZoomEnabled = WeakHashMap<WebView, Boolean>()
    private val forceZoomHandlers = WeakHashMap<WebView, ScriptHandler>()
    private val colorFilterApplied = WeakHashMap<WebView, String>()
    private val colorFilterHandlers = WeakHashMap<WebView, ScriptHandler>()
    private var forceZoomScript: String? = null
    private var colorFilterTemplate: String? = null

    fun apply(context: Context, webView: WebView) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        applyFontSize(context, prefs, webView)
        applyForceZoom(context, prefs, webView)
        applyColorFilter(context, webView, colorFilterConfig(context, prefs))
    }

    fun applyAll(context: Context, webViews: List<WebView>) {
        if (webViews.isEmpty()) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val config = colorFilterConfig(context, prefs)
        webViews.forEach { webView ->
            applyFontSize(context, prefs, webView)
            applyForceZoom(context, prefs, webView)
            applyColorFilter(context, webView, config)
        }
    }

    private fun applyFontSize(context: Context, prefs: SharedPreferences, webView: WebView) {
        val zoom = if (AccessibilityPrefs.autoFontSize(prefs)) {
            Math.round(context.resources.configuration.fontScale * 100f).coerceAtLeast(1)
        } else {
            AccessibilityPrefs.fontSizePercent(prefs)
        }
        if (webView.settings.textZoom != zoom) webView.settings.textZoom = zoom
    }

    private fun applyForceZoom(context: Context, prefs: SharedPreferences, webView: WebView) {
        val enabled = AccessibilityPrefs.forceZoom(prefs)
        if (forceZoomEnabled[webView] == enabled) return
        val first = !forceZoomEnabled.containsKey(webView)
        forceZoomEnabled[webView] = enabled
        runCatching { forceZoomHandlers.remove(webView)?.remove() }
        if (!enabled) return
        val script = forceZoomScript ?: loadAsset(context, "force_zoom.js").also { forceZoomScript = it }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching { forceZoomHandlers[webView] = WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*")) }
        }
        if (!first) webView.evaluateJavascript(script, null)
    }

    private fun applyColorFilter(context: Context, webView: WebView, config: String) {
        if (colorFilterApplied[webView] == config) return
        val first = !colorFilterApplied.containsKey(webView)
        colorFilterApplied[webView] = config
        runCatching { colorFilterHandlers.remove(webView)?.remove() }
        val template = colorFilterTemplate ?: loadAsset(context, "color_filter.js").also { colorFilterTemplate = it }
        val script = template.replace(COLOR_FILTER_PLACEHOLDER, config)
        val enabled = config != COLOR_FILTER_OFF_CONFIG
        if (enabled && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching { colorFilterHandlers[webView] = WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*")) }
        }
        if (!first) webView.evaluateJavascript(script, null)
    }

    private fun colorFilterConfig(context: Context, prefs: SharedPreferences): String {
        val mode = AccessibilityPrefs.colorFilter(prefs)
        if (mode == AccessibilityPrefs.COLOR_FILTER_OFF) return COLOR_FILTER_OFF_CONFIG
        val config = JSONObject().put("m", mode)
        if (mode == AccessibilityPrefs.COLOR_FILTER_MONOCHROME) config.put("t", colorFilterTint(context, prefs))
        return config.toString()
    }

    private fun colorFilterTint(context: Context, prefs: SharedPreferences): JSONArray {
        val theme = prefs.getString("app_theme", "system") ?: "system"
        val primary = runCatching {
            resolveThemeChrome(
                context,
                if (ThemeMode.isLight(theme)) "light" else "dark",
                prefs.getString("accent_color", "material_you") ?: "material_you",
                prefs.getString("surface_intensity", "soft_tint") ?: "soft_tint"
            ).primary
        }.getOrDefault(0xFF3F6FD8.toInt())
        return JSONArray()
            .put(Color.red(primary) / 255.0)
            .put(Color.green(primary) / 255.0)
            .put(Color.blue(primary) / 255.0)
    }

    private fun loadAsset(context: Context, name: String): String =
        context.assets.open("JavaScript/$name").bufferedReader().use { it.readText() }
}

package com.jhaiian.clint.webview

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.network.DohManager

class ClintWebViewClient(
    private val prefs: SharedPreferences,
    private val isActive: () -> Boolean = { true },
    private val onPageStartedCallback: (String) -> Unit = {},
    private val onPageFinishedCallback: (String) -> Unit = {},
    private val onTabUrlUpdatedCallback: (WebView, String) -> Unit = { _, _ -> },
    private val getDesktopHeaders: () -> Map<String, String>? = { null }
) : WebViewClient() {

    private val cooldownDomains = mutableMapOf<String, Long>()
    private var pendingHeaderLoad: String? = null

    companion object {
        private const val COOLDOWN_MS = 4000L
    }

    private val trackerHosts = setOf(
        "googletagmanager.com", "google-analytics.com", "analytics.google.com",
        "doubleclick.net", "googlesyndication.com", "adservice.google.com",
        "connect.facebook.net", "scorecardresearch.com", "quantserve.com",
        "amazon-adsystem.com", "ads.twitter.com", "static.ads-twitter.com",
        "pixel.facebook.com", "an.facebook.com", "stats.g.doubleclick.net",
        "pagead2.googlesyndication.com"
    )

    private fun registeredDomain(host: String): String {
        val parts = host.split(".")
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    private fun isInCooldown(host: String): Boolean {
        val domain = registeredDomain(host)
        val timestamp = cooldownDomains[domain] ?: return false
        if (System.currentTimeMillis() - timestamp >= COOLDOWN_MS) {
            cooldownDomains.remove(domain)
            return false
        }
        return true
    }

    private fun startCooldown(host: String) {
        cooldownDomains[registeredDomain(host)] = System.currentTimeMillis()
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        pendingHeaderLoad = null
        Uri.parse(url).host?.let { host ->
            DohManager.preResolveDns(host, prefs)
        }
        if (isActive()) onPageStartedCallback(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onTabUrlUpdatedCallback(view, url)
        if (isActive()) onPageFinishedCallback(url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onTabUrlUpdatedCallback(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase() ?: return true

        if (scheme == "intent") {
            return handleIntentScheme(view, uri.toString())
        }

        if (scheme != "http" && scheme != "https") {
            return handleCustomScheme(view, uri)
        }

        val host = uri.host ?: return false
        if (prefs.getBoolean("block_trackers", true)) {
            if (trackerHosts.any { host.contains(it) }) return true
        }
        DohManager.preResolveDns(host, prefs)

        if (request.isForMainFrame && tryOpenInApp(view, uri)) return true

        if (request.isForMainFrame) {
            val uriStr = uri.toString()
            if (pendingHeaderLoad == uriStr) {
                pendingHeaderLoad = null
                return false
            }
            val headers = getDesktopHeaders()
            if (headers != null) {
                pendingHeaderLoad = uriStr
                view.loadUrl(uriStr, headers)
                return true
            }
        }

        return false
    }

    private fun handleIntentScheme(view: WebView, uriString: String): Boolean {
        return try {
            val intent = Intent.parseUri(uriString, Intent.URI_INTENT_SCHEME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pm = view.context.packageManager
            val resolveInfo = resolveActivityCompat(pm, intent)
            val activity = view.context as? android.app.Activity

            if (resolveInfo != null && activity != null) {
                val appName = resolveInfo.loadLabel(pm).toString()
                val appIcon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                val sourceHost = view.url
                    ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                    ?:activity.getString(R.string.open_in_app_dialog_source_fallback)

                activity.runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(
                        activity, (activity as? ClintActivity)?.getDialogTheme() ?: R.style.ThemeOverlay_ClintBrowser_Dialog
                    )
                        .setTitle(activity.getString(R.string.open_in_app_dialog_title))
                        .setMessage(activity.getString(R.string.open_in_app_dialog_message, sourceHost, appName))
                        .setCancelable(false)
                        .setNegativeButton(activity.getString(R.string.open_in_app_dialog_stay_here)) { _, _ -> }
                        .setPositiveButton(activity.getString(R.string.open_in_app_dialog_confirm)) { _, _ ->
                            try { activity.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                        }
                    if (appIcon != null) builder.setIcon(appIcon)
                    builder.show()
                }
            } else {
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrEmpty()) view.loadUrl(fallbackUrl)
            }
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun handleCustomScheme(view: WebView, uri: Uri): Boolean {
        val context = view.context
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolveInfo = resolveActivityCompat(pm, intent)
        val activity = context as? android.app.Activity

        if (resolveInfo != null && activity != null) {
            val appName = resolveInfo.loadLabel(pm).toString()
            val appIcon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
            val sourceHost = view.url
                ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                ?: uri.scheme
                ?:activity.getString(R.string.open_in_app_dialog_source_fallback)

            activity.runOnUiThread {
                val builder = MaterialAlertDialogBuilder(
                    activity, (activity as? ClintActivity)?.getDialogTheme() ?: R.style.ThemeOverlay_ClintBrowser_Dialog
                )
                    .setTitle(activity.getString(R.string.open_in_app_dialog_title))
                    .setMessage(activity.getString(R.string.open_in_app_dialog_message, sourceHost, appName))
                    .setCancelable(false)
                    .setNegativeButton(activity.getString(R.string.open_in_app_dialog_stay_here)) { _, _ -> }
                    .setPositiveButton(activity.getString(R.string.open_in_app_dialog_confirm)) { _, _ ->
                        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                    }
                if (appIcon != null) builder.setIcon(appIcon)
                builder.show()
            }
            return true
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            true
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveActivityCompat(pm: PackageManager, intent: Intent): ResolveInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.resolveActivity(intent, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
    }

    fun resolveAppMatches(uri: Uri, context: android.content.Context): List<ResolveInfo> {
        val pm = context.packageManager
        val browserPackages = (
            queryActivities(pm, Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("http://example.com/")
            }) +
            queryActivities(pm, Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("https://example.com/")
            })
        ).map { it.activityInfo.packageName }.toSet()

        return queryActivities(pm, Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
        }).filter { ri ->
            val pkg = ri.activityInfo.packageName
            pkg != context.packageName && pkg !in browserPackages
        }
    }

    fun tryOpenInApp(view: WebView, uri: Uri): Boolean {
        val uriStr = uri.toString()
        val host = uri.host ?: uriStr

        if (isInCooldown(host)) return false

        val context = view.context
        val pm = context.packageManager

        val browserPackages = (
            queryActivities(pm, Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("http://example.com/")
            }) +
            queryActivities(pm, Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("https://example.com/")
            })
        ).map { it.activityInfo.packageName }.toSet()

        val appMatches = queryActivities(pm, Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
        }).filter { ri ->
            val pkg = ri.activityInfo.packageName
            pkg != context.packageName && pkg !in browserPackages
        }

        if (appMatches.isEmpty()) return false

        val activity = context as? android.app.Activity ?: return false

        activity.runOnUiThread {
            val dp = context.resources.displayMetrics.density

            if (appMatches.size == 1) {
                val match = appMatches[0]
                val appName = match.loadLabel(pm).toString()
                val appIcon = runCatching { match.loadIcon(pm) }.getOrNull()
                val specificIntent = Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(match.activityInfo.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                val builder = MaterialAlertDialogBuilder(
                    activity, (activity as? ClintActivity)?.getDialogTheme() ?: R.style.ThemeOverlay_ClintBrowser_Dialog
                )
                    .setTitle(activity.getString(R.string.open_in_app_dialog_title))
                    .setMessage(activity.getString(R.string.open_in_app_dialog_message, host, appName))
                    .setCancelable(false)
                    .setNegativeButton(activity.getString(R.string.open_in_app_dialog_stay_here)) { _, _ ->
                        startCooldown(host)
                        val h = getDesktopHeaders()
                        if (h != null) view.loadUrl(uriStr, h) else view.loadUrl(uriStr)
                    }
                    .setPositiveButton(activity.getString(R.string.open_in_app_dialog_confirm)) { _, _ ->
                        try { context.startActivity(specificIntent) } catch (_: ActivityNotFoundException) {}
                    }
                if (appIcon != null) builder.setIcon(appIcon)
                builder.show()
            } else {
                val listLayout = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
                }

                val dialog = MaterialAlertDialogBuilder(
                    activity, (activity as? ClintActivity)?.getDialogTheme() ?: R.style.ThemeOverlay_ClintBrowser_Dialog
                )
                    .setTitle(activity.getString(R.string.open_in_app_chooser_title))
                    .setMessage(activity.getString(R.string.open_in_app_chooser_message, host))
                    .setView(android.widget.ScrollView(context).apply { addView(listLayout) })
                    .setCancelable(false)
                    .setNegativeButton(activity.getString(R.string.open_in_app_dialog_stay_here)) { _, _ ->
                        startCooldown(host)
                        val h = getDesktopHeaders()
                        if (h != null) view.loadUrl(uriStr, h) else view.loadUrl(uriStr)
                    }
                    .show()

                appMatches.forEach { ri ->
                    val appName = ri.loadLabel(pm).toString()
                    val appIcon = runCatching { ri.loadIcon(pm) }.getOrNull()
                    val specificIntent = Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(ri.activityInfo.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    val row = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(
                            (20 * dp).toInt(), (12 * dp).toInt(),
                            (20 * dp).toInt(), (12 * dp).toInt()
                        )
                        background = android.util.TypedValue().let { tv ->
                            context.theme.resolveAttribute(
                                android.R.attr.selectableItemBackground, tv, true
                            )
                            androidx.core.content.ContextCompat.getDrawable(context, tv.resourceId)
                        }
                    }
                    if (appIcon != null) {
                        val iconSize = (32 * dp).toInt()
                        row.addView(android.widget.ImageView(context).apply {
                            setImageDrawable(appIcon)
                            layoutParams = android.widget.LinearLayout.LayoutParams(iconSize, iconSize)
                                .also { it.marginEnd = (16 * dp).toInt() }
                        })
                    }
                    row.addView(android.widget.TextView(context).apply {
                        text = appName
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 15f
                    })
                    row.setOnClickListener {
                        dialog.dismiss()
                        try { context.startActivity(specificIntent) } catch (_: ActivityNotFoundException) {}
                    }
                    listLayout.addView(row)
                }
            }
        }
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val host = request.url.host ?: return super.shouldInterceptRequest(view, request)
        if (prefs.getBoolean("block_trackers", true)) {
            if (trackerHosts.any { host.contains(it) }) {
                return WebResourceResponse("text/plain", "UTF-8", null)
            }
        }
        DohManager.preResolveDns(host, prefs)
        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }
}

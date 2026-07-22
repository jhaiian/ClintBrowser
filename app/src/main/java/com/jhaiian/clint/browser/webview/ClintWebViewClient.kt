package com.jhaiian.clint.browser.webview

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
import com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ClintWebViewClient(
    private val prefs: SharedPreferences,
    private val isActive: () -> Boolean = { true },
    private val onPageStartedCallback: (String) -> Unit = {},
    private val onPageFinishedCallback: (String) -> Unit = {},
    private val onTabUrlUpdatedCallback: (WebView, String) -> Unit = { _, _ -> },
    private val getDesktopHeaders: () -> Map<String, String>? = { null },
    private val getTabId: () -> String = { "" }
) : WebViewClient() {

    @Volatile private var cachedPageUrl: String? = null

    private val cooldownDomains = mutableMapOf<String, Long>()
    private var pendingHeaderLoad: String? = null

    // Caches the Quiver Guard site-exception lookup for the current page's host.
    // shouldInterceptRequest() is invoked once per subresource - often dozens of
    // times per page load thanks to images alone - and SitePermissionManager.getState()
    // is a synchronous SQLite query plus a public-suffix domain computation, so
    // repeating it per-request rather than per-navigation was adding a real,
    // cumulative DB round-trip to every single image/script/style fetch on the
    // page. The exception state can't change mid-navigation from anything the
    // WebView itself does, so it's safe to compute once per host and reuse it
    // for every subsequent request until the next page starts loading.
    @Volatile private var exceptionCacheHost: String? = null
    @Volatile private var exceptionCacheValid: Boolean = false
    @Volatile private var exceptionCacheState: Boolean = false
    private val exceptionCacheLock = Any()

    companion object {
        private const val COOLDOWN_MS = 4000L
    }

    private fun isQuiverGuardExcepted(context: android.content.Context, pageHost: String): Boolean {
        if (exceptionCacheValid && exceptionCacheHost == pageHost) return exceptionCacheState
        synchronized(exceptionCacheLock) {
            if (exceptionCacheValid && exceptionCacheHost == pageHost) return exceptionCacheState
            val state = SitePermissionManager.getState(
                context, pageHost, SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
            ) != null
            exceptionCacheHost = pageHost
            exceptionCacheState = state
            exceptionCacheValid = true
            return state
        }
    }

    private fun registeredDomain(host: String): String =
        "https://$host".toHttpUrlOrNull()?.topPrivateDomain() ?: host

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
        cachedPageUrl = url
        pendingHeaderLoad = null
        // Invalidate the per-host exception cache on every navigation so a
        // change made via the Quiver Guard exception toggle (which reloads the
        // tab) is picked up on the very next request instead of serving a
        // stale cached result for this host.
        exceptionCacheValid = false
        if (isActive()) onPageStartedCallback(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        cachedPageUrl = url
        onTabUrlUpdatedCallback(view, url)
        if (isActive()) onPageFinishedCallback(url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        cachedPageUrl = url
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

        if (scheme == "http" && request.isForMainFrame && prefs.getBoolean("https_only", true)) {
            val host = uri.host ?: ""
            val isIpAddress = host.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))
            if (!isIpAddress) {
                val httpsUri = uri.buildUpon().scheme("https").build()
                view.loadUrl(httpsUri.toString())
                return true
            }
        }

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
                    view.pauseTimers()
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
                    val d = builder.create()
                    d.setOnDismissListener { view.resumeTimers() }
                    (activity as? ClintActivity)?.applyStatusBarFlagToDialog(d)
                    d.show()
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
                view.pauseTimers()
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
                val d = builder.create()
                d.setOnDismissListener { view.resumeTimers() }
                (activity as? ClintActivity)?.applyStatusBarFlagToDialog(d)
                d.show()
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
                val d = builder.create()
                (activity as? ClintActivity)?.applyStatusBarFlagToDialog(d)
                d.show()
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
                    .create()
                (activity as? ClintActivity)?.applyStatusBarFlagToDialog(dialog)
                dialog.show()

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
        if (request.url.host == null) return super.shouldInterceptRequest(view, request)

        val quiverGuardEnabled = prefs.getBoolean("quiver_guard_enabled", false)
        if (quiverGuardEnabled) {
            val pageHost = cachedPageUrl?.let {
                runCatching { android.net.Uri.parse(it).host }.getOrNull()
            }
            val isExcepted = pageHost != null && isQuiverGuardExcepted(view.context.applicationContext, pageHost)
            if (!isExcepted) {
                val blocked = QuiverGuardWebIntegration.shouldInterceptRequest(
                    context = view.context.applicationContext,
                    request = request,
                    pageUrl = cachedPageUrl,
                    tabId = getTabId(),
                    isQuiverGuardEnabled = true
                )
                if (blocked != null) return blocked
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }
}

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
import com.jhaiian.clint.R
import com.jhaiian.clint.blocker.engine.WebsiteBlockerEngine
import com.jhaiian.clint.blocker.engine.WebsiteBlockerWebIntegration
import com.jhaiian.clint.mediacapture.MediaCaptureDetector
import com.jhaiian.clint.mediacapture.MediaCaptureStore
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
    private val onWebsiteBlockedCallback: (String) -> Unit = {},
    private val getDesktopHeaders: () -> Map<String, String>? = { null },
    private val getTabId: () -> String = { "" }
) : WebViewClient() {

    @Volatile private var cachedPageUrl: String? = null

    private val cooldownDomains = mutableMapOf<String, Long>()
    private var pendingHeaderLoad: String? = null

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
        MediaCaptureStore.updatePageUrl(getTabId(), url)
        pendingHeaderLoad = null

        exceptionCacheValid = false
        MediaCaptureStore.clearForTab(getTabId())
        if (isActive()) onPageStartedCallback(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        cachedPageUrl = url
        MediaCaptureStore.updatePageUrl(getTabId(), url)
        onTabUrlUpdatedCallback(view, url)
        if (isActive()) onPageFinishedCallback(url) else view.evaluateJavascript(TabMediaControl.PAUSE_SCRIPT, null)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        cachedPageUrl = url
        MediaCaptureStore.updatePageUrl(getTabId(), url)
        MediaCaptureStore.clearForTab(getTabId())
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
                    val mainActivity = activity as? com.jhaiian.clint.browser.MainActivity
                    if (mainActivity == null) {
                        try { activity.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                        view.resumeTimers()
                    } else {
                        mainActivity.uiState.openInAppRequest = com.jhaiian.clint.browser.webview.OpenInAppRequest(
                            host = sourceHost,
                            matches = listOf(com.jhaiian.clint.browser.webview.OpenInAppMatch(appName, appIcon, resolveInfo.activityInfo.packageName)),
                            onStayHere = { view.resumeTimers() },
                            onOpenApp = {
                                view.resumeTimers()
                                try { activity.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                            }
                        )
                    }
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
                val mainActivity = activity as? com.jhaiian.clint.browser.MainActivity
                if (mainActivity == null) {
                    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                    view.resumeTimers()
                } else {
                    mainActivity.uiState.openInAppRequest = com.jhaiian.clint.browser.webview.OpenInAppRequest(
                        host = sourceHost,
                        matches = listOf(com.jhaiian.clint.browser.webview.OpenInAppMatch(appName, appIcon, resolveInfo.activityInfo.packageName)),
                        onStayHere = { view.resumeTimers() },
                        onOpenApp = {
                            view.resumeTimers()
                            try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                        }
                    )
                }
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

        val activity = context as? com.jhaiian.clint.browser.MainActivity ?: return false

        activity.runOnUiThread {
            val matches = appMatches.map { ri ->
                com.jhaiian.clint.browser.webview.OpenInAppMatch(
                    label = ri.loadLabel(pm).toString(),
                    icon = runCatching { ri.loadIcon(pm) }.getOrNull(),
                    packageName = ri.activityInfo.packageName
                )
            }
            activity.uiState.openInAppRequest = com.jhaiian.clint.browser.webview.OpenInAppRequest(
                host = host,
                matches = matches,
                onStayHere = {
                    startCooldown(host)
                    val h = getDesktopHeaders()
                    if (h != null) view.loadUrl(uriStr, h) else view.loadUrl(uriStr)
                },
                onOpenApp = { packageName ->
                    val specificIntent = Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(specificIntent) } catch (_: ActivityNotFoundException) {}
                }
            )
        }
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (request.url.host == null) return super.shouldInterceptRequest(view, request)

        if (prefs.getBoolean(com.jhaiian.clint.mediacapture.MEDIA_CAPTURE_ENABLED_PREF, true)) {
            MediaCaptureDetector.onRequestObserved(getTabId(), cachedPageUrl, request)
        }

        val websiteBlockerEnabled = prefs.getBoolean("website_blocker_enabled", false)
        if (request.isForMainFrame && WebsiteBlockerEngine.isActive && websiteBlockerEnabled) {
            if (WebsiteBlockerWebIntegration.isHostBlocked(request.url.host)) {
                onWebsiteBlockedCallback(request.url.toString())
                return WebResourceResponse("text/html", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
            }
        }

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

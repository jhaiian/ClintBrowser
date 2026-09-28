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
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.dialogs.HttpAuthRequest
import com.jhaiian.clint.browser.dialogs.SslWarningRequest
import com.jhaiian.clint.blocker.engine.WebsiteBlockerEngine
import com.jhaiian.clint.blocker.engine.WebsiteBlockerWebIntegration
import com.jhaiian.clint.mediacapture.MediaCaptureDetector
import com.jhaiian.clint.mediacapture.MediaCaptureStore
import com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration
import com.jhaiian.clint.settings.sitepermissions.SitePermissionActivity
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
    private val getTabId: () -> String = { "" },
    private val isCustomHttpAuthEnabled: () -> Boolean = { false },
    private val onHttpAuthRequest: (HttpAuthRequest) -> Unit = {},
    private val isCustomSslWarningEnabled: () -> Boolean = { false },
    private val onSslWarning: (SslWarningRequest) -> Unit = {},
    private val isIncognito: () -> Boolean = { false }
) : WebViewClient() {

    private val allowedSslErrors = mutableSetOf<String>()

    @Volatile private var cachedPageUrl: String? = null

    private val cooldownDomains = mutableMapOf<String, Long>()
    private var pendingHeaderLoad: String? = null
    private val httpFallbackHosts = mutableSetOf<String>()
    private val upgradedHostOrigins = mutableMapOf<String, String>()

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
        Uri.parse(url).host?.lowercase()?.let { upgradedHostOrigins.remove(it) }
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
            val hostKey = host.lowercase()
            if (!isIpAddress && hostKey !in httpFallbackHosts) {
                val httpsUri = uri.buildUpon().scheme("https").build()
                upgradedHostOrigins[hostKey] = uri.toString()
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

    private fun resolveOpenInAppMode(context: android.content.Context, host: String?): String {
        if (isIncognito()) return SitePermissionActivity.PREF_VALUE_ASK
        if (!host.isNullOrEmpty()) {
            val stored = SitePermissionManager.getState(context, host, SitePermissionDatabase.TYPE_OPEN_IN_APP)
            if (stored == SitePermissionDatabase.STATE_STAY || stored == SitePermissionDatabase.STATE_OPEN || stored == SitePermissionDatabase.STATE_ASK) return stored
        }
        return prefs.getString("site_perm_default_${SitePermissionDatabase.TYPE_OPEN_IN_APP}", SitePermissionActivity.PREF_VALUE_ASK)
            ?: SitePermissionActivity.PREF_VALUE_ASK
    }

    private fun rememberOpenInApp(context: android.content.Context, host: String?, state: String) {
        if (isIncognito() || host.isNullOrEmpty()) return
        SitePermissionManager.setState(context, host, SitePermissionDatabase.TYPE_OPEN_IN_APP, state)
    }

    private fun launchOrPromptExternalApp(
        view: WebView,
        activity: android.app.Activity,
        intent: Intent,
        resolveInfo: ResolveInfo,
        sourceHost: String,
        pageHost: String?
    ) {
        val open: () -> Unit = { try { activity.startActivity(intent) } catch (_: ActivityNotFoundException) {} }
        when (resolveOpenInAppMode(activity, pageHost)) {
            SitePermissionDatabase.STATE_OPEN -> activity.runOnUiThread { open() }
            SitePermissionDatabase.STATE_STAY -> Unit
            else -> {
                val pm = activity.packageManager
                val appName = resolveInfo.loadLabel(pm).toString()
                val appIcon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                activity.runOnUiThread {
                    view.pauseTimers()
                    val mainActivity = activity as? com.jhaiian.clint.browser.MainActivity
                    if (mainActivity == null) {
                        open()
                        view.resumeTimers()
                    } else {
                        mainActivity.uiState.openInAppRequest = com.jhaiian.clint.browser.webview.OpenInAppRequest(
                            host = sourceHost,
                            matches = listOf(com.jhaiian.clint.browser.webview.OpenInAppMatch(appName, appIcon, resolveInfo.activityInfo.packageName)),
                            showRemember = !isIncognito() && !pageHost.isNullOrEmpty(),
                            onStayHere = { remember ->
                                view.resumeTimers()
                                if (remember) rememberOpenInApp(activity, pageHost, SitePermissionDatabase.STATE_STAY)
                            },
                            onOpenApp = { _, remember ->
                                view.resumeTimers()
                                if (remember) rememberOpenInApp(activity, pageHost, SitePermissionDatabase.STATE_OPEN)
                                open()
                            }
                        )
                    }
                }
            }
        }
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
                val pageHost = view.url?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                val sourceHost = pageHost ?: activity.getString(R.string.open_in_app_dialog_source_fallback)
                launchOrPromptExternalApp(view, activity, intent, resolveInfo, sourceHost, pageHost)
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
            val pageHost = view.url?.let { runCatching { Uri.parse(it).host }.getOrNull() }
            val sourceHost = pageHost
                ?: uri.scheme
                ?: activity.getString(R.string.open_in_app_dialog_source_fallback)
            launchOrPromptExternalApp(view, activity, intent, resolveInfo, sourceHost, pageHost)
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

    fun tryOpenInApp(view: WebView, uri: Uri, force: Boolean = false): Boolean {
        val uriStr = uri.toString()
        val host = uri.host ?: uriStr

        if (!force && isInCooldown(host)) return false

        val context = view.context
        val mode = if (force) SitePermissionActivity.PREF_VALUE_ASK else resolveOpenInAppMode(context, uri.host)
        if (mode == SitePermissionDatabase.STATE_STAY) return false

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

        if (mode == SitePermissionDatabase.STATE_OPEN && appMatches.size == 1) {
            val directIntent = Intent(Intent.ACTION_VIEW, uri)
                .setPackage(appMatches[0].activityInfo.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.runOnUiThread {
                try { context.startActivity(directIntent) } catch (_: ActivityNotFoundException) {}
            }
            return true
        }

        val canRemember = !force && !isIncognito() && mode == SitePermissionDatabase.STATE_ASK && !uri.host.isNullOrEmpty()

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
                showRemember = canRemember,
                onStayHere = { remember ->
                    if (remember) rememberOpenInApp(context, uri.host, SitePermissionDatabase.STATE_STAY)
                    startCooldown(host)
                    val h = getDesktopHeaders()
                    if (h != null) view.loadUrl(uriStr, h) else view.loadUrl(uriStr)
                },
                onOpenApp = { packageName, remember ->
                    if (remember) rememberOpenInApp(context, uri.host, SitePermissionDatabase.STATE_OPEN)
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

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
        if (!isCustomHttpAuthEnabled() || !isActive()) {
            handler.cancel()
            return
        }
        val isSecure = view.url?.startsWith("http://", ignoreCase = true) != true
        onHttpAuthRequest(
            HttpAuthRequest(
                host = host,
                realm = realm.orEmpty(),
                isSecure = isSecure,
                onSignIn = { username, password -> handler.proceed(username, password) },
                onCancel = { handler.cancel() }
            )
        )
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        if (!request.isForMainFrame) return
        val hostKey = request.url.host?.lowercase() ?: return
        val origin = upgradedHostOrigins.remove(hostKey) ?: return
        httpFallbackHosts.add(hostKey)
        val headers = getDesktopHeaders()
        if (headers != null) view.loadUrl(origin, headers) else view.loadUrl(origin)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val host = hostOf(error.url).orEmpty()
        val key = "$host|${error.primaryError}"
        if (key in allowedSslErrors) {
            handler.proceed()
            return
        }
        if (!isCustomSslWarningEnabled() || !isActive() || !isMainFrameHost(view, host)) {
            handler.cancel()
            return
        }
        onSslWarning(
            SslWarningRequest(
                host = host,
                errorType = error.primaryError,
                onProceed = {
                    allowedSslErrors.add(key)
                    handler.proceed()
                },
                onCancel = { handler.cancel() }
            )
        )
    }

    private fun hostOf(url: String?): String? =
        url?.let { runCatching { Uri.parse(it).host }.getOrNull() }

    private fun isMainFrameHost(view: WebView, host: String): Boolean {
        if (host.isEmpty()) return false
        return listOf(cachedPageUrl, view.url, view.originalUrl).any { hostOf(it).equals(host, ignoreCase = true) }
    }
}

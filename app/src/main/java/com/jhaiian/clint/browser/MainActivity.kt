package com.jhaiian.clint.browser
import com.jhaiian.clint.browser.delegates.*
import com.jhaiian.clint.browser.sheets.*
import com.jhaiian.clint.browser.suggestions.*
import com.jhaiian.clint.browser.webview.*

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.abs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.R
import com.jhaiian.clint.BuildConfig
import com.jhaiian.clint.crash.CrashHandler
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.tabs.TabManager
import com.jhaiian.clint.ui.ClintSnackbarHost
import com.jhaiian.clint.ui.ConfirmDialogHostActivity
import com.jhaiian.clint.ui.OverlayHostActivity
import com.jhaiian.clint.ui.SnackbarHostActivity
import com.jhaiian.clint.ui.theme.ClintComposeTheme
import com.jhaiian.clint.ui.theme.ThemeMode
import com.jhaiian.clint.update.UpdateChecker
import androidx.webkit.ScriptHandler
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ClintActivity(), OverlayHostActivity, SnackbarHostActivity, ConfirmDialogHostActivity {

    companion object {
        const val EXTRA_REFRESH_LINK_MODE = "extra_refresh_link_mode"
        const val EXTRA_REFRESH_LINK_DOWNLOAD_ID = "extra_refresh_link_download_id"
        const val EXTRA_REFRESH_LINK_FILENAME = "extra_refresh_link_filename"
        const val EXTRA_REFRESH_LINK_ORIGINAL_URL = "extra_refresh_link_original_url"
        const val EXTRA_REFRESH_LINK_ORIGINAL_REFERER = "extra_refresh_link_original_referer"
        const val EXTRA_SHORTCUT_ID = "extra_shortcut_id"
    }

    data class RefreshLinkSession(
        val downloadId: Int,
        val filename: String,
        val originalUrl: String,
        val originalReferer: String,
        val previousTabIndex: Int
    )

    internal var refreshLinkSession: RefreshLinkSession? = null

    internal val uiState = MainUiState()

    override var confirmDialogConfig: com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig?
        get() = uiState.confirmDialogConfig
        set(value) { uiState.confirmDialogConfig = value }

    override var overlayContent by mutableStateOf<(@Composable () -> Unit)?>(null)

    override val snackbarHostState = SnackbarHostState()

    internal lateinit var webContainer: FrameLayout
    internal lateinit var swipeRefreshView: ClintSwipeRefreshLayout
    internal lateinit var fullscreenContainerView: FrameLayout

    internal lateinit var prefs: SharedPreferences
    internal val tabManager = TabManager()
    internal var isDesktopMode = false
    internal var desktopModeHost: String? = null
    internal var autoDesktopPendingReload: String? = null
    internal val desktopScriptHandlers = mutableMapOf<String, ScriptHandler>()
    internal val autoplayScriptHandlers = mutableMapOf<String, ScriptHandler>()
    internal val userScriptHandlers = mutableMapOf<String, ScriptHandler>()
    private var lastUserScriptsDataVersion = 0L
    internal val quiverGuardScriptHandlers = com.jhaiian.clint.quiver.engine.ScriptHandlerStore()
    internal val quiverGuardJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    internal var bottomBarAnimator2: android.animation.ValueAnimator? = null
    internal var hasWebBottomNav: Boolean = false
    internal var nestedScrollActive = false
    internal var canvasTouchActive = false
    internal var swipeGuardBlocked = false
    private var swipeGuardInitX = 0f
    private var swipeGuardInitY = 0f

    internal var fullscreenView: View? = null
    internal var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    internal var suggestionFetcher: SuggestionFetcher? = null
    internal var suggestionsBgThread: android.os.HandlerThread? = null
    internal var suggestionsBgHandler: android.os.Handler? = null
    internal var lastOnlineSuggestions: List<String> = emptyList()

    private var backPressedOnce = false
    private val backPressHandler = Handler(Looper.getMainLooper())

    internal var pendingFileChooserCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
    internal var pendingFileChooserParams: android.webkit.WebChromeClient.FileChooserParams? = null
    internal var filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
    internal var cameraImageUri: android.net.Uri? = null
    internal var cameraVideoUri: android.net.Uri? = null

    internal data class PendingDownload(
        val url: String,
        val filename: String,
        val userAgent: String,
        val referer: String,
        val cookies: String
    )

    internal var pendingDownload: PendingDownload? = null
    internal var downloadDialogFolderPickerCallback: ((android.net.Uri) -> Unit)? = null
    internal var pendingWebPermissionRequest: android.webkit.PermissionRequest? = null
    internal var pendingWebMicPermissionRequest: android.webkit.PermissionRequest? = null
    internal var pendingWebGeoOrigin: String? = null
    internal var pendingWebGeoCallback: android.webkit.GeolocationPermissions.Callback? = null
    internal var pendingBridgeNotifCallbackId: String? = null
    internal var pendingBridgeNotifOrigin: String? = null
    internal var pendingBridgeNotifWebView: android.webkit.WebView? = null

    internal val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (granted && pending != null) {
            ClintDownloadManager.enqueue(this, pending.url, pending.filename, pending.userAgent, pending.referer, pending.cookies)
        }
    }

    internal val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    internal val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val cb = pendingFileChooserCallback
        val params = pendingFileChooserParams
        pendingFileChooserCallback = null
        pendingFileChooserParams = null
        if (granted && cb != null && params != null) {
            launchFileChooser(cb, params)
        } else if (cb != null) {
            cb.onReceiveValue(null)
        }
    }

    internal val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchVoiceSearch()
    }

    internal val webCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingWebPermissionRequest
        pendingWebPermissionRequest = null
        if (granted && request != null) {
            showWebCameraDialog(request)
        } else {
            request?.deny()
        }
    }

    internal val webMicrophonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingWebMicPermissionRequest
        pendingWebMicPermissionRequest = null
        if (granted && request != null) {
            showWebMicDialog(request)
        } else {
            request?.deny()
        }
    }

    internal val webLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val origin = pendingWebGeoOrigin
        val callback = pendingWebGeoCallback
        pendingWebGeoOrigin = null
        pendingWebGeoCallback = null
        if (granted && origin != null && callback != null) {
            showWebLocationDialog(origin, callback)
        } else {
            callback?.invoke(origin ?: "", false, false)
        }
    }

    internal val webNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val callbackId = pendingBridgeNotifCallbackId
        val origin = pendingBridgeNotifOrigin
        val wv = pendingBridgeNotifWebView
        pendingBridgeNotifCallbackId = null
        pendingBridgeNotifOrigin = null
        pendingBridgeNotifWebView = null
        if (wv == null || callbackId == null) return@registerForActivityResult
        val safeId = callbackId.replace("'", "")
        if (granted && origin != null) {
            showWebNotificationPermissionFromBridge(wv, safeId, origin)
        } else {
            wv.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
        }
    }

    internal var pendingUserScriptNotify: Triple<String, String, String>? = null

    internal val userScriptNotifyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingUserScriptNotify
        pendingUserScriptNotify = null
        if (granted && pending != null) {
            postWebNotification(pending.first, pending.second, "", pending.third)
        }
    }

    internal val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { uiState.voiceResult = it }
        }
    }

    internal val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { clip.getItemAt(it).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                cameraImageUri != null -> arrayOf(cameraImageUri!!)
                else -> null
            }
        } else {
            cameraImageUri?.let { contentResolver.delete(it, null, null) }
            null
        }
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
        cameraImageUri = null
        cameraVideoUri = null
    }

    internal val downloadDialogFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            downloadDialogFolderPickerCallback?.invoke(uri)
        }
        downloadDialogFolderPickerCallback = null
    }

    internal var pendingShortcutIconCallback: ((android.graphics.Bitmap?) -> Unit)? = null

    internal val shortcutIconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val callback = pendingShortcutIconCallback
        pendingShortcutIconCallback = null
        val bitmap = uri?.let { picked ->
            runCatching { contentResolver.openInputStream(picked)?.use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
        }
        callback?.invoke(bitmap)
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "javascript_enabled" -> applyJavaScript()
            "block_third_party_cookies" -> applyCookiePolicy()
            "custom_user_agent" -> applyUserAgent()
            "quiver_guard_enabled" -> onQuiverGuardEnabled(prefs.getBoolean("quiver_guard_enabled", false))
            com.jhaiian.clint.mediacapture.MEDIA_CAPTURE_ENABLED_PREF -> updateMediaCaptureEnabledState()
            "user_scripts_enabled" -> applyUserScripts()
            "data_saver_enabled", "data_saver_disable_images", "data_saver_disable_autoplay" -> applyDataSaverSettings()
            "hide_bars_on_scroll" -> {
                if (!prefs.getBoolean("hide_bars_on_scroll", true)) {
                    animateBottomBarTo(0f, animated = false)
                }
            }
            "scroll_hide_mode" -> {
                if (prefs.getString("scroll_hide_mode", "off") == "off") {
                    animateBottomBarTo(0f, animated = false)
                }
            }
            "shortcut_frameless_enabled" -> {
                tabManager.framelessShortcutsEnabled = prefs.getBoolean("shortcut_frameless_enabled", true)
                updateTabCount()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webContainer = FrameLayout(this)
        swipeRefreshView = ClintSwipeRefreshLayout(this).apply {
            addView(webContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        fullscreenContainerView = FrameLayout(this)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("setup_complete", false)) {
            startActivity(android.content.Intent(this, com.jhaiian.clint.setup.SetupActivity::class.java))
            finish()
            return
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        updateMediaCaptureEnabledState()
        tabManager.framelessShortcutsEnabled = prefs.getBoolean("shortcut_frameless_enabled", true)
        lastUserScriptsDataVersion = com.jhaiian.clint.userscripts.UserScriptState.getDataVersion(this)
        applySystemUiVisibility()

        val startTheme = prefs.getString("app_theme", "system") ?: "system"
        setContent {
            ClintComposeTheme(theme = startTheme) {
                MainScreen(activity = this, state = uiState)
                overlayContent?.invoke()
                ClintSnackbarHost(hostState = snackbarHostState)
            }
        }

        ClintDownloadManager.createNotificationChannel(this)
        ClintDownloadManager.init(this)
        initializeQuiverGuardEngine()
        initializeWebsiteBlockerEngine()
        installServiceWorkerMediaCapture()
        observeQuiverGuardCounter()
        createWebNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!BuildConfig.IS_FDROID && prefs.getBoolean("check_update_on_launch", true)) {
            val skipOnMetered = prefs.getBoolean("skip_update_on_metered", true)
            val isBeta = prefs.getBoolean("beta_channel", false)
            if (!skipOnMetered || !isNetworkMetered()) {
                UpdateChecker.check(this, isBeta, silent = true)
            }
        }
        migrateScrollHideMode()
        setupSwipeRefresh()
        setupAddressBar()
        applyAddressBarPosition()
        val isRefreshLinkMode = intent.getBooleanExtra(EXTRA_REFRESH_LINK_MODE, false)
        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID)
        applyShortcutFrameless(shortcutId != null)
        if (isRefreshLinkMode) {
            val downloadId = intent.getIntExtra(EXTRA_REFRESH_LINK_DOWNLOAD_ID, -1)
            val filename = intent.getStringExtra(EXTRA_REFRESH_LINK_FILENAME) ?: ""
            val originalUrl = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_URL) ?: ""
            val originalReferer = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_REFERER) ?: ""
            setIntent(android.content.Intent())
            if (downloadId != -1) {
                restoreTabs()
                refreshLinkSession = RefreshLinkSession(downloadId, filename, originalUrl, originalReferer, tabManager.activeIndex)
                openRefreshLinkTab(originalReferer.ifEmpty { originalUrl.ifEmpty { getSearchEngineHomeUrl() } })
            } else if (!restoreTabs()) {
                openNewTab(isIncognito = false, url = getSearchEngineHomeUrl())
            }
        } else if (shortcutId != null) {
            val fallbackUrl = getUrlFromIntent(intent)
            setIntent(android.content.Intent())
            restoreTabs()
            openOrResumeShortcutTab(shortcutId, fallbackUrl)
        } else {
            val intentUrl = getUrlFromIntent(intent)
            setIntent(android.content.Intent())
            if (!intentUrl.isNullOrEmpty()) {
                restoreTabs()
                openNewTab(isIncognito = false, url = intentUrl)
            } else if (!restoreTabs()) {
                openNewTab(isIncognito = false, url = getSearchEngineHomeUrl())
            }
        }
        setupBackPressedDispatcher()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val isRefreshLinkMode = intent.getBooleanExtra(EXTRA_REFRESH_LINK_MODE, false)
        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID)
        val wasShortcutFrameless = uiState.isShortcutFrameless
        applyShortcutFrameless(shortcutId != null)
        if (isRefreshLinkMode) {
            val downloadId = intent.getIntExtra(EXTRA_REFRESH_LINK_DOWNLOAD_ID, -1)
            val filename = intent.getStringExtra(EXTRA_REFRESH_LINK_FILENAME) ?: ""
            val originalUrl = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_URL) ?: ""
            val originalReferer = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_REFERER) ?: ""
            setIntent(android.content.Intent())
            if (downloadId != -1) {
                refreshLinkSession = RefreshLinkSession(downloadId, filename, originalUrl, originalReferer, tabManager.activeIndex)
                openRefreshLinkTab(originalReferer.ifEmpty { originalUrl.ifEmpty { getSearchEngineHomeUrl() } })
            }
            return
        }
        if (shortcutId != null) {
            val fallbackUrl = getUrlFromIntent(intent)
            setIntent(android.content.Intent())
            openOrResumeShortcutTab(shortcutId, fallbackUrl)
            return
        }
        val url = getUrlFromIntent(intent)
        setIntent(android.content.Intent())
        if (wasShortcutFrameless) {
            exitShortcutFramelessToNormal()
        }
        if (!url.isNullOrEmpty()) {
            openNewTab(isIncognito = false, url = url)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        bottomBarAnimator2?.cancel()
        uiState.topBarFraction = 0f
        uiState.bottomBarFraction = 0f
        nestedScrollActive = false
        canvasTouchActive = false
        hasWebBottomNav = false
        swipeRefreshView.isEnabled = true
        updateMainContentInsets()
        val currentUserScriptsVersion = com.jhaiian.clint.userscripts.UserScriptState.getDataVersion(this)
        if (currentUserScriptsVersion != lastUserScriptsDataVersion) {
            lastUserScriptsDataVersion = currentUserScriptsVersion
            applyUserScripts()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemUiVisibility()
    }

    override fun onStop() {
        super.onStop()
        saveTabs()
        if (refreshLinkSession != null) {
            cleanupRefreshLinkTabs()
            refreshLinkSession = null
        }
    }

    override fun onSystemThemeChanged() {
        applyWebDarkModeToAllTabs()
        if (::swipeRefreshView.isInitialized) updateSwipeRefreshColors(uiState.isIncognito)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        if (refreshLinkSession != null) {
            cleanupRefreshLinkTabs()
            refreshLinkSession = null
        }
        tabManager.destroyAll()
        suggestionFetcher?.cancel()
        suggestionsBgThread?.quitSafely()
        quiverGuardJobs.clear()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeGuardInitX = ev.x
                swipeGuardInitY = ev.y
                swipeGuardBlocked = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!swipeGuardBlocked) {
                    swipeGuardBlocked = true
                    swipeRefreshView.isEnabled = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeGuardBlocked) {
                    val dx = abs(ev.x - swipeGuardInitX)
                    val dy = abs(ev.y - swipeGuardInitY)
                    if (dx > slop && dx >= dy) {
                        swipeGuardBlocked = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeGuardBlocked) {
                    swipeGuardBlocked = false
                    updateMainContentInsets()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupBackPressedDispatcher() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (uiState.websiteBlockedRequest != null) {
                    dismissWebsiteBlockedOverlay()
                    return
                }
                if (fullscreenView != null) {
                    exitFullscreen()
                    return
                }
                if (uiState.searchOverlayOpen) {
                    closeSearchOverlay()
                    return
                }
                val activeTab = tabManager.activeTab
                val wv = activeTab?.webView
                if (wv?.canGoBack() == true) {
                    wv.goBack()
                    return
                }
                if (activeTab != null && closePopupTabToOpener(activeTab)) {
                    return
                }
                handleExitConfirmation()
            }
        })
    }

    private fun handleExitConfirmation() {
        val mode = prefs.getString("exit_confirmation", "toast") ?: "toast"
        when (mode) {
            "off" -> finish()
            "dialog" -> showExitDialog()
            else -> handleToastExit()
        }
    }

    private fun handleToastExit() {
        if (backPressedOnce) {
            backPressHandler.removeCallbacksAndMessages(null)
            finish()
            return
        }
        backPressedOnce = true
        Toast.makeText(this, getString(R.string.exit_tap_again), Toast.LENGTH_SHORT).show()
        backPressHandler.postDelayed({ backPressedOnce = false }, 2000L)
    }

    private fun showExitDialog() {
        uiState.confirmDialogConfig = com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig(
            title = getString(R.string.exit_dialog_title),
            message = getString(R.string.exit_dialog_message),
            cancelable = false,
            positiveLabel = getString(R.string.exit_dialog_confirm),
            onPositive = { finish() },
            negativeLabel = getString(R.string.action_cancel)
        )
    }

    fun onTabSelected(index: Int) { captureActiveTabThumbnail(); tabManager.switchTo(index); attachActiveWebView() }
    fun onSwipeTabChange(direction: Int): Boolean {
        val visibleIndices = tabManager.tabs.indices.filter { !tabManager.isGhostTab(tabManager.tabs[it]) }
        val currentPos = visibleIndices.indexOf(tabManager.activeIndex)
        if (currentPos == -1) return false
        val newPos = currentPos - direction
        if (newPos !in visibleIndices.indices) return false
        onTabSelected(visibleIndices[newPos])
        return true
    }
    fun onTabClosed(index: Int) {
        val tab = tabManager.tabs.getOrNull(index)
        tab?.let {
            removeDesktopScript(it)
            onQuiverGuardTabClosed(it)
            com.jhaiian.clint.mediacapture.MediaCaptureStore.removeTab(it.id)
            if (!it.isIncognito) com.jhaiian.clint.ui.FaviconCache.evict(this, it.url)
            com.jhaiian.clint.tabs.TabThumbnailCache.evict(this, it.id)
        }
        val wasActive = index == tabManager.activeIndex
        tabManager.closeTab(index)
        resetProgressBar()
        if (tabManager.previews().isEmpty()) {
            openNewTab(false)
            return
        }
        val activeTab = tabManager.activeTab
        if (activeTab != null && tabManager.isGhostTab(activeTab)) {
            val idx = tabManager.tabs.indexOfFirst { !tabManager.isGhostTab(it) }
            if (idx != -1) { tabManager.switchTo(idx); attachActiveWebView() } else openNewTab(false)
        } else if (wasActive) attachActiveWebView()
        else updateTabCount()
    }
    fun onNewTab() { openNewTab(false) }
    fun onNewIncognitoTab() { openNewTab(true) }

    fun onMenuGoBack() { navGoBack() }
    fun onMenuGoForward() { navGoForward() }
    fun onMenuHome() { navGoHome() }
    fun onMenuRefreshOrStop() { navRefreshOrStop() }
    fun onMenuToggleBookmark() { navToggleBookmark() }
    fun onMenuNewTab() { openNewTab(false) }
    fun onMenuIncognito() { openNewTab(true) }
    fun onMenuShare() {
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, tabManager.activeTab?.webView?.url)
        }
        startActivity(android.content.Intent.createChooser(i, getString(R.string.share_url)))
    }
    fun onMenuOpenInApp() {
        val currentUrl = tabManager.activeTab?.webView?.url ?: return
        val currentUri = runCatching { android.net.Uri.parse(currentUrl) }.getOrNull() ?: return
        val webClient = tabManager.activeTab?.webView?.webViewClient as? com.jhaiian.clint.browser.webview.ClintWebViewClient ?: return
        val appMatches = webClient.resolveAppMatches(currentUri, this)
        if (appMatches.size == 1) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, currentUri)
                .setPackage(appMatches[0].activityInfo.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
        } else {
            webClient.tryOpenInApp(tabManager.activeTab?.webView ?: return, currentUri)
        }
    }
    fun onMenuCreateShortcut() {
        val tab = tabManager.activeTab ?: return
        val url = tab.webView.url
        if (url.isNullOrEmpty()) return
        uiState.createShortcutRequest = com.jhaiian.clint.browser.dialogs.CreateShortcutRequest(
            pageUrl = url,
            initialName = tab.title.ifBlank { url }
        )
    }
    fun onMenuDownloads() { startActivity(android.content.Intent(this, com.jhaiian.clint.downloads.DownloadsActivity::class.java)) }
    fun onMenuBookmarks() { startActivity(android.content.Intent(this, com.jhaiian.clint.bookmarks.BookmarksActivity::class.java)) }
    fun onMenuHistory() { startActivity(android.content.Intent(this, com.jhaiian.clint.history.HistoryActivity::class.java)) }
    fun onMenuDesktopMode() {
        isDesktopMode = !isDesktopMode

        val wv = tabManager.activeTab?.webView
        val currentWebUrl = wv?.url
        val host = currentWebUrl?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }

        desktopModeHost = if (isDesktopMode) host else null

        if (host != null && tabManager.activeTab?.isIncognito != true) {
            val shouldSave = prefs.getString(
                com.jhaiian.clint.settings.desktopmode.DesktopModeActivity.PREF_DESKTOP_MODE_SAVE_STATE,
                com.jhaiian.clint.settings.desktopmode.DesktopModeActivity.VALUE_SAVE_STATE
            ) == com.jhaiian.clint.settings.desktopmode.DesktopModeActivity.VALUE_SAVE_STATE

            if (isDesktopMode && shouldSave) {
                com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.setState(
                    this, host,
                    com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_DESKTOP_MODE,
                    com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW
                )
            } else if (!isDesktopMode && shouldSave) {
                com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.deleteEntry(
                    this, host,
                    com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_DESKTOP_MODE
                )
            }
        }

        tabManager.tabs.forEach { tab ->
            tab.webView.settings.userAgentString = buildUserAgent()
            applyUserAgentMetadata(tab.webView)
            if (isDesktopMode) addDesktopScript(tab) else removeDesktopScript(tab)
        }

        if (wv != null && !currentWebUrl.isNullOrEmpty()) {
            val headers = buildDesktopHeaders()
            if (headers != null) wv.loadUrl(currentWebUrl, headers) else wv.reload()
        } else wv?.reload()
    }
    fun onMenuSettings() { startActivity(android.content.Intent(this, com.jhaiian.clint.settings.SettingsActivity::class.java)) }
    fun onMenuDataSaver() {
        val enabled = !prefs.getBoolean("data_saver_enabled", false)
        prefs.edit().putBoolean("data_saver_enabled", enabled).apply()
    }
    fun onMenuOpenDataSaverSettings() {
        startActivity(android.content.Intent(this, com.jhaiian.clint.settings.SettingsActivity::class.java)
            .putExtra(com.jhaiian.clint.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "data_saver"))
    }
    fun onMenuOpenDownloadSettings() {
        startActivity(android.content.Intent(this, com.jhaiian.clint.settings.SettingsActivity::class.java)
            .putExtra(com.jhaiian.clint.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "download_settings"))
    }
    fun onMenuUserScripts() {
        com.jhaiian.clint.userscripts.UserScriptState.setEnabled(
            this, !com.jhaiian.clint.userscripts.UserScriptState.isEnabled(this)
        )
    }
    fun onMenuOpenUserScriptsSettings() {
        startActivity(android.content.Intent(this, com.jhaiian.clint.userscripts.UserScriptsActivity::class.java))
    }
    fun onMenuMediaCapture() {
        val enabled = !prefs.getBoolean(com.jhaiian.clint.mediacapture.MEDIA_CAPTURE_ENABLED_PREF, true)
        prefs.edit().putBoolean(com.jhaiian.clint.mediacapture.MEDIA_CAPTURE_ENABLED_PREF, enabled).apply()
    }
    fun onMenuQuiverGuard() {
        val enabled = !prefs.getBoolean("quiver_guard_enabled", false)
        if (enabled) {
            val filterListDb = com.jhaiian.clint.quiver.FilterListDatabase(this)
            val hasActive: Boolean
            try {
                hasActive = filterListDb.hasActiveFilterLists()
            } finally {
                filterListDb.close()
            }
            if (!hasActive) {
                startActivity(
                    android.content.Intent(this, com.jhaiian.clint.quiver.QuiverGuardActivity::class.java)
                        .putExtra(com.jhaiian.clint.quiver.QuiverGuardActivity.EXTRA_SHOW_SETUP_GUIDE, true)
                )
                return
            }
        }
        prefs.edit().putBoolean("quiver_guard_enabled", enabled).apply()
    }
    fun onMenuOpenQuiverGuardSettings() {
        startActivity(android.content.Intent(this, com.jhaiian.clint.quiver.QuiverGuardActivity::class.java))
    }
    fun onMenuDisableQuiverGuardForSite() {
        val tab = tabManager.activeTab ?: return
        val wv = tab.webView
        val currentUrl = wv.url ?: return
        if (!currentUrl.startsWith("http://") && !currentUrl.startsWith("https://")) return
        val host = runCatching { android.net.Uri.parse(currentUrl).host }.getOrNull() ?: return
        if (tab.isIncognito) return
        val isExcepted = com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.getState(
            this, host, com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
        ) != null
        if (isExcepted) {
            com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.deleteEntry(
                this, host, com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
            )
        } else {
            com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.setState(
                this, host,
                com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION,
                com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW
            )
            com.jhaiian.clint.quiver.engine.BlockedRequestCounter.resetTab(tab.id)
        }
        wv.reload()
    }
    fun onMenuWebsiteBlocker() {
        val enabled = !prefs.getBoolean("website_blocker_enabled", false)
        prefs.edit().putBoolean("website_blocker_enabled", enabled).apply()
    }
    fun onMenuOpenWebsiteBlockerSettings() {
        startActivity(android.content.Intent(this, com.jhaiian.clint.blocker.WebsiteBlockerActivity::class.java))
    }
    fun onMenuReaderMode() {
        val wv = tabManager.activeTab?.webView ?: return
        val pageUrl = wv.url ?: return
        val js = assets.open("JavaScript/reader_mode.js").bufferedReader().use { it.readText() }
        wv.evaluateJavascript(js) { raw ->
            if (isFinishing) return@evaluateJavascript
            val json = runCatching {
                val unescaped = raw?.removeSurrounding("\"")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\")
                    ?.replace("\\n", "\n")
                    ?.replace("\\t", "\t")
                    ?: ""
                org.json.JSONObject(unescaped)
            }.getOrNull() ?: return@evaluateJavascript
            val title = json.optString("title", "")
            val content = json.optString("content", "")
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val theme = prefs.getString("app_theme", "system") ?: "system"
            val isDark = !ThemeMode.isLight(theme)
            val bgColor = if (isDark) "#121212" else "#ffffff"
            val textColor = if (isDark) "#e0e0e0" else "#1a1a1a"
            val secondaryColor = if (isDark) "#aaaaaa" else "#555555"
            val linkColor = if (isDark) "#90caf9" else "#1a73e8"
            val html = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{font-family:Georgia,'Times New Roman',serif;font-size:18px;line-height:1.75;max-width:700px;margin:0 auto;padding:16px 20px 32px;background-color:$bgColor;color:$textColor;}
h1,h2,h3,h4,h5,h6{font-family:-apple-system,sans-serif;line-height:1.3;color:$textColor;}
a{color:$linkColor;}
img{max-width:100%;height:auto;display:block;margin:12px auto;}
pre,code{overflow-x:auto;font-size:14px;}
blockquote{border-left:3px solid $secondaryColor;margin:0;padding:4px 16px;color:$secondaryColor;}
figure{margin:12px 0;}
figcaption{font-size:13px;color:$secondaryColor;text-align:center;margin-top:4px;}
table{border-collapse:collapse;width:100%;}
td,th{border:1px solid $secondaryColor;padding:6px 8px;}
</style>
</head>
<body>$content</body>
</html>"""
            runOnUiThread {
                uiState.contentPreviewRequest = com.jhaiian.clint.browser.sheets.ContentPreviewRequest.forReaderMode(pageUrl, title, html)
            }
        }
    }

    inner class NestedScrollBridge {
        @android.webkit.JavascriptInterface
        fun onNestedScroll(active: Boolean) {
            runOnUiThread { nestedScrollActive = active }
        }
    }

    inner class CanvasTouchBridge {
        @android.webkit.JavascriptInterface
        fun onCanvasTouch(active: Boolean) {
            runOnUiThread { canvasTouchActive = active }
        }
    }

    inner class BottomNavBridge {
        @android.webkit.JavascriptInterface
        fun onBottomNavDetected(detected: Boolean) {
            runOnUiThread {
                if (detected && !hasWebBottomNav) {
                    hasWebBottomNav = true
                }
            }
        }
    }

    inner class NotificationBridge(private val webView: android.webkit.WebView) {
        @android.webkit.JavascriptInterface
        fun getPermissionState(origin: String): String {
            val tab = tabManager.tabs.find { it.webView == webView }
            if (tab?.isIncognito == true) return "denied"
            val rawOrigin = origin.trim()
            return when (com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.getState(
                this@MainActivity, rawOrigin,
                com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_NOTIFICATION
            )) {
                com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW -> "granted"
                com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.STATE_DENY -> "denied"
                else -> "default"
            }
        }

        @android.webkit.JavascriptInterface
        fun requestPermission(callbackId: String, origin: String) {
            runOnUiThread {
                val tab = tabManager.tabs.find { it.webView == webView }
                val safeId = callbackId.replace("'", "")
                if (tab?.isIncognito == true) {
                    webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
                    return@runOnUiThread
                }
                val rawOrigin = origin.trim()
                val stored = com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.getState(
                    this@MainActivity, rawOrigin,
                    com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_NOTIFICATION
                )
                when (stored) {
                    com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            !isSystemPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            pendingBridgeNotifCallbackId = safeId
                            pendingBridgeNotifOrigin = rawOrigin
                            pendingBridgeNotifWebView = webView
                            webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            webView.evaluateJavascript("window._ClintResolvePermission('$safeId','granted')", null)
                        }
                    }
                    com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.STATE_DENY -> {
                        webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
                    }
                    else -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            !isSystemPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            pendingBridgeNotifCallbackId = safeId
                            pendingBridgeNotifOrigin = rawOrigin
                            pendingBridgeNotifWebView = webView
                            val needsRationale = shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
                            if (needsRationale) {
                                uiState.confirmDialogConfig = com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig(
                                    title = getString(R.string.notification_permission_title),
                                    message = getString(R.string.notification_permission_message),
                                    positiveLabel = getString(R.string.action_allow),
                                    onPositive = { webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                                    negativeLabel = getString(R.string.action_deny),
                                    onNegative = {
                                        pendingBridgeNotifCallbackId = null
                                        pendingBridgeNotifOrigin = null
                                        pendingBridgeNotifWebView = null
                                        webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
                                    }
                                )
                            } else {
                                webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            showWebNotificationPermissionFromBridge(webView, safeId, rawOrigin)
                        }
                    }
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun postNotification(title: String, body: String, tag: String, origin: String) {
            val rawOrigin = origin.trim()
            val tab = tabManager.tabs.find { it.webView == webView }
            if (tab?.isIncognito == true) return
            val stored = com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.getState(
                this@MainActivity, rawOrigin,
                com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_NOTIFICATION
            )
            if (stored != com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW) return
            runOnUiThread { postWebNotification(title, body, tag, rawOrigin) }
        }
    }

    inner class ClipboardBridge(private val webView: android.webkit.WebView) {
        @android.webkit.JavascriptInterface
        fun getPermissionState(origin: String): String {
            val tab = tabManager.tabs.find { it.webView == webView }
            if (tab?.isIncognito == true) return "denied"
            val rawOrigin = origin.trim()
            return when (com.jhaiian.clint.settings.sitepermissions.SitePermissionManager.getState(
                this@MainActivity, rawOrigin,
                com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_CLIPBOARD
            )) {
                com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW -> "granted"
                com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.STATE_DENY -> "denied"
                else -> "default"
            }
        }

        @android.webkit.JavascriptInterface
        fun requestPermission(callbackId: String, origin: String) {
            runOnUiThread {
                val tab = tabManager.tabs.find { it.webView == webView }
                val safeId = callbackId.replace("'", "")
                if (tab?.isIncognito == true) {
                    webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
                    return@runOnUiThread
                }
                showWebClipboardPermissionFromBridge(webView, safeId, origin.trim())
            }
        }

        @android.webkit.JavascriptInterface
        fun readClipboardText(): String {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip == null || clip.itemCount == 0) return ""
            return clip.getItemAt(0).coerceToText(this@MainActivity)?.toString() ?: ""
        }

        @android.webkit.JavascriptInterface
        fun writeClipboardText(text: String) {
            runOnUiThread {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("", text))
            }
        }
    }

    inner class SelectPickerBridge(private val webView: android.webkit.WebView) {
        @android.webkit.JavascriptInterface
        fun isEnabled(): Boolean = prefs.getBoolean("custom_select_menus_enabled", true)

        @android.webkit.JavascriptInterface
        fun onSelectOpen(id: String, optionsJson: String, multiple: Boolean, title: String) {
            runOnUiThread {
                if (tabManager.activeTab?.webView !== webView) return@runOnUiThread
                val options = parseSelectPickerOptions(optionsJson)
                if (options.isEmpty()) return@runOnUiThread
                uiState.selectPickerRequest = com.jhaiian.clint.browser.dialogs.SelectPickerRequest(
                    id, title, options, multiple, java.lang.ref.WeakReference(webView)
                )
            }
        }
    }

    private fun parseSelectPickerOptions(json: String): List<com.jhaiian.clint.browser.dialogs.SelectPickerOption> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            com.jhaiian.clint.browser.dialogs.SelectPickerOption(
                value = o.optString("value"),
                label = o.optString("label"),
                selected = o.optBoolean("selected", false),
                disabled = o.optBoolean("disabled", false),
                group = if (o.isNull("group")) null else o.optString("group")
            )
        }
    } catch (e: org.json.JSONException) {
        emptyList()
    }

    inner class BlobDownloadBridge {
        @android.webkit.JavascriptInterface
        fun receiveBlob(base64: String, filename: String, mimeType: String) {
            runOnUiThread {
                showDownloadDialogForBlob(base64, filename, mimeType)
            }
        }

        @android.webkit.JavascriptInterface
        fun onError(error: String) {
        }
    }

    inner class UserScriptBridge(private val webView: android.webkit.WebView) {
        private val prefs = getSharedPreferences("user_script_gm_values", android.content.Context.MODE_PRIVATE)
        private val calls = java.util.concurrent.ConcurrentHashMap<String, okhttp3.Call>()

        @android.webkit.JavascriptInterface
        fun getValue(scriptKey: String, key: String): String? = prefs.getString("$scriptKey|$key", null)

        @android.webkit.JavascriptInterface
        fun setValue(scriptKey: String, key: String, jsonValue: String) {
            prefs.edit().putString("$scriptKey|$key", jsonValue).apply()
            runOnUiThread {
                val safeScript = scriptKey.replace("'", "")
                val safeKey = org.json.JSONObject.quote(key)
                val safeVal = org.json.JSONObject.quote(jsonValue)
                webView.evaluateJavascript(
                    "window.__usValueChanged&&window.__usValueChanged('$safeScript',$safeKey,$safeVal)", null
                )
            }
        }

        @android.webkit.JavascriptInterface
        fun deleteValue(scriptKey: String, key: String) {
            prefs.edit().remove("$scriptKey|$key").apply()
            runOnUiThread {
                val safeScript = scriptKey.replace("'", "")
                val safeKey = org.json.JSONObject.quote(key)
                webView.evaluateJavascript(
                    "window.__usValueChanged&&window.__usValueChanged('$safeScript',$safeKey,null)", null
                )
            }
        }

        @android.webkit.JavascriptInterface
        fun listValues(scriptKey: String): String {
            val prefix = "$scriptKey|"
            val out = org.json.JSONArray()
            for (k in prefs.all.keys) {
                if (k.startsWith(prefix)) out.put(k.removePrefix(prefix))
            }
            return out.toString()
        }

        @android.webkit.JavascriptInterface
        fun notify(scriptName: String, title: String, text: String, origin: String) {
            runOnUiThread {
                val displayTitle = title.ifBlank { scriptName }
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                    isSystemPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    postWebNotification(displayTitle, text, "", origin)
                } else {
                    pendingUserScriptNotify = Triple(displayTitle, text, origin)
                    userScriptNotifyPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun download(base64: String, filename: String, mimeType: String) {
            runOnUiThread { showDownloadDialogForBlob(base64, filename, mimeType) }
        }

        @android.webkit.JavascriptInterface
        fun abort(id: String) {
            calls.remove(id)?.cancel()
        }

        @android.webkit.JavascriptInterface
        fun xhr(id: String, detailsJson: String) {
            val safeId = id.replace("'", "")
            try {
                val details = org.json.JSONObject(detailsJson)
                val url = details.getString("url")
                val method = details.optString("method", "GET").ifBlank { "GET" }
                val hasBody = details.has("data") && !details.isNull("data")
                val mediaTypeHeader = details.optJSONObject("headers")?.optString("Content-Type")
                    ?: details.optJSONObject("headers")?.optString("content-type")
                val requestBody = if (hasBody) {
                    details.optString("data", "").toRequestBody(
                        (mediaTypeHeader ?: "text/plain;charset=UTF-8").toMediaTypeOrNull()
                    )
                } else if (method != "GET" && method != "HEAD") {
                    ByteArray(0).toRequestBody(null)
                } else null
                val builder = okhttp3.Request.Builder().url(url).method(method, requestBody)
                val cookie = runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull()
                if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)
                if (details.has("headers")) {
                    details.optJSONObject("headers")?.let { headers ->
                        headers.keys().forEach { k -> builder.header(k, headers.optString(k)) }
                    }
                }
                if (!details.has("headers") || details.optJSONObject("headers")?.has("User-Agent") != true) {
                    builder.header("User-Agent", android.webkit.WebSettings.getDefaultUserAgent(this@MainActivity))
                }
                val call = com.jhaiian.clint.downloads.ClintDownloadManager.httpClient.newCall(builder.build())
                calls[safeId] = call
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        calls.remove(safeId)
                        val err = org.json.JSONObject().put("error", e.message ?: "network error")
                        webView.post { webView.evaluateJavascript("window.__usXhrCallback&&window.__usXhrCallback('$safeId',null,${err})", null) }
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        calls.remove(safeId)
                        response.use { r ->
                            val text = runCatching { r.body.string() }.getOrDefault("")
                            val headerText = StringBuilder()
                            for (i in 0 until r.headers.size) {
                                headerText.append(r.headers.name(i)).append(": ").append(r.headers.value(i)).append("\r\n")
                            }
                            val result = org.json.JSONObject()
                                .put("status", r.code)
                                .put("statusText", r.message)
                                .put("responseText", text)
                                .put("responseHeaders", headerText.toString())
                                .put("finalUrl", r.request.url.toString())
                            webView.post { webView.evaluateJavascript("window.__usXhrCallback&&window.__usXhrCallback('$safeId',${result},null)", null) }
                        }
                    }
                })
            } catch (e: Exception) {
                val err = org.json.JSONObject().put("error", e.message ?: "request error")
                webView.post { webView.evaluateJavascript("window.__usXhrCallback&&window.__usXhrCallback('$safeId',null,${err})", null) }
            }
        }
    }

    internal fun isNetworkMetered(): Boolean {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered
    }

    private fun migrateScrollHideMode() {
        when (prefs.getString("scroll_hide_mode", "off")) {
            "top_only" -> prefs.edit().putString("scroll_hide_mode", "search_bar").apply()
            "bottom_only" -> {
                val position = prefs.getString("address_bar_position", "top") ?: "top"
                val newValue = if (position == "bottom") "search_bar" else "navigation_bar"
                prefs.edit().putString("scroll_hide_mode", newValue).apply()
            }
        }
    }

    fun onImageOpenInNewTab(imageUrl: String) { handleImageOpenInNewTab(imageUrl) }
    fun onImageOpenIncognito(imageUrl: String) { openNewTab(isIncognito = true, url = imageUrl) }
    fun onImageOpenInCurrentTab(imageUrl: String) { dismissContentPreview(); loadUrl(imageUrl) }
    fun onImagePreview(imageUrl: String) { handleImagePreview(imageUrl) }
    fun onImageCopy(imageUrl: String) { handleImageCopy(imageUrl) }
    fun onImageDownload(imageUrl: String, altText: String) { handleImageDownload(imageUrl, altText) }
    fun onImageShare(imageUrl: String) { handleImageShare(imageUrl) }

    fun onLinkOpenInNewTab(url: String) { handleLinkOpenInNewTab(url) }
    fun onLinkOpenIncognito(url: String) { handleLinkOpenIncognito(url) }
    fun onLinkOpenNewTabBackground(url: String) { handleLinkOpenNewTabBackground(url) }
    fun onLinkPreviewPage(url: String) { handleLinkPreviewPage(url) }
    fun onLinkCopyAddress(url: String) { handleLinkCopyAddress(url) }
    fun onLinkCopyText(url: String, text: String) { handleLinkCopyText(text) }
    fun onLinkShare(url: String) { handleLinkShare(url) }

    fun onPreviewOpenInNewTab(url: String) { openNewTab(isIncognito = false, url = url) }

    fun onPreviewLinkOpenInNewTab(url: String) { dismissContentPreview(); handleLinkOpenInNewTab(url) }
    fun onPreviewLinkOpenIncognito(url: String) { dismissContentPreview(); handleLinkOpenIncognito(url) }
    fun onPreviewLinkOpenInCurrentTab(url: String) { dismissContentPreview(); loadUrl(url) }
    fun onPreviewLinkCopyAddress(url: String) { handleLinkCopyAddress(url) }
    fun onPreviewLinkCopyText(url: String, text: String) { handleLinkCopyText(text) }
    fun onPreviewLinkShare(url: String) { handleLinkShare(url) }

    private fun dismissContentPreview() {
        uiState.contentPreviewRequest = null
    }

    private fun getUrlFromIntent(intent: android.content.Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            android.content.Intent.ACTION_SEND -> intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            else -> intent.data?.toString()
        }
    }
}

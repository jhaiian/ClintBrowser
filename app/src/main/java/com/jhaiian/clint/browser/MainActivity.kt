package com.jhaiian.clint.browser
import com.jhaiian.clint.browser.delegates.*
import com.jhaiian.clint.browser.menu.*
import com.jhaiian.clint.browser.sheets.*
import com.jhaiian.clint.browser.suggestions.*
import com.jhaiian.clint.browser.webview.*

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.abs
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.R
import com.jhaiian.clint.BuildConfig
import com.jhaiian.clint.crash.CrashHandler
import com.jhaiian.clint.databinding.ActivityMainBinding
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.tabs.TabManager
import com.jhaiian.clint.tabs.TabSwitcherSheet
import com.jhaiian.clint.update.UpdateChecker
import com.jhaiian.clint.ui.ClintToast
import androidx.webkit.ScriptHandler

class MainActivity : ClintActivity(), TabSwitcherSheet.Listener, MenuBottomSheet.Listener, ImageLongPressSheet.Listener, LinkLongPressSheet.Listener, ContentPreviewSheet.Listener, PreviewLinkLongPressSheet.Listener {

    companion object {
        const val EXTRA_REFRESH_LINK_MODE = "extra_refresh_link_mode"
        const val EXTRA_REFRESH_LINK_DOWNLOAD_ID = "extra_refresh_link_download_id"
        const val EXTRA_REFRESH_LINK_FILENAME = "extra_refresh_link_filename"
        const val EXTRA_REFRESH_LINK_ORIGINAL_URL = "extra_refresh_link_original_url"
        const val EXTRA_REFRESH_LINK_ORIGINAL_REFERER = "extra_refresh_link_original_referer"
    }

    data class RefreshLinkSession(
        val downloadId: Int,
        val filename: String,
        val originalUrl: String,
        val originalReferer: String,
        val previousTabIndex: Int
    )

    internal var refreshLinkSession: RefreshLinkSession? = null

    internal lateinit var binding: ActivityMainBinding
    internal lateinit var prefs: SharedPreferences
    internal val tabManager = TabManager()
    internal var isDesktopMode = false
    internal var desktopModeHost: String? = null
    internal var autoDesktopPendingReload: String? = null
    internal val desktopScriptHandlers = mutableMapOf<String, ScriptHandler>()
    internal val autoplayScriptHandlers = mutableMapOf<String, ScriptHandler>()
    internal val quiverGuardScriptHandlers = com.jhaiian.clint.quiver.engine.ScriptHandlerStore()
    internal val quiverGuardJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    internal var topBarFullHeight = 0
    internal var bottomBarFullHeight = 0
    internal var statusBarInsetPx = 0
    internal var cachedStatusBarInsetPx = 0
    internal var cachedNavBarInsetPx = 0
    internal var topBarFraction: Float = 0f
    internal var bottomBarAnimator2: ValueAnimator? = null
    internal var bottomBarFraction: Float = 0f
    internal var hasWebBottomNav: Boolean = false
    internal var nestedScrollActive = false
    internal var canvasTouchActive = false
    internal var swipeGuardBlocked = false
    private var swipeGuardInitX = 0f
    private var swipeGuardInitY = 0f

    internal var fullscreenView: View? = null
    internal var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    internal var suggestionFetcherTop: SuggestionFetcher? = null
    internal var suggestionFetcherBottom: SuggestionFetcher? = null
    internal var suggestionsBgThread: android.os.HandlerThread? = null

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
    internal var pendingVoiceSearchEditText: android.widget.EditText? = null
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
        else pendingVoiceSearchEditText = null
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

    internal val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull() ?: return@registerForActivityResult
            pendingVoiceSearchEditText?.let { editText ->
                editText.setText(text)
                editText.setSelection(text.length)
            }
        }
        pendingVoiceSearchEditText = null
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

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "javascript_enabled" -> applyJavaScript()
            "block_third_party_cookies" -> applyCookiePolicy()
            "custom_user_agent" -> applyUserAgent()
            "quiver_guard_enabled" -> onQuiverGuardEnabled(prefs.getBoolean("quiver_guard_enabled", false))
            "data_saver_enabled", "data_saver_disable_images", "data_saver_disable_autoplay" -> applyDataSaverSettings()
            "force_dark_web" -> {
                tabManager.tabs.forEach { applyWebDarkMode(it.webView) }
                tabManager.activeTab?.webView?.reload()
            }
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
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        applyStatusBarVisibility()
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            if (statusBars.top > 0) cachedStatusBarInsetPx = statusBars.top
            val effectivePadding = if (prefs.getBoolean("hide_status_bar", false)) 0 else statusBars.top
            statusBarInsetPx = effectivePadding
            v.setPadding(0, effectivePadding, 0, 0)
            val sbLp = binding.statusBarBackground.layoutParams
            sbLp.height = effectivePadding
            binding.statusBarBackground.layoutParams = sbLp
            v.post {
                if (topBarFullHeight == 0 && v.height > 0) {
                    topBarFullHeight = v.height
                    binding.swipeRefresh.setProgressViewOffset(false, v.height + 4, v.height + 72)
                    updateMainContentInsets()
                }
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            cachedNavBarInsetPx = navBars.bottom
            v.setPadding(0, 0, 0, navBars.bottom)
            v.post {
                if (v.height > 0 && bottomBarFullHeight != v.height) {
                    bottomBarFullHeight = v.height
                    setBottomBarFraction(bottomBarFraction)
                }
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarBottom) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            cachedNavBarInsetPx = navBars.bottom
            val bottomPad = if (ime.bottom > navBars.bottom) ime.bottom else navBars.bottom
            v.setPadding(0, 0, 0, bottomPad)
            v.post {
                if (v.visibility == android.view.View.VISIBLE && v.height > 0 && bottomBarFullHeight != v.height) {
                    bottomBarFullHeight = v.height
                    updateMainContentInsets()
                }
            }
            insets
        }
        ClintDownloadManager.createNotificationChannel(this)
        ClintDownloadManager.init(this)
        initializeQuiverGuardEngine()
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
        setupNavigationButtons()
        applyAddressBarPosition()
        val isRefreshLinkMode = intent.getBooleanExtra(EXTRA_REFRESH_LINK_MODE, false)
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
        val url = getUrlFromIntent(intent)
        setIntent(android.content.Intent())
        if (!url.isNullOrEmpty()) {
            openNewTab(isIncognito = false, url = url)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        bottomBarAnimator2?.cancel()
        bottomBarFraction = 0f
        topBarFraction = 0f
        nestedScrollActive = false
        canvasTouchActive = false
        hasWebBottomNav = false
        binding.bottomBar.translationY = 0f
        binding.toolbarTop.translationY = 0f
        binding.toolbarBottom.translationY = 0f
        binding.mainContent.setPadding(0, 0, 0, 0)
        binding.swipeRefresh.isEnabled = true
        topBarFullHeight = 0
        bottomBarFullHeight = 0
        ViewCompat.requestApplyInsets(binding.toolbarTop)
        ViewCompat.requestApplyInsets(binding.bottomBar)
        ViewCompat.requestApplyInsets(binding.toolbarBottom)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBarVisibility()
    }

    override fun onStop() {
        super.onStop()
        saveTabs()
        if (refreshLinkSession != null) {
            cleanupRefreshLinkTabs()
            refreshLinkSession = null
        }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        if (refreshLinkSession != null) {
            cleanupRefreshLinkTabs()
            refreshLinkSession = null
        }
        tabManager.destroyAll()
        suggestionFetcherTop?.cancel()
        suggestionFetcherBottom?.cancel()
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
                    binding.swipeRefresh.isEnabled = false
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
                if (fullscreenView != null) {
                    exitFullscreen()
                    return
                }
                if (binding.addressBarSearch.isShowing) {
                    binding.addressBarSearch.hide()
                    return
                }
                if (binding.addressBarSearchBottom.isShowing) {
                    binding.addressBarSearchBottom.hide()
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
        ClintToast.show(this, getString(R.string.exit_tap_again), R.drawable.ic_arrow_back_24)
        backPressHandler.postDelayed({ backPressedOnce = false }, 2000L)
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.exit_dialog_title))
            .setMessage(getString(R.string.exit_dialog_message))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.exit_dialog_confirm)) { _, _ -> finish() }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    override fun onTabSelected(index: Int) { tabManager.switchTo(index); attachActiveWebView() }
    override fun onTabClosed(index: Int) {
        val tab = tabManager.tabs.getOrNull(index)
        tab?.let {
            removeDesktopScript(it)
            onQuiverGuardTabClosed(it)
            if (!it.isIncognito) com.jhaiian.clint.ui.FaviconCache.evict(this, it.url)
        }
        val wasActive = index == tabManager.activeIndex
        tabManager.closeTab(index)
        resetProgressBar()
        if (tabManager.count == 0) openNewTab(false)
        else if (wasActive) attachActiveWebView()
        else updateTabCount()
    }
    override fun onNewTab() { openNewTab(false) }
    override fun onNewIncognitoTab() { openNewTab(true) }

    override fun onMenuGoBack() { tabManager.activeTab?.webView?.let { if (it.canGoBack()) it.goBack() } }
    override fun onMenuGoForward() { tabManager.activeTab?.webView?.let { if (it.canGoForward()) it.goForward() } }
    override fun onMenuHome() { loadUrl(getSearchEngineHomeUrl()) }
    override fun onMenuRefreshOrStop() {
        tabManager.activeTab?.webView?.let { wv ->
            val loading = binding.progressBar.visibility == View.VISIBLE ||
                binding.progressBarBottom.visibility == View.VISIBLE
            if (loading) { wv.stopLoading(); onPageFinished(wv.url ?: "") } else wv.reload()
        }
    }
    override fun onMenuToggleBookmark() {
        val url = tabManager.activeTab?.webView?.url ?: return
        val title = tabManager.activeTab?.title ?: url
        if (com.jhaiian.clint.bookmarks.BookmarkManager.isBookmarked(this, url)) {
            com.jhaiian.clint.bookmarks.BookmarkManager.remove(this, url)
        } else {
            com.jhaiian.clint.bookmarks.BookmarkManager.add(this, com.jhaiian.clint.bookmarks.Bookmark(url = url, title = title))
        }
        updateBookmarkIcon()
    }
    override fun onMenuNewTab() { openNewTab(false) }
    override fun onMenuIncognito() { openNewTab(true) }
    override fun onMenuShare() {
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, tabManager.activeTab?.webView?.url)
        }
        startActivity(android.content.Intent.createChooser(i, getString(R.string.share_url)))
    }
    override fun onMenuOpenInApp() {
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
    override fun onMenuDownloads() { startActivity(android.content.Intent(this, com.jhaiian.clint.downloads.DownloadsActivity::class.java)) }
    override fun onMenuBookmarks() { startActivity(android.content.Intent(this, com.jhaiian.clint.bookmarks.BookmarksActivity::class.java)) }
    override fun onMenuHistory() { startActivity(android.content.Intent(this, com.jhaiian.clint.history.HistoryActivity::class.java)) }
    override fun onMenuDesktopMode() {
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
    override fun onMenuSettings() { startActivity(android.content.Intent(this, com.jhaiian.clint.settings.SettingsActivity::class.java)) }
    override fun onMenuDataSaver() {
        val enabled = !prefs.getBoolean("data_saver_enabled", false)
        prefs.edit().putBoolean("data_saver_enabled", enabled).apply()
    }
    override fun onMenuOpenDataSaverSettings() {
        startActivity(android.content.Intent(this, com.jhaiian.clint.settings.SettingsActivity::class.java)
            .putExtra(com.jhaiian.clint.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "data_saver"))
    }
    override fun onMenuOpenDownloadSettings() {
        startActivity(android.content.Intent(this, com.jhaiian.clint.settings.SettingsActivity::class.java)
            .putExtra(com.jhaiian.clint.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "download_settings"))
    }
    override fun onMenuQuiverGuard() {
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
    override fun onMenuOpenQuiverGuardSettings() {
        startActivity(android.content.Intent(this, com.jhaiian.clint.quiver.QuiverGuardActivity::class.java))
    }
    override fun onMenuDisableQuiverGuardForSite() {
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
    override fun onMenuReaderMode() {
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
            val theme = prefs.getString("app_theme", "dark") ?: "dark"
            val isDark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
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
                val sheet = ContentPreviewSheet.newInstanceForReaderMode(pageUrl, title, html)
                sheet.show(supportFragmentManager, "reader_mode_preview")
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
                                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity, getDialogTheme())
                                    .setTitle(getString(R.string.notification_permission_title))
                                    .setMessage(getString(R.string.notification_permission_message))
                                    .setNegativeButton(getString(R.string.action_deny)) { _, _ ->
                                        pendingBridgeNotifCallbackId = null
                                        pendingBridgeNotifOrigin = null
                                        pendingBridgeNotifWebView = null
                                        webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
                                    }
                                    .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
                                        webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    .create().also { applyStatusBarFlagToDialog(it) }.show()
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

    override fun onImageOpenInNewTab(imageUrl: String) { handleImageOpenInNewTab(imageUrl) }
    override fun onImageOpenIncognito(imageUrl: String) { openNewTab(isIncognito = true, url = imageUrl) }
    override fun onImageOpenInCurrentTab(imageUrl: String) { dismissContentPreview(); loadUrl(imageUrl) }
    override fun onImagePreview(imageUrl: String) { handleImagePreview(imageUrl) }
    override fun onImageCopy(imageUrl: String) { handleImageCopy(imageUrl) }
    override fun onImageDownload(imageUrl: String, altText: String) { handleImageDownload(imageUrl, altText) }
    override fun onImageShare(imageUrl: String) { handleImageShare(imageUrl) }

    override fun onLinkOpenInNewTab(url: String) { handleLinkOpenInNewTab(url) }
    override fun onLinkOpenIncognito(url: String) { handleLinkOpenIncognito(url) }
    override fun onLinkPreviewPage(url: String) { handleLinkPreviewPage(url) }
    override fun onLinkCopyAddress(url: String) { handleLinkCopyAddress(url) }
    override fun onLinkCopyText(url: String, text: String) { handleLinkCopyText(text) }
    override fun onLinkShare(url: String) { handleLinkShare(url) }

    override fun onPreviewOpenInNewTab(url: String) { openNewTab(isIncognito = false, url = url) }

    override fun onPreviewLinkOpenInNewTab(url: String) { dismissContentPreview(); handleLinkOpenInNewTab(url) }
    override fun onPreviewLinkOpenIncognito(url: String) { dismissContentPreview(); handleLinkOpenIncognito(url) }
    override fun onPreviewLinkOpenInCurrentTab(url: String) { dismissContentPreview(); loadUrl(url) }
    override fun onPreviewLinkCopyAddress(url: String) { handleLinkCopyAddress(url) }
    override fun onPreviewLinkCopyText(url: String, text: String) { handleLinkCopyText(text) }
    override fun onPreviewLinkShare(url: String) { handleLinkShare(url) }

    private fun dismissContentPreview() {
        (supportFragmentManager.findFragmentByTag("link_preview") as? ContentPreviewSheet)?.dismiss()
        (supportFragmentManager.findFragmentByTag("image_preview") as? ContentPreviewSheet)?.dismiss()
    }

    private fun getUrlFromIntent(intent: android.content.Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            android.content.Intent.ACTION_SEND -> intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            else -> intent.data?.toString()
        }
    }

    private fun applyStatusBarVisibility() {
        val hide = prefs.getBoolean("hide_status_bar", false)
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            statusBarInsetPx = 0
            binding.toolbarTop.setPadding(0, 0, 0, 0)
            val sbLp = binding.statusBarBackground.layoutParams
            sbLp.height = 0
            binding.statusBarBackground.layoutParams = sbLp
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
            if (cachedStatusBarInsetPx > 0) {
                statusBarInsetPx = cachedStatusBarInsetPx
                binding.toolbarTop.setPadding(0, cachedStatusBarInsetPx, 0, 0)
                val sbLp = binding.statusBarBackground.layoutParams
                sbLp.height = cachedStatusBarInsetPx
                binding.statusBarBackground.layoutParams = sbLp
            }
        }
    }
}

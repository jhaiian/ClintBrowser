package com.jhaiian.clint.browser

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.R
import com.jhaiian.clint.crash.CrashHandler
import com.jhaiian.clint.databinding.ActivityMainBinding
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.network.DohManager
import com.jhaiian.clint.tabs.TabManager
import com.jhaiian.clint.tabs.TabSwitcherSheet
import com.jhaiian.clint.update.UpdateChecker
import com.jhaiian.clint.ui.ClintToast
import androidx.webkit.ScriptHandler

class MainActivity : ClintActivity(), TabSwitcherSheet.Listener, MenuBottomSheet.Listener {

    internal lateinit var binding: ActivityMainBinding
    internal lateinit var prefs: SharedPreferences
    internal val tabManager = TabManager()
    internal var isDesktopMode = false
    internal val desktopScriptHandlers = mutableMapOf<String, ScriptHandler>()

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

    internal var fullscreenView: View? = null
    internal var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private var backPressedOnce = false
    private val backPressHandler = Handler(Looper.getMainLooper())

    internal var pendingFileChooserCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
    internal var pendingFileChooserParams: android.webkit.WebChromeClient.FileChooserParams? = null
    internal var filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
    internal var cameraImageUri: android.net.Uri? = null
    internal var cameraVideoUri: android.net.Uri? = null

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

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "javascript_enabled" -> applyJavaScript()
            "block_third_party_cookies" -> applyCookiePolicy()
            "custom_user_agent" -> applyUserAgent()
            "block_trackers" -> reattachWebClients()
            "doh_mode", "doh_provider" -> { DohManager.invalidate(); reattachWebClients() }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (prefs.getBoolean("check_update_on_launch", true)) {
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
        val intentUrl = intent?.data?.toString()
        if (!intentUrl.isNullOrEmpty()) {
            restoreTabs()
            openNewTab(isIncognito = false, url = intentUrl)
        } else if (!restoreTabs()) {
            openNewTab(isIncognito = false, url = getSearchEngineHomeUrl())
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val url = intent.data?.toString()
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
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        tabManager.destroyAll()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (fullscreenView != null) { exitFullscreen(); return true }
            val wv = tabManager.activeTab?.webView
            if (wv?.canGoBack() == true) { wv.goBack(); return true }
            handleExitConfirmation()
            return true
        }
        return super.onKeyDown(keyCode, event)
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
            if (!it.isIncognito) com.jhaiian.clint.ui.FaviconCache.evict(this, it.url)
        }
        val wasActive = index == tabManager.activeIndex
        tabManager.closeTab(index)
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
        val webClient = tabManager.activeTab?.webView?.webViewClient as? com.jhaiian.clint.webview.ClintWebViewClient ?: return
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
    override fun onMenuDesktopMode() {
        isDesktopMode = !isDesktopMode
        tabManager.tabs.forEach { tab ->
            tab.webView.settings.userAgentString = buildUserAgent()
            applyUserAgentMetadata(tab.webView)
            if (isDesktopMode) addDesktopScript(tab) else removeDesktopScript(tab)
        }
        val wv = tabManager.activeTab?.webView
        val currentWebUrl = wv?.url
        if (wv != null && !currentWebUrl.isNullOrEmpty()) {
            val headers = buildDesktopHeaders()
            if (headers != null) wv.loadUrl(currentWebUrl, headers) else wv.reload()
        } else wv?.reload()
    }
    override fun onMenuSettings() { startActivity(android.content.Intent(this, com.jhaiian.clint.settings.SettingsActivity::class.java)) }

    inner class NestedScrollBridge {
        @android.webkit.JavascriptInterface
        fun onNestedScroll(active: Boolean) {
            runOnUiThread { nestedScrollActive = active }
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

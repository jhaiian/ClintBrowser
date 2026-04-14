package com.jhaiian.clint.browser

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.R
import com.jhaiian.clint.crash.CrashHandler
import com.jhaiian.clint.databinding.ActivityMainBinding
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.network.DohManager
import com.jhaiian.clint.tabs.TabManager
import com.jhaiian.clint.tabs.TabSwitcherSheet
import com.jhaiian.clint.update.UpdateChecker
import androidx.webkit.ScriptHandler

class MainActivity : ClintActivity(), TabSwitcherSheet.Listener {

    internal lateinit var binding: ActivityMainBinding
    internal lateinit var prefs: SharedPreferences
    internal val tabManager = TabManager()
    internal var isDesktopMode = false
    internal val desktopScriptHandlers = mutableMapOf<String, ScriptHandler>()

    internal var topBarFullHeight = 0
    internal var bottomBarFullHeight = 0
    internal var statusBarInsetPx = 0
    internal var cachedStatusBarInsetPx = 0
    internal var barsHidden = false
    internal var barAnimator: ValueAnimator? = null
    internal var nestedScrollActive = false

    internal var fullscreenView: View? = null
    internal var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

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
                if (!prefs.getBoolean("hide_bars_on_scroll", true)) animateBars(hide = false, animated = false)
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
            v.post {
                if (topBarFullHeight == 0 && v.height > 0) {
                    topBarFullHeight = v.height
                    binding.swipeRefresh.setProgressViewOffset(false, v.height + 4, v.height + 72)
                }
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, navBars.bottom)
            v.post {
                if (bottomBarFullHeight == 0 && v.height > 0) bottomBarFullHeight = v.height
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
        setupSwipeRefresh()
        setupAddressBar()
        setupNavigationButtons()
        val intentUrl = intent?.data?.toString()
        if (!intentUrl.isNullOrEmpty()) {
            openNewTab(isIncognito = false, url = intentUrl)
        } else if (!restoreTabs()) {
            openNewTab(isIncognito = false, url = getSearchEngineHomeUrl())
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        if (barsHidden) {
            animateBars(hide = false, animated = false)
            nestedScrollActive = false
        }
        topBarFullHeight = 0
        bottomBarFullHeight = 0
        ViewCompat.requestApplyInsets(binding.toolbarTop)
        ViewCompat.requestApplyInsets(binding.bottomBar)
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
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTabSelected(index: Int) { tabManager.switchTo(index); attachActiveWebView() }
    override fun onTabClosed(index: Int) {
        val tab = tabManager.tabs.getOrNull(index)
        tab?.let { removeDesktopScript(it) }
        val wasActive = index == tabManager.activeIndex
        tabManager.closeTab(index)
        if (tabManager.count == 0) openNewTab(false)
        else if (wasActive) attachActiveWebView()
        else updateTabCount()
    }
    override fun onNewTab() { openNewTab(false) }
    override fun onNewIncognitoTab() { openNewTab(true) }

    inner class NestedScrollBridge {
        @android.webkit.JavascriptInterface
        fun onNestedScroll(active: Boolean) {
            runOnUiThread { nestedScrollActive = active }
        }
    }

    internal fun isNetworkMetered(): Boolean {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered
    }

    private fun applyStatusBarVisibility() {
        val hide = prefs.getBoolean("hide_status_bar", false)
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            statusBarInsetPx = 0
            binding.toolbarTop.setPadding(0, 0, 0, 0)
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
            if (cachedStatusBarInsetPx > 0) {
                statusBarInsetPx = cachedStatusBarInsetPx
                binding.toolbarTop.setPadding(0, cachedStatusBarInsetPx, 0, 0)
            }
        }
    }
}

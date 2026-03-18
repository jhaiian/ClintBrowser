package com.jhaiian.clint

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

import com.jhaiian.clint.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), TabSwitcherSheet.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private val tabManager = TabManager()
    private var isDesktopMode = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "javascript_enabled" -> applyJavaScript()
            "block_third_party_cookies" -> applyCookiePolicy()
            "custom_user_agent" -> applyUserAgent()
            "block_trackers" -> reattachWebClients()
            "doh_mode", "doh_provider" -> { DohManager.invalidate(); reattachWebClients() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
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

    override fun onStop() {
        super.onStop()
        saveTabs()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        tabManager.destroyAll()
        super.onDestroy()
    }

    private fun saveTabs() {
        val urls = tabManager.tabs
            .filter { !it.isIncognito }
            .mapNotNull { tab ->
                val url = tab.webView.url ?: tab.url
                url.takeIf { it.isNotEmpty() && it != "about:blank" }
            }
        val activeNonIncognito = tabManager.tabs
            .filter { !it.isIncognito }
            .indexOf(tabManager.activeTab)
            .coerceAtLeast(0)
        prefs.edit()
            .putString("saved_tab_urls", urls.joinToString("\n"))
            .putInt("saved_tab_active", activeNonIncognito)
            .apply()
    }

    private fun restoreTabs(): Boolean {
        val savedUrls = prefs.getString("saved_tab_urls", null)
            ?.split("\n")
            ?.filter { it.isNotEmpty() }
            ?: return false
        if (savedUrls.isEmpty()) return false
        val activeIdx = prefs.getInt("saved_tab_active", 0).coerceIn(0, savedUrls.lastIndex)
        savedUrls.forEach { url -> openNewTabSilent(url) }
        tabManager.switchTo(activeIdx)
        attachActiveWebView()
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openNewTabSilent(url: String) {
        val webView = createWebView(false)
        val tab = BrowserTab(url = url, webView = webView)
        tabManager.add(tab)
        webView.webViewClient = ClintWebViewClient(prefs) { tabManager.activeTab?.id == tab.id }
        webView.webChromeClient = ClintWebChromeClient(
            isActive = { tabManager.activeTab?.id == tab.id },
            onTitleChanged = { title ->
                tab.title = title
                if (tabManager.activeTab?.id == tab.id) updateTabCount()
            }
        )
        webView.loadUrl(url)
    }

    private var nestedScrollActive = false

    private val scrollTrackJs = """
        (function() {
            if (window.__clintTracked) return;
            window.__clintTracked = true;
            window.__clintNestedScrolled = false;
            document.addEventListener('scroll', function(e) {
                var t = e.target;
                var isRoot = !t || t === document || t === document.documentElement || t === document.body;
                if (!isRoot) {
                    window.__clintNestedScrolled = (t.scrollTop > 0 || t.scrollLeft > 0);
                } else {
                    window.__clintNestedScrolled = false;
                }
            }, true);
        })();
    """.trimIndent()

    private fun injectScrollTracker(webView: android.webkit.WebView) {
        webView.evaluateJavascript(scrollTrackJs, null)
    }

    private fun queryNestedScroll(webView: android.webkit.WebView) {
        webView.evaluateJavascript(
            "(typeof window.__clintNestedScrolled !== 'undefined' && window.__clintNestedScrolled).toString()"
        ) { result ->
            nestedScrollActive = result?.trim('"') == "true"
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.canChildScrollUpCallback = {
            val wv = tabManager.activeTab?.webView
            wv != null && (wv.canScrollVertically(-1) || nestedScrollActive)
        }
        binding.swipeRefresh.apply {
            setColorSchemeColors(
                ContextCompat.getColor(this@MainActivity, R.color.purple_300),
                ContextCompat.getColor(this@MainActivity, R.color.purple_200)
            )
            setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(this@MainActivity, R.color.toolbar_color)
            )
            setOnRefreshListener {
                nestedScrollActive = false
                tabManager.activeTab?.webView?.reload() ?: run { isRefreshing = false }
            }
        }
    }

    private fun applyJavaScript() {
        val enabled = prefs.getBoolean("javascript_enabled", true)
        tabManager.tabs.forEach { it.webView.settings.javaScriptEnabled = enabled }
        tabManager.activeTab?.webView?.reload()
    }

    private fun applyCookiePolicy() {
        val blockThirdParty = prefs.getBoolean("block_third_party_cookies", true)
        val cookieManager = CookieManager.getInstance()
        tabManager.tabs.forEach { tab ->
            if (!tab.isIncognito) cookieManager.setAcceptThirdPartyCookies(tab.webView, !blockThirdParty)
        }
        tabManager.activeTab?.webView?.reload()
    }

    private fun applyUserAgent() {
        val ua = buildUserAgent()
        tabManager.tabs.forEach { it.webView.settings.userAgentString = ua }
        tabManager.activeTab?.webView?.reload()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun reattachWebClients() {
        tabManager.tabs.forEach { tab ->
            tab.webView.webViewClient = ClintWebViewClient(prefs) { tabManager.activeTab?.id == tab.id }
        }
    }

    private fun getSearchEngineHomeUrl(): String {
        return when (prefs.getString("search_engine", "duckduckgo")) {
            "brave" -> "https://search.brave.com"
            "google" -> "https://www.google.com"
            else -> "https://duckduckgo.com"
        }
    }

    private fun getSearchQueryUrl(query: String): String {
        val encoded = Uri.encode(query)
        return when (prefs.getString("search_engine", "duckduckgo")) {
            "brave" -> "https://search.brave.com/search?q=$encoded"
            "google" -> "https://www.google.com/search?q=$encoded"
            else -> "https://duckduckgo.com/?q=$encoded"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(isIncognito: Boolean): WebView {
        val webView = WebView(this)
        val settings = webView.settings
        settings.javaScriptEnabled = prefs.getBoolean("javascript_enabled", true)
        settings.domStorageEnabled = !isIncognito
        settings.cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.safeBrowsingEnabled = false
        settings.userAgentString = buildUserAgent()
        val cookieManager = CookieManager.getInstance()
        if (isIncognito) {
            cookieManager.setAcceptCookie(false)
        } else {
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, !prefs.getBoolean("block_third_party_cookies", true))
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimetype)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription(getString(R.string.downloading))
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(applicationContext, R.string.download_started, Toast.LENGTH_LONG).show()
        }
        return webView
    }

    private fun buildUserAgent(): String {
        return when {
            isDesktopMode ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            prefs.getBoolean("custom_user_agent", true) ->
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            else ->
                WebSettings.getDefaultUserAgent(this)
        }
    }

    private fun openNewTab(isIncognito: Boolean, url: String = getSearchEngineHomeUrl()) {
        val webView = createWebView(isIncognito)
        val tab = BrowserTab(isIncognito = isIncognito, webView = webView)
        val index = tabManager.add(tab)
        webView.webViewClient = ClintWebViewClient(prefs) { tabManager.activeTab?.id == tab.id }
        webView.webChromeClient = ClintWebChromeClient(
            isActive = { tabManager.activeTab?.id == tab.id },
            onTitleChanged = { title ->
                tab.title = title
                if (tabManager.activeTab?.id == tab.id) updateTabCount()
            }
        )
        tabManager.switchTo(index)
        attachActiveWebView()
        loadUrl(url)
    }

    private fun attachActiveWebView() {
        val tab = tabManager.activeTab ?: return
        binding.webContainer.removeAllViews()
        (tab.webView.parent as? android.view.ViewGroup)?.removeView(tab.webView)
        binding.webContainer.addView(tab.webView, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        updateIncognitoState(tab.isIncognito)
        updateSwipeRefreshColors(tab.isIncognito)
        updateTabCount()
        updateAddressBar(tab.webView.url ?: "")
        updateNavigationState()
        val cookieManager = CookieManager.getInstance()
        if (tab.isIncognito) {
            cookieManager.setAcceptCookie(false)
        } else {
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(tab.webView, !prefs.getBoolean("block_third_party_cookies", true))
        }
        nestedScrollActive = false
        tab.webView.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                queryNestedScroll(tab.webView)
            }
            false
        }
    }

    private fun updateIncognitoState(isIncognito: Boolean) {
        binding.incognitoIcon.visibility = if (isIncognito) View.VISIBLE else View.GONE
        val color = ContextCompat.getColor(this, if (isIncognito) R.color.incognito_toolbar_color else R.color.toolbar_color)
        binding.toolbarTop.setBackgroundColor(color)
        binding.bottomBar.setBackgroundColor(color)
    }

    private fun updateSwipeRefreshColors(isIncognito: Boolean) {
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, if (isIncognito) R.color.incognito_toolbar_color else R.color.toolbar_color)
        )
        if (isIncognito) {
            binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.incognito_accent))
        } else {
            binding.swipeRefresh.setColorSchemeColors(
                ContextCompat.getColor(this, R.color.purple_300),
                ContextCompat.getColor(this, R.color.purple_200)
            )
        }
    }

    private fun updateTabCount() {
        val count = tabManager.count
        binding.btnTabCount.text = if (count > 99) ":D" else count.toString()
    }

    private fun setupAddressBar() {
        binding.addressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                navigateToInput(); true
            } else false
        }
        binding.addressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.addressBar.post { binding.addressBar.selectAll() }
            } else {
                updateAddressBar(tabManager.activeTab?.webView?.url ?: "")
            }
        }
    }

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener { tabManager.activeTab?.webView?.let { if (it.canGoBack()) it.goBack() } }
        binding.btnForward.setOnClickListener { tabManager.activeTab?.webView?.let { if (it.canGoForward()) it.goForward() } }
        binding.btnRefresh.setOnClickListener {
            tabManager.activeTab?.webView?.let { wv ->
                if (binding.progressBar.visibility == View.VISIBLE) {
                    wv.stopLoading(); onPageFinished(wv.url ?: "")
                } else { wv.reload() }
            }
        }
        binding.btnHome.setOnClickListener { loadUrl(getSearchEngineHomeUrl()) }
        binding.btnTabCount.setOnClickListener { showTabSwitcher() }
        binding.btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
            popup.menu.findItem(R.id.action_desktop_mode)?.isChecked = isDesktopMode
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_new_tab -> { openNewTab(false); true }
                    R.id.action_new_incognito -> { openNewTab(true); true }
                    R.id.action_desktop_mode -> {
                        isDesktopMode = !isDesktopMode
                        val activeWebView = tabManager.activeTab?.webView
                        activeWebView?.settings?.userAgentString = buildUserAgent()
                        activeWebView?.settings?.useWideViewPort = isDesktopMode
                        activeWebView?.settings?.loadWithOverviewMode = isDesktopMode
                        activeWebView?.reload()
                        true
                    }
                    R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                    R.id.action_share -> {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, tabManager.activeTab?.webView?.url) }
                        startActivity(Intent.createChooser(i, getString(R.string.share_url))); true
                    }
                    R.id.action_open_external -> { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tabManager.activeTab?.webView?.url))) }; true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun showTabSwitcher() {
        val existing = supportFragmentManager.findFragmentByTag("tab_switcher") as? TabSwitcherSheet
        if (existing != null && existing.isAdded) return
        val sheet = TabSwitcherSheet()
        sheet.tabs = tabManager.previews().toMutableList()
        sheet.activeIndex = tabManager.activeIndex
        sheet.show(supportFragmentManager, "tab_switcher")
    }

    override fun onTabSelected(index: Int) { tabManager.switchTo(index); attachActiveWebView() }
    override fun onTabClosed(index: Int) {
        val wasActive = index == tabManager.activeIndex
        tabManager.closeTab(index)
        if (tabManager.count == 0) openNewTab(false)
        else if (wasActive) attachActiveWebView()
        else updateTabCount()
    }
    override fun onNewTab() { openNewTab(false) }
    override fun onNewIncognitoTab() { openNewTab(true) }

    fun loadUrl(input: String) {
        tabManager.activeTab?.webView?.loadUrl(formatUrl(input))
        tabManager.activeTab?.url = formatUrl(input)
        hideKeyboard()
    }

    private fun navigateToInput() {
        val input = binding.addressBar.text?.toString()?.trim() ?: ""
        if (input.isNotEmpty()) loadUrl(input)
        hideKeyboard()
    }

    private fun formatUrl(input: String): String {
        val t = input.trim()
        return when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> getSearchQueryUrl(t)
        }
    }

    fun updateAddressBar(url: String) {
        val display = url.removePrefix("https://").removePrefix("http://")
        if (!binding.addressBar.isFocused) binding.addressBar.setText(display)
        binding.lockIcon.setImageResource(if (url.startsWith("https://")) R.drawable.ic_lock_24 else R.drawable.ic_lock_open_24)
    }

    fun onTabUrlUpdated(webView: WebView, url: String) {
        tabManager.tabs.find { it.webView === webView }?.url = url
    }

    fun onPageStarted(url: String) {
        binding.swipeRefresh.isRefreshing = false
        updateAddressBar(url)
        binding.btnRefresh.setImageResource(R.drawable.ic_close_24)
        binding.progressBar.visibility = View.VISIBLE
        updateNavigationState()
    }

    fun onPageFinished(url: String) {
        binding.swipeRefresh.isRefreshing = false
        updateAddressBar(url)
        binding.progressBar.visibility = View.INVISIBLE
        binding.btnRefresh.setImageResource(R.drawable.ic_refresh_24)
        updateNavigationState()
        tabManager.activeTab?.webView?.let { injectScrollTracker(it) }
        nestedScrollActive = false
    }

    fun onProgressChanged(progress: Int) {
        binding.progressBar.progress = progress
        binding.progressBar.visibility = if (progress < 100) View.VISIBLE else View.INVISIBLE
    }

    private fun updateNavigationState() {
        val wv = tabManager.activeTab?.webView
        binding.btnBack.alpha = if (wv?.canGoBack() == true) 1.0f else 0.38f
        binding.btnForward.alpha = if (wv?.canGoForward() == true) 1.0f else 0.38f
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.addressBar.windowToken, 0)
        binding.addressBar.clearFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val wv = tabManager.activeTab?.webView
            if (wv?.canGoBack() == true) { wv.goBack(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}

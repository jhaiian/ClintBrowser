package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.suggestions.*
import com.jhaiian.clint.browser.menu.showMenu
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.webview.loadJsAsset

import android.content.Context
import android.content.Intent
import android.Manifest
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.bookmarks.Bookmark
import com.jhaiian.clint.bookmarks.BookmarkManager
import com.jhaiian.clint.history.SearchHistoryManager

internal fun MainActivity.applyAddressBarPosition() {
    val position = prefs.getString("address_bar_position", "top") ?: "top"
    when (position) {
        "top" -> {
            binding.toolbarTop.visibility = View.VISIBLE
            binding.bottomBar.visibility = View.GONE
            binding.toolbarBottom.visibility = View.GONE
            restoreStatusBarInset()
        }
        "bottom" -> {
            binding.toolbarTop.visibility = View.GONE
            binding.bottomBar.visibility = View.GONE
            binding.toolbarBottom.visibility = View.VISIBLE
            statusBarInsetPx = 0
            binding.toolbarTop.setPadding(0, 0, 0, 0)
            val sbLp = binding.statusBarBackground.layoutParams
            sbLp.height = 0
            binding.statusBarBackground.layoutParams = sbLp
        }
        else -> {
            binding.toolbarTop.visibility = View.VISIBLE
            binding.bottomBar.visibility = View.VISIBLE
            binding.toolbarBottom.visibility = View.GONE
            restoreStatusBarInset()
        }
    }
    topBarFraction = 0f
    bottomBarFraction = 0f
    topBarFullHeight = 0
    bottomBarFullHeight = 0
    binding.toolbarTop.translationY = 0f
    binding.toolbarBottom.translationY = 0f
    binding.bottomBar.translationY = 0f
    ViewCompat.requestApplyInsets(binding.toolbarTop)
    ViewCompat.requestApplyInsets(binding.bottomBar)
    ViewCompat.requestApplyInsets(binding.toolbarBottom)
    updateMainContentInsets()
}

private fun MainActivity.restoreStatusBarInset() {
    if (prefs.getBoolean("hide_status_bar", false)) return
    if (cachedStatusBarInsetPx > 0) {
        statusBarInsetPx = cachedStatusBarInsetPx
        binding.toolbarTop.setPadding(0, cachedStatusBarInsetPx, 0, 0)
        val sbLp = binding.statusBarBackground.layoutParams
        sbLp.height = cachedStatusBarInsetPx
        binding.statusBarBackground.layoutParams = sbLp
    }
}

internal fun MainActivity.setupAddressBar() {
    suggestionFetcherTop = SuggestionFetcher()
    suggestionFetcherBottom = SuggestionFetcher()

    val bgHandler = android.os.Handler(android.os.Looper.getMainLooper())

    val buildAdapter = { searchBar: com.google.android.material.search.SearchBar,
                         searchView: com.google.android.material.search.SearchView,
                         fetcher: SuggestionFetcher,
                         recyclerView: androidx.recyclerview.widget.RecyclerView ->

        var adapterRef: SearchSuggestionsAdapter? = null
        val adapter = SearchSuggestionsAdapter(
            onItemClick = { item ->
                val formatted = formatUrl(item)
                searchBar.setText(formatted)
                setSearchBarLockIcon(searchBar, formatted)
                searchView.hide()
                SearchHistoryManager.add(this, item)
                loadUrl(item)
            },
            onItemFill = { item ->
                searchView.editText.setText(item)
                searchView.editText.setSelection(item.length)
            },
            onHistoryDelete = { item ->
                Thread {
                    SearchHistoryManager.delete(this, item)
                    val query = searchView.editText.text?.toString() ?: ""
                    val history = SearchHistoryManager.search(this, query)
                    val bookmarks = BookmarkManager.search(this, query)
                    bgHandler.post { adapterRef?.submitCombined(bookmarks, history, emptyList()) }
                }.start()
            }
        )
        adapterRef = adapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = adapter
        adapter
    }

    val adapterTop = buildAdapter(
        binding.addressBar,
        binding.addressBarSearch,
        suggestionFetcherTop!!,
        binding.suggestionsListTop
    )
    val adapterBottom = buildAdapter(
        binding.addressBarBottom,
        binding.addressBarSearchBottom,
        suggestionFetcherBottom!!,
        binding.suggestionsListBottom
    )

    val setupPair = { searchBar: com.google.android.material.search.SearchBar,
                      searchView: com.google.android.material.search.SearchView,
                      fetcher: SuggestionFetcher,
                      adapter: SearchSuggestionsAdapter ->

        searchBar.setOnClickListener { searchView.show() }

        val relevantToolbar: View = if (searchBar === binding.addressBar) binding.toolbarTop else binding.toolbarBottom
        var savedToolbarVisibility = View.VISIBLE

        searchView.addTransitionListener { _, _, newState ->
            if (newState == com.google.android.material.search.SearchView.TransitionState.SHOWN) {
                savedToolbarVisibility = relevantToolbar.visibility
                relevantToolbar.visibility = View.INVISIBLE
                val current = tabManager.activeTab?.webView?.url ?: ""
                searchView.editText.setText(current)
                searchView.editText.selectAll()
                Thread {
                    val history = SearchHistoryManager.getAll(this)
                    val bookmarks = BookmarkManager.getAll(this)
                    bgHandler.post { adapter.submitCombined(bookmarks, history, emptyList()) }
                }.start()
            } else if (newState == com.google.android.material.search.SearchView.TransitionState.HIDING) {
                relevantToolbar.visibility = savedToolbarVisibility
            } else if (newState == com.google.android.material.search.SearchView.TransitionState.HIDDEN) {
                fetcher.cancel()
                adapter.submitCombined(emptyList(), emptyList(), emptyList())
                val current = tabManager.activeTab?.url ?: ""
                setSearchBarLockIcon(searchBar, current)
                searchBar.setText(current)
            }
        }

        searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                Thread {
                    val history = SearchHistoryManager.search(this@setupAddressBar, query)
                    val bookmarks = BookmarkManager.search(this@setupAddressBar, query)
                    bgHandler.post {
                        if (query.isBlank()) {
                            fetcher.cancel()
                            adapter.submitCombined(bookmarks, history, emptyList())
                        } else {
                            fetcher.fetch(query) { suggestions ->
                                adapter.submitCombined(bookmarks, history, suggestions)
                            }
                        }
                    }
                }.start()
            }
        })

        searchView.editText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                    event.action == android.view.KeyEvent.ACTION_DOWN)) {
                val input = searchView.editText.text?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    val formatted = formatUrl(input)
                    searchBar.setText(formatted)
                    setSearchBarLockIcon(searchBar, formatted)
                    searchView.hide()
                    Thread { SearchHistoryManager.add(this, input) }.start()
                    loadUrl(input)
                }
                true
            } else false
        }

        searchView.post {
            if (searchView.toolbar.menu.size() == 0) {
                searchView.toolbar.inflateMenu(R.menu.search_view_actions)
                val tintColor = getThemeColor(R.attr.clintIconTint)
                val tintList = android.content.res.ColorStateList.valueOf(tintColor)
                val micItem = searchView.toolbar.menu.findItem(R.id.action_voice_search)
                micItem?.icon?.mutate()?.let { icon ->
                    androidx.core.graphics.drawable.DrawableCompat.setTintList(icon, tintList)
                    micItem.icon = icon
                }
                searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        micItem?.isVisible = s?.isEmpty() == true
                    }
                })
                searchView.toolbar.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_voice_search -> { handleVoiceSearchTap(searchView); true }
                        else -> false
                    }
                }
            }
        }
    }

    setupPair(binding.addressBar, binding.addressBarSearch, suggestionFetcherTop!!, adapterTop)
    setupPair(binding.addressBarBottom, binding.addressBarSearchBottom, suggestionFetcherBottom!!, adapterBottom)
    binding.addressBarSearch.setupWithSearchBar(binding.addressBar)
    binding.addressBarSearchBottom.setupWithSearchBar(binding.addressBarBottom)
    binding.addressBarSearchBottom.post {
        var rootLinear: android.widget.LinearLayout? = null
        for (i in 0 until binding.addressBarSearchBottom.childCount) {
            val child = binding.addressBarSearchBottom.getChildAt(i)
            if (child is android.widget.LinearLayout) {
                rootLinear = child
                break
            }
        }
        val linear = rootLinear ?: return@post
        if (linear.childCount < 2) return@post
        val first = linear.getChildAt(0)
        val second = linear.getChildAt(1)
        linear.removeAllViews()
        linear.addView(second)
        linear.addView(first)
        linear.gravity = android.view.Gravity.BOTTOM
    }

    val tintColor = getThemeColor(R.attr.clintIconTint)
    val tintList = android.content.res.ColorStateList.valueOf(tintColor)
    listOf(binding.addressBarSearch, binding.addressBarSearchBottom).forEach { searchView ->
        searchView.post {
            val navIcon = ContextCompat.getDrawable(this, R.drawable.ic_arrow_back_24)?.mutate()
            if (navIcon != null) {
                androidx.core.graphics.drawable.DrawableCompat.setTintList(navIcon, tintList)
                searchView.toolbar.navigationIcon = navIcon
            }
        }
    }
}

internal fun MainActivity.setupNavigationButtons() {
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
    binding.btnBookmark.setOnClickListener {
        val url = tabManager.activeTab?.webView?.url ?: return@setOnClickListener
        val title = tabManager.activeTab?.title ?: url
        if (BookmarkManager.isBookmarked(this, url)) {
            BookmarkManager.remove(this, url)
        } else {
            BookmarkManager.add(this, Bookmark(url = url, title = title))
        }
        updateBookmarkIcon()
    }
    binding.btnMenu.setOnClickListener { anchor -> showMenu(anchor) }
    binding.btnTabCountBottom.setOnClickListener { showTabSwitcher() }
    binding.btnMenuBottom.setOnClickListener { anchor -> showMenu(anchor) }
}

internal fun MainActivity.loadUrl(input: String) {
    val url = formatUrl(input)
    val wv = tabManager.activeTab?.webView ?: return
    tabManager.activeTab?.url = url
    val headers = buildDesktopHeaders()
    if (headers != null) wv.loadUrl(url, headers) else wv.loadUrl(url)
    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
}

private fun MainActivity.setSearchBarLockIcon(
    searchBar: com.google.android.material.search.SearchBar,
    url: String
) {
    val lockRes = if (url.startsWith("https://")) R.drawable.ic_lock_24 else R.drawable.ic_lock_open_24
    searchBar.navigationIcon = androidx.core.content.ContextCompat.getDrawable(this, lockRes)
}

internal fun MainActivity.formatUrl(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(".") && !t.contains(" ") -> {
            val host = t.substringBefore("/").substringBefore(":")
            val isIpAddress = host.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))
            if (isIpAddress) "http://$t" else "https://$t"
        }
        else -> getSearchQueryUrl(t)
    }
}

internal fun MainActivity.updateAddressBar(url: String) {
    if (!binding.addressBarSearch.isShowing) {
        setSearchBarLockIcon(binding.addressBar, url)
        binding.addressBar.setText(url)
    }
    if (!binding.addressBarSearchBottom.isShowing) {
        setSearchBarLockIcon(binding.addressBarBottom, url)
        binding.addressBarBottom.setText(url)
    }
}

internal fun MainActivity.onTabUrlUpdated(webView: android.webkit.WebView, url: String) {
    tabManager.tabs.find { it.webView === webView }?.url = url
    if (tabManager.activeTab?.webView === webView &&
        !binding.addressBarSearch.isShowing && !binding.addressBarSearchBottom.isShowing) {
        updateAddressBar(url)
    }
}

internal fun MainActivity.onPageStarted(url: String) {
    binding.swipeRefresh.isRefreshing = false
    updateAddressBar(url)
    binding.btnRefresh.setImageResource(R.drawable.ic_close_24)
    binding.progressBar.visibility = View.VISIBLE
    binding.progressBarBottom.visibility = View.VISIBLE
    updateNavigationState()
    if (hasWebBottomNav) {
        hasWebBottomNav = false
        animateBottomBarTo(0f, animated = true)
    }

    if (url.startsWith("http")) {
        if (url == autoDesktopPendingReload) {
            autoDesktopPendingReload = null
        } else {
            val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()
            if (host != null) {
                val shouldSaveState = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(
                        com.jhaiian.clint.settings.desktopmode.DesktopModeActivity.PREF_DESKTOP_MODE_SAVE_STATE,
                        com.jhaiian.clint.settings.desktopmode.DesktopModeActivity.VALUE_SAVE_STATE
                    ) == com.jhaiian.clint.settings.desktopmode.DesktopModeActivity.VALUE_SAVE_STATE

                val isSaved = shouldSaveState && com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
                    .getState(this, host, com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase.TYPE_DESKTOP_MODE) != null

                val hostDomain = registeredDomain(host)
                val lockedDomain = desktopModeHost?.let { registeredDomain(it) }

                when {
                    isSaved && !isDesktopMode -> {
                        isDesktopMode = true
                        desktopModeHost = host
                        tabManager.tabs.forEach { tab ->
                            tab.webView.settings.userAgentString = buildUserAgent()
                            applyUserAgentMetadata(tab.webView)
                            addDesktopScript(tab)
                        }
                        tabManager.activeTab?.webView?.let { wv ->
                            val headers = buildDesktopHeaders()
                            if (headers != null) {
                                autoDesktopPendingReload = url
                                wv.loadUrl(url, headers)
                            }
                        }
                    }
                    isSaved && isDesktopMode && host != desktopModeHost -> {
                        desktopModeHost = host
                    }
                    !isSaved && isDesktopMode && host != desktopModeHost -> {
                        if (hostDomain == lockedDomain || !shouldSaveState) {
                            desktopModeHost = host
                        } else {
                            isDesktopMode = false
                            desktopModeHost = null
                            tabManager.tabs.forEach { tab ->
                                tab.webView.settings.userAgentString = buildUserAgent()
                                applyUserAgentMetadata(tab.webView)
                                removeDesktopScript(tab)
                            }
                            tabManager.activeTab?.webView?.reload()
                        }
                    }
                }
            }
        }
    }
}

internal fun MainActivity.onPageFinished(url: String) {
    binding.swipeRefresh.isRefreshing = false
    updateAddressBar(url)
    binding.progressBar.visibility = View.INVISIBLE
    binding.progressBarBottom.visibility = View.INVISIBLE
    binding.btnRefresh.setImageResource(R.drawable.ic_refresh_24)
    updateNavigationState()
    tabManager.activeTab?.webView?.let { wv ->
        injectScrollTracker(wv)
        injectBottomNavDetector(wv)
        wv.evaluateJavascript(loadJsAsset("link_touch_tracker.js"), null)
        val theme = prefs.getString("app_theme", "default") ?: "default"
        val darkWeb = when (theme) { "dark" -> true; "light" -> false; else -> prefs.getBoolean("force_dark_web", false) }
        if (darkWeb
            && !WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
            && !WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
        ) {
            wv.evaluateJavascript(loadJsAsset("dark_mode.js"), null)
        }
    }
    nestedScrollActive = false
    updateBookmarkIcon()

    if (url.startsWith("http") && !SearchHistoryManager.isSearchEngineUrl(url)) {
        val title = tabManager.activeTab?.webView?.title ?: ""
        Thread {
            SearchHistoryManager.add(applicationContext, url, title)
            if (com.jhaiian.clint.bookmarks.BookmarkManager.isBookmarked(applicationContext, url)) {
                com.jhaiian.clint.bookmarks.BookmarkManager.updateLastVisit(applicationContext, url)
            }
        }.start()
    }
}

internal fun MainActivity.onProgressChanged(progress: Int) {
    binding.progressBar.progress = progress
    binding.progressBar.visibility = if (progress < 100) View.VISIBLE else View.INVISIBLE
    binding.progressBarBottom.progress = progress
    binding.progressBarBottom.visibility = if (progress < 100) View.VISIBLE else View.INVISIBLE
}

internal fun MainActivity.updateBookmarkIcon() {
    val url = tabManager.activeTab?.webView?.url ?: ""
    val isBookmarked = url.isNotEmpty() && BookmarkManager.isBookmarked(this, url)
    binding.btnBookmark.setImageResource(
        if (isBookmarked) R.drawable.ic_bookmark_filled_24 else R.drawable.ic_bookmark_24
    )
    binding.btnBookmark.alpha = if (url.isEmpty()) 0.38f else 1.0f
}

internal fun MainActivity.updateNavigationState() {
    val wv = tabManager.activeTab?.webView
    binding.btnBack.alpha = if (wv?.canGoBack() == true) 1.0f else 0.38f
    binding.btnForward.alpha = if (wv?.canGoForward() == true) 1.0f else 0.38f
}

internal fun MainActivity.updateTabCount() {
    val count = tabManager.count
    val text = if (count > 99) ":D" else count.toString()
    binding.btnTabCount.text = text
    binding.btnTabCountBottom.text = text
}

internal fun MainActivity.updateIncognitoState(isIncognito: Boolean) {
    binding.incognitoIcon.visibility = if (isIncognito) View.VISIBLE else View.GONE
    binding.incognitoIconBottom.visibility = if (isIncognito) View.VISIBLE else View.GONE
    val color = if (isIncognito) {
        ContextCompat.getColor(this, R.color.incognito_toolbar_color)
    } else {
        getThemeColor(com.google.android.material.R.attr.colorSurface)
    }
    binding.toolbarTop.setBackgroundColor(color)
    binding.toolbarBottom.setBackgroundColor(color)
    binding.bottomBar.setBackgroundColor(color)
    binding.statusBarBackground.setBackgroundColor(color)
}

internal fun MainActivity.updateSwipeRefreshColors(isIncognito: Boolean) {
    binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
        if (isIncognito) ContextCompat.getColor(this, R.color.incognito_toolbar_color)
        else getThemeColor(com.google.android.material.R.attr.colorSurface)
    )
    if (isIncognito) {
        binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.incognito_accent))
    } else {
        binding.swipeRefresh.setColorSchemeColors(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
    }
}

internal fun MainActivity.hideKeyboard() {
    if (binding.addressBarSearch.isShowing) binding.addressBarSearch.hide()
    if (binding.addressBarSearchBottom.isShowing) binding.addressBarSearchBottom.hide()
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
}

internal fun MainActivity.getThemeColor(attrId: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrId, typedValue, true)
    return typedValue.data
}

internal fun MainActivity.handleVoiceSearchTap(searchView: com.google.android.material.search.SearchView) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        pendingVoiceSearchEditText = searchView.editText
        launchVoiceSearch()
    } else {
        pendingVoiceSearchEditText = searchView.editText
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.voice_search_permission_title))
            .setMessage(getString(R.string.voice_search_permission_message))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }
}

private fun registeredDomain(host: String): String =
    com.jhaiian.clint.util.registeredDomain(host)

internal fun MainActivity.launchVoiceSearch() {
    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
    }
    runCatching { voiceSearchLauncher.launch(intent) }.onFailure {
        pendingVoiceSearchEditText = null
        com.jhaiian.clint.ui.ClintToast.show(this, getString(R.string.voice_search_not_available), R.drawable.ic_mic_24)
    }
}

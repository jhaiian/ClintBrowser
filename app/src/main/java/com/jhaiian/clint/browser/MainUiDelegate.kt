package com.jhaiian.clint.browser

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.webkit.WebViewFeature
import com.jhaiian.clint.R
import com.jhaiian.clint.bookmarks.Bookmark
import com.jhaiian.clint.bookmarks.BookmarkManager
import com.jhaiian.clint.bookmarks.BookmarksActivity
import com.jhaiian.clint.downloads.DownloadsActivity
import com.jhaiian.clint.settings.SettingsActivity
import com.jhaiian.clint.webview.ClintWebViewClient

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
    val setupBar = { bar: android.widget.EditText ->
        bar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                navigateFromBar(bar); true
            } else false
        }
        bar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                bar.post { bar.selectAll() }
            } else {
                updateAddressBar(tabManager.activeTab?.webView?.url ?: "")
            }
        }
    }
    setupBar(binding.addressBar)
    setupBar(binding.addressBarBottom)
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

private fun MainActivity.showMenu(anchor: View) {
    val style = prefs.getString("menu_style", "popup") ?: "popup"
    if (style == "bottom_sheet") {
        showBottomSheetMenu()
    } else {
        showPopupMenu(anchor)
    }
}

private fun MainActivity.showBottomSheetMenu() {
    val position = prefs.getString("address_bar_position", "top") ?: "top"
    val bottomBarHidden = position != "split"

    val wv = tabManager.activeTab?.webView
    val currentUrl = wv?.url ?: ""
    val isLoading = binding.progressBar.visibility == View.VISIBLE ||
        binding.progressBarBottom.visibility == View.VISIBLE

    val currentUri = currentUrl.takeIf { it.isNotEmpty() }
        ?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
    val webClient = wv?.webViewClient as? ClintWebViewClient
    val appMatches = if (currentUri != null && webClient != null &&
        (currentUri.scheme == "http" || currentUri.scheme == "https")) {
        webClient.resolveAppMatches(currentUri, this)
    } else emptyList()

    val openInAppEnabled = appMatches.isNotEmpty()
    val openInAppLabel: String? = if (appMatches.size == 1)
        getString(R.string.menu_open_in_named_app, appMatches[0].loadLabel(packageManager).toString())
    else null

    val sheet = MenuBottomSheet().apply {
        showNavRow = bottomBarHidden
        canGoBack = wv?.canGoBack() == true
        canGoForward = wv?.canGoForward() == true
        isBookmarked = currentUrl.isNotEmpty() && BookmarkManager.isBookmarked(this@showBottomSheetMenu, currentUrl)
        this.isLoading = isLoading
        this.isDesktopMode = this@showBottomSheetMenu.isDesktopMode
        this.isDataSaverEnabled = prefs.getBoolean("data_saver_enabled", false)
        this.openInAppEnabled = openInAppEnabled
        this.openInAppLabel = openInAppLabel
    }
    sheet.show(supportFragmentManager, "menu_sheet")
}

private fun MainActivity.showPopupMenu(anchor: View) {
    val position = prefs.getString("address_bar_position", "top") ?: "top"
    val bottomBarHidden = position != "split"

    val popupView = LayoutInflater.from(this).inflate(R.layout.popup_menu, null)
    val popup = PopupWindow(
        popupView,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    )
    popup.elevation = 12f
    popup.isOutsideTouchable = true

    val desktopCheck = popupView.findViewById<ImageView>(R.id.desktop_mode_check)
    desktopCheck.alpha = if (isDesktopMode) 1f else 0f

    val navIconsRow = popupView.findViewById<View>(R.id.nav_icons_row)
    val navIconsDivider = popupView.findViewById<View>(R.id.nav_icons_divider)

    if (bottomBarHidden) {
        navIconsRow.visibility = View.VISIBLE
        navIconsDivider.visibility = View.VISIBLE

        val wv = tabManager.activeTab?.webView
        val popupBtnBack = popupView.findViewById<ImageButton>(R.id.popup_btn_back)
        val popupBtnForward = popupView.findViewById<ImageButton>(R.id.popup_btn_forward)
        val popupBtnHome = popupView.findViewById<ImageButton>(R.id.popup_btn_home)
        val popupBtnRefresh = popupView.findViewById<ImageButton>(R.id.popup_btn_refresh)
        val popupBtnBookmark = popupView.findViewById<ImageButton>(R.id.popup_btn_bookmark)

        popupBtnBack.alpha = if (wv?.canGoBack() == true) 1.0f else 0.38f
        popupBtnForward.alpha = if (wv?.canGoForward() == true) 1.0f else 0.38f

        val url = tabManager.activeTab?.webView?.url ?: ""
        val isBookmarked = url.isNotEmpty() && BookmarkManager.isBookmarked(this, url)
        popupBtnBookmark.setImageResource(
            if (isBookmarked) R.drawable.ic_bookmark_filled_24 else R.drawable.ic_bookmark_24
        )

        val isLoading = binding.progressBar.visibility == View.VISIBLE ||
            binding.progressBarBottom.visibility == View.VISIBLE
        popupBtnRefresh.setImageResource(
            if (isLoading) R.drawable.ic_close_24 else R.drawable.ic_refresh_24
        )

        popupBtnBack.setOnClickListener {
            popup.dismiss()
            tabManager.activeTab?.webView?.let { if (it.canGoBack()) it.goBack() }
        }
        popupBtnForward.setOnClickListener {
            popup.dismiss()
            tabManager.activeTab?.webView?.let { if (it.canGoForward()) it.goForward() }
        }
        popupBtnHome.setOnClickListener {
            popup.dismiss()
            loadUrl(getSearchEngineHomeUrl())
        }
        popupBtnRefresh.setOnClickListener {
            popup.dismiss()
            tabManager.activeTab?.webView?.let { wv2 ->
                if (isLoading) {
                    wv2.stopLoading(); onPageFinished(wv2.url ?: "")
                } else {
                    wv2.reload()
                }
            }
        }
        popupBtnBookmark.setOnClickListener {
            popup.dismiss()
            val bookmarkUrl = tabManager.activeTab?.webView?.url ?: return@setOnClickListener
            val title = tabManager.activeTab?.title ?: bookmarkUrl
            if (BookmarkManager.isBookmarked(this, bookmarkUrl)) {
                BookmarkManager.remove(this, bookmarkUrl)
            } else {
                BookmarkManager.add(this, Bookmark(url = bookmarkUrl, title = title))
            }
            updateBookmarkIcon()
        }
    }

    val openInAppItem = popupView.findViewById<View>(R.id.menu_open_in_app)
    val openInAppText = popupView.findViewById<android.widget.TextView>(R.id.menu_open_in_app_text)
    val currentUrl = tabManager.activeTab?.webView?.url
    val currentUri = currentUrl?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
    val webClient = tabManager.activeTab?.webView?.webViewClient as? ClintWebViewClient
    val appMatches = if (currentUri != null && webClient != null &&
        (currentUri.scheme == "http" || currentUri.scheme == "https")) {
        webClient.resolveAppMatches(currentUri, this)
    } else emptyList()

    if (appMatches.isEmpty()) {
        openInAppItem.isEnabled = false
        openInAppItem.alpha = 0.38f
        openInAppText.text = getString(R.string.menu_open_in_app)
    } else if (appMatches.size == 1) {
        val appName = appMatches[0].loadLabel(packageManager).toString()
        openInAppText.text = getString(R.string.menu_open_in_named_app, appName)
        openInAppItem.setOnClickListener {
            popup.dismiss()
            val intent = Intent(Intent.ACTION_VIEW, currentUri)
                .setPackage(appMatches[0].activityInfo.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
        }
    } else {
        openInAppText.text = getString(R.string.menu_open_in_app)
        openInAppItem.setOnClickListener {
            popup.dismiss()
            val wv = tabManager.activeTab?.webView ?: return@setOnClickListener
            webClient?.tryOpenInApp(wv, currentUri!!)
        }
    }

    popupView.findViewById<View>(R.id.menu_new_tab).setOnClickListener { popup.dismiss(); openNewTab(false) }
    popupView.findViewById<View>(R.id.menu_incognito).setOnClickListener { popup.dismiss(); openNewTab(true) }
    popupView.findViewById<View>(R.id.menu_share).setOnClickListener {
        popup.dismiss()
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tabManager.activeTab?.webView?.url)
        }
        startActivity(Intent.createChooser(i, getString(R.string.share_url)))
    }
    popupView.findViewById<View>(R.id.menu_downloads).setOnClickListener {
        popup.dismiss(); startActivity(Intent(this, DownloadsActivity::class.java))
    }
    popupView.findViewById<View>(R.id.menu_bookmarks).setOnClickListener {
        popup.dismiss(); startActivity(Intent(this, BookmarksActivity::class.java))
    }
    popupView.findViewById<View>(R.id.menu_reader_mode).setOnClickListener {
        popup.dismiss()
        onMenuReaderMode()
    }
    popupView.findViewById<View>(R.id.menu_desktop_mode).setOnClickListener {
        isDesktopMode = !isDesktopMode
        desktopCheck.alpha = if (isDesktopMode) 1f else 0f
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
        } else {
            wv?.reload()
        }
        popup.dismiss()
    }

    val dataSaverCheck = popupView.findViewById<ImageView>(R.id.data_saver_check)
    dataSaverCheck.alpha = if (prefs.getBoolean("data_saver_enabled", false)) 1f else 0f
    popupView.findViewById<View>(R.id.menu_data_saver).setOnClickListener {
        popup.dismiss()
        onMenuDataSaver()
    }
    popupView.findViewById<View>(R.id.menu_data_saver).setOnLongClickListener {
        popup.dismiss()
        onMenuOpenDataSaverSettings()
        true
    }
    popupView.findViewById<View>(R.id.menu_settings).setOnClickListener {
        popup.dismiss(); startActivity(Intent(this, SettingsActivity::class.java))
    }

    popupView.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    val xOff = -popupView.measuredWidth + anchor.width
    val yOff = if (position == "bottom") -(popupView.measuredHeight + anchor.height) else 0
    popup.showAsDropDown(anchor, xOff, yOff, Gravity.TOP or Gravity.END)
}

internal fun MainActivity.loadUrl(input: String) {
    val url = formatUrl(input)
    val wv = tabManager.activeTab?.webView ?: return
    tabManager.activeTab?.url = url
    val headers = buildDesktopHeaders()
    if (headers != null) wv.loadUrl(url, headers) else wv.loadUrl(url)
    hideKeyboard()
}

private fun MainActivity.navigateFromBar(bar: android.widget.EditText) {
    val input = bar.text?.toString()?.trim() ?: ""
    if (input.isNotEmpty()) loadUrl(input)
    hideKeyboard()
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
    if (!binding.addressBar.isFocused) binding.addressBar.setText(url)
    if (!binding.addressBarBottom.isFocused) binding.addressBarBottom.setText(url)
    val lockRes = if (url.startsWith("https://")) R.drawable.ic_lock_24 else R.drawable.ic_lock_open_24
    binding.lockIcon.setImageResource(lockRes)
    binding.lockIconBottom.setImageResource(lockRes)
}

internal fun MainActivity.onTabUrlUpdated(webView: android.webkit.WebView, url: String) {
    tabManager.tabs.find { it.webView === webView }?.url = url
    if (tabManager.activeTab?.webView === webView &&
        !binding.addressBar.isFocused && !binding.addressBarBottom.isFocused) {
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
        binding.swipeRefresh.setColorSchemeColors(getThemeColor(com.google.android.material.R.attr.colorPrimary))
    }
}

internal fun MainActivity.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(binding.addressBar.windowToken, 0)
    imm.hideSoftInputFromWindow(binding.addressBarBottom.windowToken, 0)
    binding.addressBar.clearFocus()
    binding.addressBarBottom.clearFocus()
}

internal fun MainActivity.getThemeColor(attrId: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrId, typedValue, true)
    return typedValue.data
}

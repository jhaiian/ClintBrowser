package com.jhaiian.clint.browser.menu

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.jhaiian.clint.R
import com.jhaiian.clint.bookmarks.Bookmark
import com.jhaiian.clint.bookmarks.BookmarkManager
import com.jhaiian.clint.bookmarks.BookmarksActivity
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.delegates.*
import com.jhaiian.clint.browser.webview.ClintWebViewClient
import com.jhaiian.clint.downloads.DownloadsActivity
import com.jhaiian.clint.history.HistoryActivity
import com.jhaiian.clint.settings.SettingsActivity

internal fun MainActivity.showMenu(anchor: View) {
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
        ?.let { runCatching { Uri.parse(it) }.getOrNull() }
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
    val openInAppText = popupView.findViewById<TextView>(R.id.menu_open_in_app_text)
    val currentUrl = tabManager.activeTab?.webView?.url
    val currentUri = currentUrl?.let { runCatching { Uri.parse(it) }.getOrNull() }
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
    popupView.findViewById<View>(R.id.menu_downloads).setOnLongClickListener {
        popup.dismiss()
        onMenuOpenDownloadSettings()
        true
    }
    popupView.findViewById<View>(R.id.menu_bookmarks).setOnClickListener {
        popup.dismiss(); startActivity(Intent(this, BookmarksActivity::class.java))
    }
    popupView.findViewById<View>(R.id.menu_history).setOnClickListener {
        popup.dismiss(); startActivity(Intent(this, HistoryActivity::class.java))
    }
    popupView.findViewById<View>(R.id.menu_reader_mode).setOnClickListener {
        popup.dismiss()
        onMenuReaderMode()
    }
    popupView.findViewById<View>(R.id.menu_desktop_mode).setOnClickListener {
        popup.dismiss()
        onMenuDesktopMode()
        desktopCheck.alpha = if (isDesktopMode) 1f else 0f
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
    val maxPopupH = (resources.displayMetrics.heightPixels * 0.90).toInt()
    if (popupView.measuredHeight > maxPopupH) {
        popup.height = maxPopupH
    }

    val xOff = -popupView.measuredWidth + anchor.width
    val yOff = if (position == "bottom") -(popupView.measuredHeight + anchor.height) else 0
    popup.showAsDropDown(anchor, xOff, yOff, Gravity.TOP or Gravity.END)
}

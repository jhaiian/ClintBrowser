package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.MainActivity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.jhaiian.clint.R
import com.jhaiian.clint.shortcuts.ShortcutIconStore
import com.jhaiian.clint.shortcuts.ShortcutSavedTab
import com.jhaiian.clint.shortcuts.ShortcutStore
import com.jhaiian.clint.shortcuts.ShortcutTabSessionManager
import com.jhaiian.clint.shortcuts.WebAppShortcut
import java.util.UUID

internal fun MainActivity.applyShortcutFrameless(isShortcutLaunch: Boolean) {
    val enabled = isShortcutLaunch && prefs.getBoolean("shortcut_frameless_enabled", true)
    if (uiState.isShortcutFrameless == enabled) return
    uiState.isShortcutFrameless = enabled
    uiState.topBarFraction = 0f
    uiState.bottomBarFraction = 0f
    uiState.topBarFullHeightPx = 0
    uiState.bottomBarFullHeightPx = 0
    updateMainContentInsets()
}

internal fun MainActivity.createHomeScreenShortcut(url: String, name: String, icon: Bitmap?) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
        Toast.makeText(this, getString(R.string.create_shortcut_unsupported), Toast.LENGTH_SHORT).show()
        return
    }
    val shortcutId = "webapp_${UUID.randomUUID()}"
    val iconPath = icon?.let { ShortcutIconStore.save(this, shortcutId, it) }
    ShortcutStore.save(this, WebAppShortcut(id = shortcutId, url = url, name = name, iconPath = iconPath, tabId = null))
    val shortcutIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setClass(this@createHomeScreenShortcut, MainActivity::class.java)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(MainActivity.EXTRA_SHORTCUT_ID, shortcutId)
    }
    val iconCompat = icon?.let { IconCompat.createWithBitmap(it) }
        ?: fallbackLauncherIconCompat()
    val shortcutInfo = ShortcutInfoCompat.Builder(this, shortcutId)
        .setShortLabel(name)
        .setLongLabel(name)
        .setIcon(iconCompat)
        .setIntent(shortcutIntent)
        .build()
    ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null)
}

internal fun MainActivity.openOrResumeShortcutTab(shortcutId: String, fallbackUrl: String?) {
    val groupInMemory = tabManager.tabs.any { tabManager.effectiveShortcutId(it) == shortcutId }
    if (groupInMemory) {
        val persistedActiveId = ShortcutTabSessionManager.load(this, shortcutId).firstOrNull { it.isActive }?.tabId
        val targetIndex = persistedActiveId
            ?.let { id -> tabManager.tabs.indexOfFirst { it.id == id && tabManager.effectiveShortcutId(it) == shortcutId } }
            ?.takeIf { it != -1 }
            ?: tabManager.tabs.indexOfFirst { tabManager.effectiveShortcutId(it) == shortcutId }
        tabManager.switchTo(targetIndex)
        attachActiveWebView()
        return
    }
    val record = ShortcutStore.get(this, shortcutId)
    val persistedTabs = ShortcutTabSessionManager.load(this, shortcutId)
    if (persistedTabs.isNotEmpty()) {
        val activeId = persistedTabs.firstOrNull { it.isActive }?.tabId ?: persistedTabs.first().tabId
        persistedTabs.forEach { saved ->
            openNewTabSilent(saved.url, saved.tabId, shortcutId, deferLoad = saved.tabId != activeId, title = saved.title)
        }
        val index = tabManager.tabs.indexOfFirst { it.id == activeId }
        tabManager.switchTo(if (index != -1) index else tabManager.tabs.lastIndex)
        attachActiveWebView()
        return
    }
    val url = record?.url ?: fallbackUrl ?: getSearchEngineHomeUrl()
    val previousTabId = tabManager.activeTab?.id
    openNewTab(isIncognito = false, url = url, shortcutId = shortcutId, previousTabId = previousTabId)
    val name = record?.name
    if (!name.isNullOrEmpty()) tabManager.activeTab?.title = name
    tabManager.activeTab?.let { ShortcutStore.updateTabId(this, shortcutId, it.id) }
}

internal fun MainActivity.exitShortcutFramelessToNormal() {
    val activeTab = tabManager.activeTab ?: return
    val activeShortcutId = tabManager.effectiveShortcutId(activeTab) ?: return
    persistShortcutTabsSession(activeShortcutId)
    captureActiveTabThumbnail()
    val previousId = activeTab.previousTabId
    val previousIndex = previousId
        ?.let { id -> tabManager.tabs.indexOfFirst { it.id == id && !tabManager.isGhostTab(it) } }
        ?.takeIf { it != -1 }
    val targetIndex = previousIndex ?: tabManager.tabs.indexOfLast { !tabManager.isGhostTab(it) }
    if (targetIndex != -1) {
        tabManager.switchTo(targetIndex)
        attachActiveWebView()
    } else {
        openNewTab(isIncognito = false, url = getSearchEngineHomeUrl())
    }
}

internal fun MainActivity.persistShortcutTabsSession(shortcutId: String) {
    val groupTabs = tabManager.tabs.filter { tabManager.effectiveShortcutId(it) == shortcutId }
    if (groupTabs.isEmpty()) return
    val previousActiveId = ShortcutTabSessionManager.load(this, shortcutId).firstOrNull { it.isActive }?.tabId
    val currentActiveId = tabManager.activeTab?.id
    val activeId = when {
        groupTabs.any { it.id == currentActiveId } -> currentActiveId
        groupTabs.any { it.id == previousActiveId } -> previousActiveId
        else -> groupTabs.first().id
    }
    val saved = groupTabs.mapIndexedNotNull { index, tab ->
        val url = tab.webView.url?.takeIf { it.isNotEmpty() && it != "about:blank" }
            ?: tab.url.takeIf { it.isNotEmpty() && it != "about:blank" }
            ?: return@mapIndexedNotNull null
        ShortcutSavedTab(position = index, url = url, title = tab.title, isActive = tab.id == activeId, tabId = tab.id)
    }
    if (saved.isNotEmpty()) Thread { ShortcutTabSessionManager.save(this, shortcutId, saved) }.start()
}

internal fun MainActivity.persistAllShortcutTabGroups() {
    val shortcutIds = tabManager.tabs.mapNotNull { tabManager.effectiveShortcutId(it) }.distinct()
    shortcutIds.forEach { persistShortcutTabsSession(it) }
}

private fun MainActivity.fallbackLauncherIconCompat(): IconCompat {
    val drawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
    val bitmap = drawable?.let {
        val width = it.intrinsicWidth.coerceAtLeast(1)
        val height = it.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        it.setBounds(0, 0, canvas.width, canvas.height)
        it.draw(canvas)
        bmp
    }
    return bitmap?.let { IconCompat.createWithBitmap(it) } ?: IconCompat.createWithResource(this, R.mipmap.ic_launcher)
}

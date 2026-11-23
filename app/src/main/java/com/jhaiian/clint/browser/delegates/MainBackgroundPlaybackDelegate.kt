package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.media.BackgroundPlayback
import com.jhaiian.clint.browser.media.BackgroundPlaybackBridge
import com.jhaiian.clint.browser.media.BackgroundPlaybackService
import com.jhaiian.clint.browser.media.BackgroundPlaybackTracker
import com.jhaiian.clint.browser.webview.ClintWebView
import com.jhaiian.clint.browser.webview.loadJsAsset

import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jhaiian.clint.R
import com.jhaiian.clint.tabs.BrowserTab
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun MainActivity.installBackgroundPlaybackBridge(webView: WebView) {
    webView.addJavascriptInterface(
        BackgroundPlaybackBridge(
            onState = { frameId, state, isTop -> onBackgroundMediaState(webView, frameId, state, isTop) },
            onInfo = { frameId, info -> onBackgroundMediaInfo(webView, frameId, info) }
        ),
        BackgroundPlaybackBridge.NAME
    )
}

internal fun MainActivity.keepsMediaAlive(webView: WebView): Boolean {
    val tab = tabManager.tabs.firstOrNull { it.webView === webView } ?: return false
    if (BackgroundPlayback.isEnabled(prefs) && BackgroundPlayback.session.value?.tabId == tab.id) return true
    return lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && backgroundPlaybackTracker.isPlaying(tab.id)
}

private fun MainActivity.isMediaParkable(tab: BrowserTab): Boolean =
    backgroundPlaybackTracker.isPlaying(tab.id) ||
        (BackgroundPlayback.isEnabled(prefs) && BackgroundPlayback.session.value?.tabId == tab.id)

internal fun MainActivity.syncParkedMediaViews() {
    val active = tabManager.activeTab?.webView
    for (index in webContainer.childCount - 1 downTo 0) {
        val child = webContainer.getChildAt(index)
        if (child === active) continue
        val tab = tabManager.tabs.firstOrNull { it.webView === child }
        if (tab == null || !isMediaParkable(tab)) webContainer.removeViewAt(index)
    }
    for (index in 0 until webContainer.childCount) {
        val child = webContainer.getChildAt(index)
        val parked = child !== active
        child.alpha = if (parked) 0f else 1f
        child.importantForAccessibility = if (parked) {
            android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        (child as? ClintWebView)?.parked = parked
        if (parked) child.clearFocus()
    }
    active?.bringToFront()
}

internal fun MainActivity.refreshBackgroundPlaybackVisibility() {
    tabManager.tabs.forEach { (it.webView as? ClintWebView)?.refreshWindowVisibility() }
}

internal fun MainActivity.addBackgroundPlaybackScript(tab: BrowserTab) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    removeBackgroundPlaybackScript(tab)
    val script = loadJsAsset("background_playback.js")
        .replace("__CLINT_BACKGROUND_PLAYBACK__", BackgroundPlayback.isEnabled(prefs).toString())
    backgroundPlaybackScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, script, setOf("*"))
}

internal fun MainActivity.removeBackgroundPlaybackScript(tab: BrowserTab) {
    backgroundPlaybackScriptHandlers.remove(tab.id)?.remove()
}

internal fun MainActivity.applyBackgroundPlaybackSetting() {
    val enabled = BackgroundPlayback.isEnabled(prefs)
    tabManager.tabs.forEach { tab ->
        addBackgroundPlaybackScript(tab)
        tab.webView.evaluateJavascript(
            "(function(){var c=window.__clintBackgroundPlayback;if(c)c.run({action:'config',backgroundPlayback:$enabled});})();",
            null
        )
    }
    if (!enabled) BackgroundPlayback.clear()
}

internal fun MainActivity.attachBackgroundPlayback() {
    val handler: (BackgroundPlayback.Command) -> Unit = { command ->
        runOnUiThread { onBackgroundPlaybackCommand(command) }
    }
    backgroundPlaybackCommandHandler = handler
    BackgroundPlayback.commandHandler = handler
    lifecycleScope.launch {
        BackgroundPlayback.session
            .map { it?.tabId }
            .distinctUntilChanged()
            .collect {
                refreshBackgroundPlaybackVisibility()
                syncParkedMediaViews()
            }
    }
}

internal fun MainActivity.detachBackgroundPlayback() {
    val handler = backgroundPlaybackCommandHandler ?: return
    backgroundPlaybackCommandHandler = null
    if (BackgroundPlayback.commandHandler !== handler) return
    BackgroundPlayback.commandHandler = null
    backgroundPlaybackTracker.clearAll()
    BackgroundPlayback.clear()
}

internal fun MainActivity.onBackgroundPlaybackWebViewDestroyed(webView: WebView) {
    val tab = tabManager.tabs.firstOrNull { it.webView === webView } ?: return
    backgroundPlaybackScriptHandlers.remove(tab.id)
    backgroundPlaybackTracker.clearTab(tab.id)
    if (BackgroundPlayback.session.value?.tabId == tab.id) BackgroundPlayback.clear()
}

private fun MainActivity.onBackgroundMediaState(webView: WebView, frameId: String, state: String, isTop: Boolean) {
    if (isFinishing || isDestroyed) return
    val tab = tabManager.tabs.firstOrNull { it.webView === webView } ?: return
    val gone = state == BackgroundPlaybackBridge.STATE_GONE && isTop
    if (gone) {
        backgroundPlaybackTracker.clearTab(tab.id)
    } else {
        backgroundPlaybackTracker.update(tab.id, frameId, state == BackgroundPlaybackBridge.STATE_PLAYING)
    }
    val playing = backgroundPlaybackTracker.isPlaying(tab.id)
    (webView as? ClintWebView)?.audioState?.audible = playing
    (webView as? ClintWebView)?.refreshWindowVisibility()
    if (tabManager.activeTab?.id != tab.id) syncParkedMediaViews()
    if (!BackgroundPlayback.isEnabled(prefs)) return
    val current = BackgroundPlayback.session.value
    if (gone) {
        if (current == null || current.tabId != tab.id) return
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            BackgroundPlayback.clear()
        } else {
            BackgroundPlayback.publish(current.copy(playing = false))
        }
        return
    }
    if (playing) {
        BackgroundPlayback.publish(backgroundPlaybackSessionFor(tab, playing = true))
        if (!BackgroundPlaybackService.isRunning) BackgroundPlaybackService.start(this)
    } else if (current?.tabId == tab.id) {
        BackgroundPlayback.publish(backgroundPlaybackSessionFor(tab, playing = false))
    }
}

private fun MainActivity.onBackgroundMediaInfo(webView: WebView, frameId: String, info: BackgroundPlaybackTracker.MediaInfo) {
    if (isFinishing || isDestroyed) return
    val tab = tabManager.tabs.firstOrNull { it.webView === webView } ?: return
    backgroundPlaybackTracker.updateInfo(tab.id, frameId, info)
    if (!BackgroundPlayback.isEnabled(prefs)) return
    val current = BackgroundPlayback.session.value ?: return
    if (current.tabId != tab.id) return
    BackgroundPlayback.publish(backgroundPlaybackSessionFor(tab, current.playing))
}

private fun MainActivity.backgroundPlaybackSessionFor(tab: BrowserTab, playing: Boolean): BackgroundPlayback.Session {
    val info = backgroundPlaybackTracker.info(tab.id)
    val positionMs = info?.let {
        if (it.durationMs > 0) it.positionMs.coerceAtMost(it.durationMs) else it.positionMs
    } ?: -1L
    val base = BackgroundPlayback.Session(
        tabId = tab.id,
        title = "",
        subtitle = "",
        playing = playing,
        durationMs = info?.durationMs ?: -1L,
        positionMs = positionMs,
        speed = info?.rate ?: 1f,
        positionUpdatedAt = info?.receivedAt ?: 0L,
        canSeek = info?.canSeek == true,
        canSkipNext = info?.canNext == true,
        canSkipPrevious = info?.canPrevious == true
    )
    if (tab.isIncognito) {
        return base.copy(title = getString(R.string.background_playback_incognito_title))
    }
    val host = android.net.Uri.parse(tab.webView.url ?: tab.url).host.orEmpty().removePrefix("www.")
    val pageTitle = tab.title.takeIf { it.isNotBlank() && it != tab.webView.url }
    return base.copy(
        title = info?.title?.takeIf { it.isNotEmpty() } ?: pageTitle ?: host,
        subtitle = info?.artist?.takeIf { it.isNotEmpty() } ?: host,
        album = info?.album.orEmpty(),
        artworkUrl = info?.artworkUrl
    )
}

private fun MainActivity.onBackgroundPlaybackCommand(command: BackgroundPlayback.Command) {
    val session = BackgroundPlayback.session.value ?: return
    val tab = tabManager.tabs.firstOrNull { it.id == session.tabId }
    if (tab == null) {
        BackgroundPlayback.clear()
        return
    }
    when (command) {
        BackgroundPlayback.Command.Play -> runBackgroundPlaybackAction(tab, "play")
        BackgroundPlayback.Command.Pause -> runBackgroundPlaybackAction(tab, "pause")
        BackgroundPlayback.Command.Next -> runBackgroundPlaybackAction(tab, "next")
        BackgroundPlayback.Command.Previous -> runBackgroundPlaybackAction(tab, "previous")
        is BackgroundPlayback.Command.SeekTo -> {
            runBackgroundPlaybackAction(tab, "seek", command.positionMs / 1000.0)
            BackgroundPlayback.publish(
                session.copy(
                    positionMs = if (session.durationMs > 0) command.positionMs.coerceAtMost(session.durationMs) else command.positionMs,
                    positionUpdatedAt = android.os.SystemClock.elapsedRealtime()
                )
            )
        }
        BackgroundPlayback.Command.Stop -> {
            runBackgroundPlaybackAction(tab, "pause")
            BackgroundPlayback.clear()
        }
    }
}

private fun MainActivity.runBackgroundPlaybackAction(tab: BrowserTab, action: String, time: Double = 0.0) {
    if (handleVideoTakeoverPlayback(tab, action)) return
    val frame = backgroundPlaybackTracker.lastFrame(tab.id).orEmpty()
    tab.webView.evaluateJavascript(
        "(function(){var c=window.__clintBackgroundPlayback;if(c)c.run({action:'$action',frame:'$frame',time:$time});})();",
        null
    )
}

internal fun MainActivity.toggleTabMute(tabId: String) {
    val webView = tabManager.tabs.firstOrNull { it.id == tabId }?.webView as? ClintWebView ?: return
    webView.setAudioMuted(!webView.audioState.muted)
}

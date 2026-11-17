package com.jhaiian.clint.browser.delegates

import androidx.lifecycle.Lifecycle
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.network.BrowserEngineRestart
import com.jhaiian.clint.browser.network.NetworkSwitchMonitor
import com.jhaiian.clint.downloads.ClintDownloadManager

internal fun MainActivity.startNetworkSwitchMonitor() {
    if (networkSwitchMonitor != null) return
    networkSwitchMonitor = NetworkSwitchMonitor(this) { change -> onNetworkSwitched(change) }.also { it.start() }
}

internal fun MainActivity.stopNetworkSwitchMonitor() {
    networkSwitchMonitor?.stop()
    networkSwitchMonitor = null
}

private fun MainActivity.onNetworkSwitched(@Suppress("UNUSED_PARAMETER") change: NetworkSwitchMonitor.Change) {
    if (isFinishing || isDestroyed) return
    // Fire offline -> online in every page so web apps drop and re-open their connections.
    tabManager.tabs.forEach { tab -> runCatching { tab.webView.setNetworkAvailable(false) } }
    window.decorView.postDelayed({
        tabManager.tabs.forEach { tab -> runCatching { tab.webView.setNetworkAvailable(true) } }
    }, 300)

    if (!prefs.getBoolean(BrowserEngineRestart.PREF_NETWORK_SWITCH_RECOVERY, BrowserEngineRestart.DEFAULT_NETWORK_SWITCH_RECOVERY)) return
    // Silent recovery: no UI. In the foreground restart right away; in the background restart on return.
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        if (canRestartEngineQuietly()) restartBrowserEngine()
    } else {
        pendingEngineRestart = true
    }
}

/** Called from onStart: after a network switch that happened while we were in the background, restart quietly. */
internal fun MainActivity.consumePendingEngineRestart(): Boolean {
    if (!pendingEngineRestart) return false
    pendingEngineRestart = false
    if (!canRestartEngineQuietly()) return false
    restartBrowserEngine()
    return true
}

/** A quiet restart must not lose anything that lives only in this process. */
internal fun MainActivity.canRestartEngineQuietly(): Boolean =
    tabManager.tabs.none { it.isIncognito } &&
        ClintDownloadManager.activeCount() == 0 &&
        tabManager.tabs.none { backgroundPlaybackTracker.isPlaying(it.id) }

internal fun MainActivity.restartBrowserEngine() {
    saveTabs(blocking = true)
    BrowserEngineRestart.relaunch(this)
}

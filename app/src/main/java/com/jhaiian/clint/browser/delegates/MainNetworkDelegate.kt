package com.jhaiian.clint.browser.delegates

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.media.BackgroundPlayback
import com.jhaiian.clint.browser.network.NetworkSwitchMonitor
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.ui.showClintSnackbar

private const val NETWORK_REAVAILABLE_DELAY_MS = 600L

private val networkHandler = Handler(Looper.getMainLooper())

internal fun MainActivity.attachNetworkSwitchMonitor() {
    NetworkSwitchMonitor.listener = { change -> onNetworkChanged(change) }
}

internal fun MainActivity.detachNetworkSwitchMonitor() {
    NetworkSwitchMonitor.listener = null
    networkHandler.removeCallbacksAndMessages(null)
}

internal fun MainActivity.consumeEngineRestartIntent(source: Intent?): Boolean {
    if (source?.getBooleanExtra(MainActivity.EXTRA_ENGINE_RESTART_REQUEST, false) != true) return false
    NetworkSwitchMonitor.requestRestart(NetworkSwitchMonitor.RestartRequest.MANUAL)
    return true
}

internal fun MainActivity.performPendingEngineRestart(foreground: Boolean = true) {
    val request = NetworkSwitchMonitor.restartRequest
    if (request == NetworkSwitchMonitor.RestartRequest.NONE || !foreground) return
    NetworkSwitchMonitor.restartRequest = NetworkSwitchMonitor.RestartRequest.NONE
    if (request == NetworkSwitchMonitor.RestartRequest.AUTO && !NetworkSwitchMonitor.isAutoRecoveryEnabled(prefs)) return
    if (engineRestartBlocked()) {
        if (request == NetworkSwitchMonitor.RestartRequest.MANUAL) {
            showClintSnackbar(getString(R.string.network_restart_blocked))
        }
        return
    }
    restartEngine()
}

private fun MainActivity.setTabsNetworkAvailable(available: Boolean) {
    tabManager.tabs.forEach { tab -> runCatching { tab.webView.setNetworkAvailable(available) } }
}

private fun MainActivity.onNetworkChanged(change: NetworkSwitchMonitor.Change) {
    if (isFinishing || isDestroyed) return
    when (change) {
        NetworkSwitchMonitor.Change.LOST -> setTabsNetworkAvailable(false)
        NetworkSwitchMonitor.Change.RESTORED -> setTabsNetworkAvailable(true)
        NetworkSwitchMonitor.Change.SWITCHED -> {
            setTabsNetworkAvailable(false)
            networkHandler.postDelayed({
                if (!isFinishing && !isDestroyed) setTabsNetworkAvailable(true)
            }, NETWORK_REAVAILABLE_DELAY_MS)
            NetworkSwitchMonitor.requestRestart(NetworkSwitchMonitor.RestartRequest.AUTO)
            performPendingEngineRestart(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
    }
}

private fun MainActivity.engineRestartBlocked(): Boolean =
    tabManager.tabs.any { it.isIncognito } ||
        ClintDownloadManager.activeCount() > 0 ||
        BackgroundPlayback.session.value != null ||
        isVideoFullscreen()

private fun MainActivity.restartEngine() {
    NetworkSwitchMonitor.engineRestarting = true
    saveTabs(blocking = true)
    runCatching { android.webkit.CookieManager.getInstance().flush() }
    val relaunch = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(MainActivity.EXTRA_ENGINE_RESTART, true)
    tabManager.activeTab
        ?.let { tabManager.effectiveShortcutId(it) }
        ?.let { relaunch.putExtra(MainActivity.EXTRA_SHORTCUT_ID, it) }
    val started = runCatching { startActivity(relaunch) }.isSuccess
    if (!started) {
        NetworkSwitchMonitor.engineRestarting = false
        return
    }
    android.os.Process.killProcess(android.os.Process.myPid())
}

package com.jhaiian.clint.browser.network

import android.app.Activity
import android.content.Intent
import com.jhaiian.clint.browser.MainActivity

/**
 * Restarts the app process so WebView's network stack (connection pools, QUIC sessions, host cache)
 * starts fresh. Tabs are restored from the saved session on the next launch.
 */
object BrowserEngineRestart {
    const val EXTRA_ENGINE_RESTART = "extra_engine_restart"

    /** Recover automatically after a network switch (snackbar in foreground, quiet restart on return from background). */
    const val PREF_NETWORK_SWITCH_RECOVERY = "network_switch_recovery"
    const val DEFAULT_NETWORK_SWITCH_RECOVERY = true

    fun relaunch(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_ENGINE_RESTART, true)
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

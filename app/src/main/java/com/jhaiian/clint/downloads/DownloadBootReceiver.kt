package com.jhaiian.clint.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // init() launches its loading/registration work on ClintDownloadManager's own
            // application-scoped coroutine, which would otherwise keep running after onReceive
            // returns with no guarantee the process stays alive long enough to finish. goAsync()
            // extends the receiver's lifetime, and completing it once the returned Job finishes is
            // the standard pattern for bridging a BroadcastReceiver to background coroutine work.
            val pendingResult = goAsync()
            val job = ClintDownloadManager.init(context)
            job.invokeOnCompletion { pendingResult.finish() }
        }
    }
}

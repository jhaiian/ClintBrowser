package com.jhaiian.clint.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when a single download's [DownloadCustomScheduleMonitor]-armed alarm reaches its scheduled
 * start time. The process may not otherwise be running at that moment, so this first ensures
 * [ClintDownloadManager] has loaded its persisted state before resuming the download, the same
 * ordering [DownloadScheduleWorker] and [DownloadBootReceiver] use.
 *
 * Unlike [DownloadScheduleMonitor]'s window checks, this alarm is armed with
 * [android.app.AlarmManager.setExactAndAllowWhileIdle] whenever the user has granted exact-alarm
 * permission (see [DownloadCustomScheduleMonitor.canScheduleExact]), which is itself one of the
 * documented exemptions from the Android 12+ background foreground-service-start restriction — a
 * user-requested exact alarm is allowed to start a foreground service even from the background. So
 * this one didn't need to move to WorkManager; it only falls back to the same failure mode
 * [DownloadScheduleWorker] fixes if exact-alarm permission is denied, in which case a several-minute
 * slip is the documented, accepted tradeoff (see [DownloadCustomScheduleMonitor.schedule]).
 */
class DownloadCustomScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id == -1) return
        val pendingResult = goAsync()
        val job = ClintDownloadManager.init(context)
        job.invokeOnCompletion {
            ClintDownloadManager.resume(context, id)
            pendingResult.finish()
        }
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}

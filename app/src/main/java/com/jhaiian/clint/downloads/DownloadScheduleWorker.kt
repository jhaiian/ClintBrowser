package com.jhaiian.clint.downloads

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters

/**
 * Fires when [DownloadScheduleMonitor]'s next window boundary is reached, and reconciles queued
 * downloads against it. A plain inexact [android.app.AlarmManager] alarm firing a
 * [android.content.BroadcastReceiver] is not one of Android 12+'s documented exemptions from the
 * "no starting foreground services from the background" restriction, so calling
 * [DownloadForegroundService.start] from such a receiver while the app is backgrounded can throw
 * silently and leave nothing running. [CoroutineWorker.setForeground] is WorkManager's own
 * sanctioned path to that same foreground state, so by the time [reconcile] asks
 * [DownloadForegroundService] to start, this process is already legitimately foregrounded and that
 * nested start succeeds.
 *
 * The process may not otherwise be running at this point, so this first ensures [ClintDownloadManager]
 * has loaded its persisted state before reconciling, the same ordering [DownloadBootReceiver] uses.
 */
class DownloadScheduleWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        setForeground(foregroundInfo())
        ClintDownloadManager.init(applicationContext).join()
        DownloadScheduleMonitor.reconcile(applicationContext)
        return Result.success()
    }

    /** Reuses [DownloadForegroundService]'s own notification id so this doesn't flash a second, separate one. */
    private fun foregroundInfo(): ForegroundInfo {
        ClintDownloadManager.createNotificationChannel(applicationContext)
        val notification = DownloadNotificationHelper.buildSummaryNotification(applicationContext, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(DownloadForegroundService.FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(DownloadForegroundService.FOREGROUND_ID, notification)
        }
    }
}

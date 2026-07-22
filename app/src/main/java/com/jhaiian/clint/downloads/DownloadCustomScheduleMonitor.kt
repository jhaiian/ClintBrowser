package com.jhaiian.clint.downloads

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Arms and fires the per-download alarms used by "Schedule This Download". Unlike
 * [DownloadScheduleMonitor], which pauses/resumes every download around a single recurring daily
 * window, this fires a one-shot [AlarmManager] alarm for a single download at the exact date/time
 * the user picked, and that download ignores the global window entirely once it has one of these
 * (see the `scheduledStartAtMillis > 0` checks in [ClintDownloadManager]).
 */
internal object DownloadCustomScheduleMonitor {

    /** Offset so per-download request codes never collide with [DownloadScheduleMonitor]'s single alarm. */
    private const val REQUEST_CODE_BASE = 80_000

    private fun pendingIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, DownloadCustomScheduleReceiver::class.java)
            .putExtra(DownloadCustomScheduleReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * True if this alarm can fire at (rather than merely around) [atMillis]. Below API 31 exact alarms
     * never needed a special permission; from API 31 on, [AlarmManager.canScheduleExactAlarms] reflects
     * whether the user has granted "Alarms & reminders" for this app (see [canRequestExactAlarmPermission]).
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /** Arms (or replaces) the alarm that resumes download [id] once [atMillis] is reached. */
    fun schedule(context: Context, id: Int, atMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(context, id)
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        } else {
            // No exact-alarm permission: the OS may defer this by several minutes or more, but this is
            // still strictly better than not scheduling it at all.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        }
    }

    /** Cancels any pending alarm for [id], e.g. because it was paused, resumed early, or removed. */
    fun cancel(context: Context, id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(context, id))
    }

    /**
     * Called once after downloads are loaded from disk on process start. Re-arms an alarm for every
     * download still waiting on its custom schedule, or resumes it immediately if its time already
     * passed while the app wasn't running.
     */
    fun rearmAll(context: Context) {
        ClintDownloadManager.downloadsFlow.value
            .filter { it.status == DownloadStatus.PAUSED && it.waitingForCustomSchedule && it.scheduledStartAtMillis > 0L }
            .forEach { item ->
                if (item.scheduledStartAtMillis <= System.currentTimeMillis()) {
                    ClintDownloadManager.resume(context, item.id)
                } else {
                    schedule(context, item.id, item.scheduledStartAtMillis)
                }
            }
    }
}

package com.jhaiian.clint.downloads

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Enforces an optional daily time window during which downloads are allowed to run. Unlike
 * [DownloadNetworkMonitor], nothing needs to stay alive continuously to notice a boundary
 * crossing, so the next one is armed as a single deferred [DownloadScheduleWorker] run that
 * reschedules itself when it fires. WorkManager (rather than a raw AlarmManager alarm) is what
 * lets that worker legitimately turn into a foreground service on Android 12+ even while the app
 * is fully backgrounded; see [DownloadScheduleWorker]'s doc for why the plain-alarm version of
 * this could silently stop auto-resuming downloads.
 */
internal object DownloadScheduleMonitor {

    private const val UNIQUE_WORK_NAME = "download_schedule_check"

    /** Ids currently paused specifically by the schedule, mirroring [DownloadNetworkMonitor.unmeteredPausedIds]. */
    internal val scheduleWaitingIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun isEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(
            DownloadSettingsFragment.PREF_SCHEDULE_ENABLED, DownloadSettingsFragment.DEFAULT_SCHEDULE_ENABLED
        )
    }

    private fun windowMinutes(context: Context): Pair<Int, Int> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val start = prefs.getInt(
            DownloadSettingsFragment.PREF_SCHEDULE_START_MINUTES, DownloadSettingsFragment.DEFAULT_SCHEDULE_START_MINUTES
        )
        val end = prefs.getInt(
            DownloadSettingsFragment.PREF_SCHEDULE_END_MINUTES, DownloadSettingsFragment.DEFAULT_SCHEDULE_END_MINUTES
        )
        return start to end
    }

    /**
     * Whether downloads are currently allowed to run. Always true when the schedule is off. An
     * end time earlier than the start time (e.g. 23:00 to 07:00) is treated as an overnight window
     * that wraps past midnight rather than an empty one.
     */
    fun isWithinWindow(context: Context): Boolean {
        if (!isEnabled(context)) return true
        val (start, end) = windowMinutes(context)
        if (start == end) return true
        val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        return if (start < end) now in start until end else now >= start || now < end
    }

    /** Milliseconds until [isWithinWindow] would next flip, rounded up to the start of that minute. */
    private fun millisUntilNextBoundary(context: Context): Long {
        val (start, end) = windowMinutes(context)
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val boundary = if (isWithinWindow(context)) end else start
        var deltaMinutes = boundary - nowMinutes
        if (deltaMinutes <= 0) deltaMinutes += 24 * 60
        val secondsIntoMinute = cal.get(Calendar.SECOND)
        return (deltaMinutes * 60_000L - secondsIntoMinute * 1000L).coerceAtLeast(1_000L)
    }

    /**
     * (Re)arms the single work request driving the next [reconcile] call, replacing any previously
     * scheduled one. When this runs at the end of [reconcile] inside [DownloadScheduleWorker]'s own
     * `doWork()`, the REPLACE policy cancels that same in-flight work, which is fine here because
     * [reconcile] has already applied every state change it needs to by this point.
     */
    fun scheduleNextCheck(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (!isEnabled(context)) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val (start, end) = windowMinutes(context)
        if (start == end) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<DownloadScheduleWorker>()
            .setInitialDelay(millisUntilNextBoundary(context), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Applies the current window to queued and active downloads, then arms the next check. Safe to call anytime. */
    fun reconcile(context: Context) {
        if (isWithinWindow(context)) {
            val toResume = ClintDownloadManager.downloadsFlow.value
                .filter { it.status == DownloadStatus.PAUSED && it.waitingForSchedule }
                .map { it.id }
            scheduleWaitingIds.clear()
            toResume.forEach { ClintDownloadManager.resume(context, it) }
            ClintDownloadManager.tryDequeueNext(context)
        } else {
            val toPause = ClintDownloadManager.downloadsFlow.value.filter {
                (it.status == DownloadStatus.QUEUED || it.status in DownloadStatus.ACTIVELY_WORKING) &&
                    it.scheduledStartAtMillis == 0L
            }
            toPause.forEach { item ->
                ClintDownloadManager.updateItem(item.id) { it.copy(waitingForSchedule = true) }
                scheduleWaitingIds.add(item.id)
                ClintDownloadManager.pause(context, item.id)
            }
        }
        scheduleNextCheck(context)
    }

    /** Called right after the schedule preference changes, so the new window takes effect immediately. */
    fun onScheduleChanged(context: Context) {
        reconcile(context)
    }
}

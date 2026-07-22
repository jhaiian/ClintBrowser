package com.jhaiian.clint.downloads

import com.jhaiian.clint.util.formatFileSize

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment

internal object DownloadNotificationHelper {

    /**
     * Notifications sharing this key collapse into one expandable group in the notification
     * shade, headed by the group-summary notification built in [buildSummaryNotification].
     */
    const val DOWNLOAD_GROUP_KEY = "com.jhaiian.clint.downloads.GROUP"

    fun createNotificationChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val progressChannel = NotificationChannel(
            ClintDownloadManager.CHANNEL_ID,
            context.getString(R.string.download_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.download_notification_channel_desc)
            setSound(null, null)
        }
        nm.createNotificationChannel(progressChannel)
        val eventChannel = NotificationChannel(
            ClintDownloadManager.EVENT_CHANNEL_ID,
            context.getString(R.string.download_event_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.download_event_notification_channel_desc)
        }
        nm.createNotificationChannel(eventChannel)
    }

    /**
     * Serves as both the required foreground-service notification and the group summary for the
     * per-download notifications below it, so simultaneous downloads collapse under one entry in
     * the notification shade instead of each spawning its own top-level entry.
     */
    fun buildSummaryNotification(context: Context, activeCount: Int): Notification {
        val downloadsIntent = Intent(context, DownloadsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val downloadsPi = PendingIntent.getActivity(
            context, 0, downloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (activeCount > 0)
            context.resources.getQuantityString(R.plurals.download_notification_group_summary, activeCount, activeCount)
        else
            context.getString(R.string.download_foreground_notification_text)
        return NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.download_foreground_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(downloadsPi)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setGroupSummary(true)
            .build()
    }

    fun showQueuedNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_status_queued))
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, false)
            .addAction(0, context.getString(R.string.action_pause), pausePendingIntent(context, item.id))
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showProgressNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val statusText: String
        val metaText: String?
        val indeterminate: Boolean
        val progress: Int
        val showPause: Boolean

        when (item.status) {
            DownloadStatus.CONNECTING -> {
                val sizeStr = if (item.totalBytes > 0) formatFileSize(item.totalBytes) else null
                statusText = sizeStr ?: context.getString(R.string.download_status_connecting)
                metaText = if (sizeStr != null) context.getString(R.string.download_status_connecting) else null
                indeterminate = true
                progress = 0
                showPause = true
            }
            DownloadStatus.RETRYING -> {
                val pct = item.progressPercent
                val sizeStr = when {
                    item.bytesDownloaded > 0 && pct >= 0 && item.totalBytes > 0 ->
                        context.getString(R.string.download_status_progress, pct, formatFileSize(item.bytesDownloaded), formatFileSize(item.totalBytes))
                    item.bytesDownloaded > 0 && pct >= 0 ->
                        context.getString(R.string.download_status_progress_unknown_total, pct, formatFileSize(item.bytesDownloaded))
                    item.bytesDownloaded > 0 ->
                        context.getString(R.string.download_status_progress_indeterminate, formatFileSize(item.bytesDownloaded))
                    item.totalBytes > 0 -> formatFileSize(item.totalBytes)
                    else -> null
                }
                val retryStr = if (item.retryDelaySec > 0)
                    context.getString(R.string.download_status_retrying_in, item.retryDelaySec)
                else
                    context.getString(R.string.download_status_retrying)
                statusText = sizeStr ?: retryStr
                metaText = if (sizeStr != null) retryStr else null
                indeterminate = true
                progress = 0
                showPause = true
            }
            else -> {
                val pct = item.progressPercent
                val downloaded = formatFileSize(item.bytesDownloaded)
                statusText = if (pct >= 0) {
                    if (item.totalBytes > 0)
                        context.getString(R.string.download_status_progress, pct, downloaded, formatFileSize(item.totalBytes))
                    else
                        context.getString(R.string.download_status_progress_unknown_total, pct, downloaded)
                } else {
                    context.getString(R.string.download_status_progress_indeterminate, downloaded)
                }
                metaText = buildSpeedEtaText(context, item)
                indeterminate = pct < 0
                progress = pct.coerceAtLeast(0)
                showPause = item.resumable
            }
        }

        val downloadsIntent = Intent(context, DownloadsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val downloadsPi = PendingIntent.getActivity(
            context, item.id + 50000, downloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (metaText != null) "$statusText  \u2022  $metaText" else statusText
        val builder = NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progress, indeterminate)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setContentIntent(downloadsPi)

        if (showPause) {
            builder.addAction(0, context.getString(R.string.action_pause), pausePendingIntent(context, item.id))
        }

        nm.notify(item.id, builder.build())
    }

    fun showAllocationNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pct = item.allocationProgress
        val allocStr = context.getString(R.string.download_status_allocating, pct)
        val contentText = if (item.totalBytes > 0)
            "${formatFileSize(item.totalBytes)}  \u2022  $allocStr"
        else
            allocStr
        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct, pct == 0)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showCopyingTempNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val progress = item.copyProgress
        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_copying_temp_notification, progress))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progress, progress == 0)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showDeletingTempNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_deleting_temp_notification))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showPausedNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pct = item.progressPercent
        val progressText = buildPausedProgressText(context, item)
        val elapsedSec = item.activeElapsedMs / 1000L
        val pausedLabel = context.getString(R.string.download_notification_paused)
        val metaLabel = if (elapsedSec >= 1L) "$pausedLabel  \u2022  ${formatElapsed(elapsedSec)}" else pausedLabel
        val contentText = if (progressText != null) "$progressText  \u2022  $metaLabel" else metaLabel

        val resumePi = PendingIntent.getBroadcast(
            context, item.id + 50000,
            Intent(context, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_RESUME
                putExtra(DownloadActionReceiver.EXTRA_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct.coerceAtLeast(0), false)
            .addAction(0, context.getString(R.string.action_resume), resumePi)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showWaitingUnmeteredNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pct = item.progressPercent
        val progressText = buildPausedProgressText(context, item)
        val elapsedSec = item.activeElapsedMs / 1000L
        val waitingLabel = context.getString(R.string.download_notification_waiting_unmetered)
        val metaLabel = if (elapsedSec >= 1L) "$waitingLabel  \u2022  ${formatElapsed(elapsedSec)}" else waitingLabel
        val contentText = if (progressText != null) "$progressText  \u2022  $metaLabel" else metaLabel

        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct.coerceAtLeast(0), false)
            .addAction(0, context.getString(R.string.action_pause), pausePendingIntent(context, item.id))
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showWaitingNetworkNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pct = item.progressPercent
        val progressText = buildPausedProgressText(context, item)
        val elapsedSec = item.activeElapsedMs / 1000L
        val waitingLabel = context.getString(R.string.download_notification_waiting_network)
        val metaLabel = if (elapsedSec >= 1L) "$waitingLabel  \u2022  ${formatElapsed(elapsedSec)}" else waitingLabel
        val contentText = if (progressText != null) "$progressText  \u2022  $metaLabel" else metaLabel

        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct.coerceAtLeast(0), false)
            .addAction(0, context.getString(R.string.action_pause), pausePendingIntent(context, item.id))
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showWaitingScheduleNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pct = item.progressPercent
        val progressText = buildPausedProgressText(context, item)
        val elapsedSec = item.activeElapsedMs / 1000L
        val waitingLabel = context.getString(R.string.download_notification_waiting_schedule)
        val metaLabel = if (elapsedSec >= 1L) "$waitingLabel  \u2022  ${formatElapsed(elapsedSec)}" else waitingLabel
        val contentText = if (progressText != null) "$progressText  \u2022  $metaLabel" else metaLabel

        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct.coerceAtLeast(0), false)
            .addAction(0, context.getString(R.string.action_pause), pausePendingIntent(context, item.id))
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showWaitingCustomScheduleNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pct = item.progressPercent
        val progressText = buildPausedProgressText(context, item)
        val waitingLabel = context.getString(
            R.string.download_notification_waiting_custom_schedule,
            formatScheduledDateTime(context, item.scheduledStartAtMillis)
        )
        val contentText = if (progressText != null) "$progressText  \u2022  $waitingLabel" else waitingLabel

        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct.coerceAtLeast(0), false)
            .addAction(0, context.getString(R.string.action_pause), pausePendingIntent(context, item.id))
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showCompleteNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (item.file == null && item.contentUri == null) {
            nm.cancel(item.id)
            return
        }

        nm.cancel(item.id)

        if (!isPushEnabled(context)) return

        val openIntent = Intent(context, DownloadsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(DownloadsActivity.EXTRA_OPEN_ID, item.id)
        }
        val openPi = PendingIntent.getActivity(
            context, item.id + 10000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val downloadsIntent = Intent(context, DownloadsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val downloadsPi = PendingIntent.getActivity(
            context, item.id + 20000, downloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationCompat.Builder(context, ClintDownloadManager.EVENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_notification_complete))
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(downloadsPi)
            .addAction(0, context.getString(R.string.action_open), openPi)
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showFailedNotification(context: Context, item: DownloadItem) {
        if (!isPushEnabled(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val downloadsIntent = Intent(context, DownloadsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val downloadsPi = PendingIntent.getActivity(
            context, item.id + 30000, downloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(context, ClintDownloadManager.EVENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_notification_failed))
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(downloadsPi)
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showRetryingNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(item.id)
        if (!isPushEnabled(context)) return
        val downloadsIntent = Intent(context, DownloadsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val downloadsPi = PendingIntent.getActivity(
            context, item.id + 60000, downloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val retryText = if (item.retryDelaySec > 0)
            context.getString(R.string.download_notification_retrying_in, item.retryDelaySec)
        else
            context.getString(R.string.download_notification_retrying)
        NotificationCompat.Builder(context, ClintDownloadManager.EVENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(item.filename)
            .setContentText(retryText)
            .setGroup(DOWNLOAD_GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(downloadsPi)
            .build()
            .let { nm.notify(item.id, it) }
    }

    private fun isPushEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(DownloadSettingsFragment.PREF_PUSH_NOTIFICATIONS, DownloadSettingsFragment.DEFAULT_PUSH_NOTIFICATIONS)

    private fun pausePendingIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, id + 40000,
            Intent(context, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_PAUSE
                putExtra(DownloadActionReceiver.EXTRA_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildPausedProgressText(context: Context, item: DownloadItem): String? {
        val pct = item.progressPercent
        return when {
            pct >= 0 && item.totalBytes > 0 ->
                context.getString(R.string.download_status_progress, pct, formatFileSize(item.bytesDownloaded), formatFileSize(item.totalBytes))
            pct >= 0 ->
                context.getString(R.string.download_status_progress_unknown_total, pct, formatFileSize(item.bytesDownloaded))
            item.bytesDownloaded > 0 ->
                context.getString(R.string.download_status_progress_indeterminate, formatFileSize(item.bytesDownloaded))
            else -> null
        }
    }

    private fun buildSpeedEtaText(context: Context, item: DownloadItem): String? {
        val speed = item.speedBytesPerSec
        val elapsedMs = item.activeElapsedMs +
                if (item.activeStartedAt > 0L) System.currentTimeMillis() - item.activeStartedAt else 0L
        val elapsedStr = if (elapsedMs >= 1000L) formatElapsed(elapsedMs / 1000L) else null
        if (speed <= 0L) return elapsedStr
        val remaining = item.totalBytes - item.bytesDownloaded
        val speedEta = if (item.totalBytes <= 0L || remaining <= 0L)
            context.getString(R.string.download_speed_only, formatFileSize(speed))
        else {
            // The ETA uses the average speed rather than the current reading above, since the
            // current speed alone can swing the remaining-time estimate wildly on a connection
            // that briefly speeds up or stalls.
            val etaSpeed = item.averageSpeedBytesPerSec().takeIf { it > 0L } ?: speed
            context.getString(R.string.download_speed_eta, formatFileSize(speed), formatEta(context, remaining / etaSpeed))
        }
        return if (elapsedStr != null) "$speedEta  \u2022  $elapsedStr" else speedEta
    }

    private fun formatElapsed(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    private fun formatEta(context: Context, seconds: Long): String = when {
        seconds < 60 -> context.getString(R.string.download_eta_seconds, seconds)
        seconds < 3600 -> context.getString(R.string.download_eta_minutes, seconds / 60, seconds % 60)
        else -> context.getString(R.string.download_eta_hours, seconds / 3600, (seconds % 3600) / 60)
    }

}

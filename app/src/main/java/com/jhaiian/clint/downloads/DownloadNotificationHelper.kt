package com.jhaiian.clint.downloads

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

    fun showQueuedNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_status_queued))
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
                val sizeStr = if (item.totalBytes > 0) formatBytes(item.totalBytes) else null
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
                        context.getString(R.string.download_status_progress, pct, formatBytes(item.bytesDownloaded), formatBytes(item.totalBytes))
                    item.bytesDownloaded > 0 && pct >= 0 ->
                        context.getString(R.string.download_status_progress_unknown_total, pct, formatBytes(item.bytesDownloaded))
                    item.bytesDownloaded > 0 ->
                        context.getString(R.string.download_status_progress_indeterminate, formatBytes(item.bytesDownloaded))
                    item.totalBytes > 0 -> formatBytes(item.totalBytes)
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
                val downloaded = formatBytes(item.bytesDownloaded)
                statusText = if (pct >= 0) {
                    if (item.totalBytes > 0)
                        context.getString(R.string.download_status_progress, pct, downloaded, formatBytes(item.totalBytes))
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

        val contentText = if (metaText != null) "$statusText  \u2022  $metaText" else statusText
        val builder = NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progress, indeterminate)

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
            "${formatBytes(item.totalBytes)}  \u2022  $allocStr"
        else
            allocStr
        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct, pct == 0)
            .build()
            .let { nm.notify(item.id, it) }
    }

    fun showMovingNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val progress = item.moveProgress
        NotificationCompat.Builder(context, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_moving_notification, progress))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progress, progress == 0)
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
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
            .setContentTitle(item.filename)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct.coerceAtLeast(0), false)
            .addAction(0, context.getString(R.string.action_resume), resumePi)
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
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
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
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
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
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_notification_complete))
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
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_notification_failed))
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
            .setSmallIcon(R.drawable.ic_keyboard_double_arrow_down_24)
            .setContentTitle(item.filename)
            .setContentText(retryText)
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
                context.getString(R.string.download_status_progress, pct, formatBytes(item.bytesDownloaded), formatBytes(item.totalBytes))
            pct >= 0 ->
                context.getString(R.string.download_status_progress_unknown_total, pct, formatBytes(item.bytesDownloaded))
            item.bytesDownloaded > 0 ->
                context.getString(R.string.download_status_progress_indeterminate, formatBytes(item.bytesDownloaded))
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
            context.getString(R.string.download_speed_only, formatBytes(speed))
        else
            context.getString(R.string.download_speed_eta, formatBytes(speed), formatEta(context, remaining / speed))
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

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

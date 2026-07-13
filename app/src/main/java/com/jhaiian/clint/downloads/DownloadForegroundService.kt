package com.jhaiian.clint.downloads

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.jhaiian.clint.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while any download is actively transferring. [LifecycleService] provides
 * [lifecycleScope], which replaces the previous [android.os.Handler]-based 1-second polling loop
 * with a direct reactive collection of [ClintDownloadManager.downloadsFlow]: the service now reacts
 * to state changes as they happen rather than checking on a fixed interval. [collectLatest] with a
 * trailing [delay] reproduces the old "wait a second before actually stopping" debounce, so a
 * download finishing right as the next one starts doesn't cause a stop-then-immediately-restart.
 */
class DownloadForegroundService : LifecycleService() {

    companion object {
        private const val FOREGROUND_ID = 9001

        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ClintDownloadManager.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = buildPlaceholderNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_ID, notification)
        }

        lifecycleScope.launch {
            ClintDownloadManager.downloadsFlow
                .map { list -> list.any { it.status in DownloadStatus.ACTIVELY_WORKING } }
                .distinctUntilChanged()
                .collectLatest { hasActive ->
                    if (!hasActive) {
                        delay(1000)
                        stopSelf()
                    }
                }
        }

        return START_STICKY
    }

    private fun buildPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, ClintDownloadManager.CHANNEL_ID)
            .setContentTitle(getString(R.string.download_foreground_notification_title))
            .setContentText(getString(R.string.download_foreground_notification_text))
            .setSmallIcon(R.drawable.ic_notification_24)
            .setOngoing(true)
            .build()
    }
}

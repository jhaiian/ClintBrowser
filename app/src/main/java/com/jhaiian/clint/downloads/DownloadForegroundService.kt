package com.jhaiian.clint.downloads

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.jhaiian.clint.R

class DownloadForegroundService : Service() {

    companion object {
        private const val FOREGROUND_ID = 999_999
        private const val WAKELOCK_TAG = "ClintBrowser:DownloadWakeLock"

        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!hasActiveDownloads()) {
                stopSelf()
            } else {
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notification = NotificationCompat.Builder(this, ClintDownloadManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.download_foreground_notification_title))
            .setContentText(getString(R.string.download_foreground_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(monitorRunnable)
        handler.postDelayed(monitorRunnable, 1_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(monitorRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasActiveDownloads(): Boolean {
        return synchronized(ClintDownloadManager.downloads) {
            ClintDownloadManager.downloads.any { item ->
                item.status == DownloadStatus.DOWNLOADING ||
                item.status == DownloadStatus.CONNECTING ||
                item.status == DownloadStatus.MOVING ||
                item.status == DownloadStatus.RETRYING ||
                item.status == DownloadStatus.ALLOCATING
            }
        }
    }
}


package com.jhaiian.clint.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.app.NotificationCompat
import com.jhaiian.clint.R
import okhttp3.OkHttpClient
import okhttp3.Request
import android.os.Handler
import android.os.Looper
import com.jhaiian.clint.ui.ClintToast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

object ClintDownloadManager {

    enum class DownloadStatus { DOWNLOADING, COMPLETE, FAILED, CANCELLED }

    data class DownloadItem(
        val id: Int,
        val url: String,
        var filename: String,
        val userAgent: String,
        val referer: String = "",
        val cookies: String = "",
        var bytesDownloaded: Long = 0L,
        var totalBytes: Long = -1L,
        var status: DownloadStatus = DownloadStatus.DOWNLOADING,
        var file: File? = null,
        var errorMessage: String? = null
    ) {
        val progressPercent: Int
            get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else -1
    }

    private val CHANNEL_ID = "clint_downloads"
    private val PREFS_NAME = "clint_downloads_prefs"
    private val KEY_DOWNLOADS = "saved_downloads"
    private val executor = Executors.newFixedThreadPool(4)
    private val idCounter = AtomicInteger(1)
    private val futures = mutableMapOf<Int, Future<*>>()
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false

    val downloads = mutableListOf<DownloadItem>()
    var onDownloadsChanged: (() -> Unit)? = null

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.download_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.download_notification_channel_desc)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!initialized) {
            initialized = true
            loadDownloads()
        }
    }

    private fun loadDownloads() {
        val ctx = appContext ?: return
        val json = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOADS, null) ?: return
        try {
            val arr = JSONArray(json)
            val loaded = mutableListOf<DownloadItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val status = DownloadStatus.valueOf(obj.getString("status"))
                val item = DownloadItem(
                    id = obj.getInt("id"),
                    url = obj.getString("url"),
                    filename = obj.getString("filename"),
                    userAgent = obj.getString("userAgent"),
                    referer = obj.optString("referer"),
                    cookies = obj.optString("cookies"),
                    bytesDownloaded = obj.getLong("bytesDownloaded"),
                    totalBytes = obj.getLong("totalBytes"),
                    status = status,
                    errorMessage = obj.optString("errorMessage").takeIf { it.isNotEmpty() }
                )
                val path = obj.optString("filePath")
                if (path.isNotEmpty()) item.file = File(path)
                loaded.add(item)
            }
            synchronized(downloads) { downloads.addAll(loaded) }
            loaded.maxOfOrNull { it.id }?.let { max ->
                if (max >= idCounter.get()) idCounter.set(max + 1)
            }
        } catch (_: Exception) {}
    }

    private fun saveDownloads() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        synchronized(downloads) {
            downloads.forEach { item ->
                if (item.status == DownloadStatus.DOWNLOADING) return@forEach
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("filename", item.filename)
                    put("userAgent", item.userAgent)
                    put("referer", item.referer)
                    put("cookies", item.cookies)
                    put("bytesDownloaded", item.bytesDownloaded)
                    put("totalBytes", item.totalBytes)
                    put("status", item.status.name)
                    item.errorMessage?.let { put("errorMessage", it) }
                    item.file?.let { put("filePath", it.absolutePath) }
                }
                arr.put(obj)
            }
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DOWNLOADS, arr.toString()).apply()
    }

    fun enqueue(context: Context, url: String, filename: String, userAgent: String, referer: String = "", cookies: String = "") {
        val id = idCounter.getAndIncrement()
        val item = DownloadItem(id = id, url = url, filename = filename, userAgent = userAgent, referer = referer, cookies = cookies)
        synchronized(downloads) { downloads.add(0, item) }
        onDownloadsChanged?.invoke()
        showProgressNotification(context, item)

        val future = executor.submit {
            runDownload(context, item)
        }
        futures[id] = future
    }

    fun cancel(context: Context, id: Int) {
        futures[id]?.cancel(true)
        futures.remove(id)
        synchronized(downloads) {
            downloads.find { it.id == id }?.let { it.status = DownloadStatus.CANCELLED }
        }
        context.getSystemService(NotificationManager::class.java).cancel(id)
        saveDownloads()
        onDownloadsChanged?.invoke()
    }

    fun clearCompleted() {
        synchronized(downloads) {
            downloads.removeAll { it.status != DownloadStatus.DOWNLOADING }
        }
        saveDownloads()
        onDownloadsChanged?.invoke()
    }

    private fun runDownload(context: Context, item: DownloadItem) {
        try {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder()
                .url(item.url)
                .header("User-Agent", item.userAgent)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .apply {
                    if (item.referer.isNotEmpty()) header("Referer", item.referer)
                    if (item.cookies.isNotEmpty()) header("Cookie", item.cookies)
                }
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                fail(context, item, "Server error ${response.code}")
                return
            }

            val body = response.body ?: run { fail(context, item, "Empty response"); return }
            item.totalBytes = body.contentLength()

            val destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            destDir.mkdirs()
            val destFile = uniqueFile(destDir, item.filename)
            item.filename = destFile.name  
            item.file = destFile
            val ctx = appContext
            if (ctx != null) {
                val msg = ctx.getString(R.string.toast_downloading, item.filename)
                mainHandler.post { ClintToast.show(ctx, msg, R.drawable.ic_download_24) }
            }

            val buffer = ByteArray(8192)
            var lastNotifyBytes = 0L
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            destFile.delete()
                            item.status = DownloadStatus.CANCELLED
                            context.getSystemService(NotificationManager::class.java).cancel(item.id)
                            saveDownloads()
                            onDownloadsChanged?.invoke()
                            return
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        item.bytesDownloaded += read
                        if (item.bytesDownloaded - lastNotifyBytes > 8192) {
                            lastNotifyBytes = item.bytesDownloaded
                            showProgressNotification(context, item)
                            onDownloadsChanged?.invoke()
                        }
                    }
                }
            }

            item.status = DownloadStatus.COMPLETE
            saveDownloads()
            onDownloadsChanged?.invoke()
            showCompleteNotification(context, item)
        } catch (e: Exception) {
            if (item.status != DownloadStatus.CANCELLED) {
                fail(context, item, e.message ?: "Unknown error")
            }
        }
    }

    private fun fail(context: Context, item: DownloadItem, msg: String) {
        item.status = DownloadStatus.FAILED
        item.errorMessage = msg
        saveDownloads()
        onDownloadsChanged?.invoke()
        showFailedNotification(context, item)
    }

    private fun showProgressNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val progress = item.progressPercent
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.filename)
            .setContentText(if (progress >= 0) "$progress%" else context.getString(R.string.downloading))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)

        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_CANCEL
            putExtra(DownloadActionReceiver.EXTRA_ID, item.id)
        }
        val cancelPi = PendingIntent.getBroadcast(
            context, item.id, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, context.getString(R.string.action_cancel), cancelPi)
        nm.notify(item.id, builder.build())
    }

    private fun showCompleteNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (item.file == null) {
            nm.cancel(item.id)
            return
        }

        nm.cancel(item.id)

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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_notification_complete))
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(downloadsPi)
            .addAction(0, context.getString(R.string.action_open), openPi)
        nm.notify(item.id, builder.build())
    }

    private fun showFailedNotification(context: Context, item: DownloadItem) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val downloadsIntent = Intent(context, DownloadsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val downloadsPi = PendingIntent.getActivity(
            context, item.id + 30000, downloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(item.filename)
            .setContentText(context.getString(R.string.download_notification_failed))
            .setAutoCancel(true)
            .setContentIntent(downloadsPi)
            .build()
            .let { nm.notify(item.id, it) }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext  = if (dot >= 0) name.substring(dot) else ""
        var i = 1
        while (file.exists()) { file = File(dir, "$base($i)$ext"); i++ }
        return file
    }
}

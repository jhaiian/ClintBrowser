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

            val contentType = response.header("Content-Type")?.substringBefore(";")?.trim() ?: ""
            val contentDisposition = response.header("Content-Disposition") ?: ""

            val rawStream = body.byteStream()
            val peekBuf = ByteArray(512)
            val peekRead = rawStream.read(peekBuf, 0, 512).coerceAtLeast(0)
            val peekBytes = peekBuf.copyOf(peekRead)

            if (item.filename.endsWith(".bin") || !item.filename.contains(".")) {
                val magicExt = detectExtFromMagicBytes(peekBytes)
                val fixedExt = magicExt
                    ?: android.webkit.MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(contentType)
                        ?.takeIf { it != "bin" }
                    ?: run {
                        val cdFilename = extractFilenameFromContentDisposition(contentDisposition)
                        val cdExt = cdFilename?.substringAfterLast('.')
                            ?.takeIf { it.isNotEmpty() && it.length <= 10 && it != "bin" }
                        cdExt ?: run {
                            val guessed = android.webkit.URLUtil.guessFileName(item.url, contentDisposition, contentType)
                            guessed.substringAfterLast('.').takeIf { it.isNotEmpty() && it != "bin" }
                        }
                    }
                if (fixedExt != null) {
                    item.filename = "${item.filename.removeSuffix(".bin")}.$fixedExt"
                }
            }

            val fullStream = java.io.SequenceInputStream(
                java.io.ByteArrayInputStream(peekBytes),
                rawStream
            )

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
            fullStream.use { input ->
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
        } catch (e: Throwable) {
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

    private fun extractFilenameFromContentDisposition(cd: String): String? {
        if (cd.isBlank()) return null
        val fnStar = Regex("""filename\*\s*=\s*(?:[^']*'[^']*')?(.+)""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.trim()?.let {
                java.net.URLDecoder.decode(it.trim('"', '\''), "UTF-8")
            }
        if (!fnStar.isNullOrBlank()) return fnStar
        val fn = Regex("""filename\s*=\s*["']?([^"';\r\n]+)["']?""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.trim()
        return fn?.ifBlank { null }
    }

    private var contentInfoUtil: com.j256.simplemagic.ContentInfoUtil? = null

    private fun detectExtFromMagicBytes(b: ByteArray): String? {
        if (b.size < 4) return null

        fun at(i: Int) = b.getOrElse(i) { 0 }.toInt() and 0xFF

        // MP4 / M4V / M4A / MOV – ISO base media file format: box-size then "ftyp" at offset 4
        if (b.size >= 8
            && at(4) == 0x66 && at(5) == 0x74 && at(6) == 0x79 && at(7) == 0x70) {
            if (b.size >= 12) {
                val brand = String(b, 8, 4, Charsets.ISO_8859_1)
                return when {
                    brand.startsWith("M4A") || brand.startsWith("m4a") -> "m4a"
                    brand.startsWith("M4V") || brand.startsWith("m4v") -> "m4v"
                    brand.startsWith("qt") || brand.startsWith("QT") -> "mov"
                    brand.startsWith("3gp") || brand.startsWith("3GP") -> "3gp"
                    brand.startsWith("3g2") || brand.startsWith("3G2") -> "3g2"
                    brand.startsWith("f4v") || brand.startsWith("F4V") -> "f4v"
                    else -> "mp4"
                }
            }
            return "mp4"
        }

        // WebM / MKV – EBML signature
        if (at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3) {
            val header = String(b, 0, minOf(b.size, 64), Charsets.ISO_8859_1)
            return if (header.contains("webm", ignoreCase = true)) "webm" else "mkv"
        }

        // RIFF-container formats (AVI / WAV / WebP)
        if (at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 && b.size >= 12) {
            return when {
                at(8) == 0x41 && at(9) == 0x56 && at(10) == 0x49 -> "avi"
                at(8) == 0x57 && at(9) == 0x41 && at(10) == 0x56 && at(11) == 0x45 -> "wav"
                at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "webp"
                else -> null
            }
        }

        // FLV
        if (at(0) == 0x46 && at(1) == 0x4C && at(2) == 0x56) return "flv"

        // MPEG Transport Stream (188-byte sync)
        if (at(0) == 0x47 && b.size >= 189 && at(188) == 0x47) return "ts"

        // MPEG Program Stream / MPEG-1/2 video elementary stream
        if (at(0) == 0x00 && at(1) == 0x00 && at(2) == 0x01) {
            return when (at(3)) {
                0xBA -> "mpg"
                0xB3 -> "mpg"
                else -> null
            }
        }

        // OGG (covers Ogg Vorbis, Ogg Theora, Opus in Ogg)
        if (at(0) == 0x4F && at(1) == 0x67 && at(2) == 0x67 && at(3) == 0x53) return "ogg"

        // MP3 – ID3 tag
        if (at(0) == 0x49 && at(1) == 0x44 && at(2) == 0x33) return "mp3"

        // MP3 – sync word (no ID3)
        if (at(0) == 0xFF && (at(1) and 0xE0) == 0xE0) {
            val layer = (at(1) shr 1) and 0x03
            if (layer == 1) return "mp3"
        }

        // AAC ADTS
        if (at(0) == 0xFF && (at(1) == 0xF1 || at(1) == 0xF9)) return "aac"

        // FLAC
        if (at(0) == 0x66 && at(1) == 0x4C && at(2) == 0x61 && at(3) == 0x43) return "flac"

        // JPEG
        if (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) return "jpg"

        // PNG
        if (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) return "png"

        // GIF
        if (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46) return "gif"

        // BMP
        if (at(0) == 0x42 && at(1) == 0x4D) return "bmp"

        // PDF
        if (at(0) == 0x25 && at(1) == 0x50 && at(2) == 0x44 && at(3) == 0x46) return "pdf"

        // ZIP / APK / DOCX / XLSX / PPTX (PK header)
        if (at(0) == 0x50 && at(1) == 0x4B && at(2) == 0x03 && at(3) == 0x04) {
            return try {
                if (contentInfoUtil == null) contentInfoUtil = com.j256.simplemagic.ContentInfoUtil()
                contentInfoUtil?.findMatch(b)?.mimeType?.let { mime ->
                    android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                } ?: "zip"
            } catch (_: Throwable) { "zip" }
        }

        // RAR
        if (at(0) == 0x52 && at(1) == 0x61 && at(2) == 0x72 && at(3) == 0x21) return "rar"

        // 7-Zip
        if (at(0) == 0x37 && at(1) == 0x7A && at(2) == 0xBC && at(3) == 0xAF) return "7z"

        // gzip
        if (at(0) == 0x1F && at(1) == 0x8B) return "gz"

        // Fall back to simplemagic for anything else
        return try {
            if (contentInfoUtil == null) contentInfoUtil = com.j256.simplemagic.ContentInfoUtil()
            val info = contentInfoUtil?.findMatch(b) ?: return null
            val mime = info.mimeType ?: return null
            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: mime.substringAfterLast('/').takeIf { it.isNotEmpty() && !it.contains('+') }
        } catch (_: Throwable) { null }
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

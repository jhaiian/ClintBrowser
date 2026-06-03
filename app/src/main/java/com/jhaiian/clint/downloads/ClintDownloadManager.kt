package com.jhaiian.clint.downloads

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors as JavaExecutors
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

object ClintDownloadManager {

    internal const val CHANNEL_ID = "clint_downloads"
    internal const val EVENT_CHANNEL_ID = "clint_download_events_v2"

    internal val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .build()

    internal val executor = Executors.newCachedThreadPool()
    internal val scheduledExecutor: ScheduledExecutorService = JavaExecutors.newScheduledThreadPool(2)
    private val idCounter = AtomicInteger(1)
    internal val futures = mutableMapOf<Int, Future<*>>()
    internal val pauseRequested: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    internal val removedIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    internal var appContext: Context? = null
    internal val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false

    val downloads = mutableListOf<DownloadItem>()
    var onDownloadsChanged: (() -> Unit)? = null

    internal fun persistDownload(item: DownloadItem) {
        val ctx = appContext ?: return
        DownloadPersistence.persistDownload(ctx, item, removedIds)
    }

    private fun deletePersistedDownload(id: Int) {
        val ctx = appContext ?: return
        DownloadPersistence.deletePersistedDownload(ctx, id)
    }

    fun createNotificationChannel(context: Context) {
        DownloadNotificationHelper.createNotificationChannel(context)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!initialized) {
            initialized = true
            loadDownloads()
            DownloadNetworkMonitor.register(context)
            tryDequeueNext(context)
        }
    }

    private fun loadDownloads() {
        val ctx = appContext ?: return
        val loaded = DownloadPersistence.loadDownloads(ctx)
        loaded.forEach { item ->
            if (item.url == "blob:" && item.status == DownloadStatus.QUEUED) {
                item.status = DownloadStatus.FAILED
                item.errorMessage = ctx.getString(R.string.download_error_blob_expired)
                DownloadPersistence.persistDownload(ctx, item, removedIds)
            }
        }
        synchronized(downloads) { downloads.addAll(loaded) }
        loaded.maxOfOrNull { it.id }?.let { max ->
            if (max >= idCounter.get()) idCounter.set(max + 1)
        }
    }

    internal fun concurrentLimit(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getInt(
            DownloadSettingsFragment.PREF_CONCURRENT_DOWNLOADS,
            DownloadSettingsFragment.DEFAULT_CONCURRENT_DOWNLOADS
        )
    }

    internal fun activeCount(): Int = synchronized(downloads) {
        downloads.count {
            it.status == DownloadStatus.CONNECTING ||
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.ALLOCATING ||
            it.status == DownloadStatus.MOVING ||
            it.status == DownloadStatus.RETRYING
        }
    }

    internal fun tryDequeueNext(context: Context) {
        val limit = concurrentLimit(context)
        val isMetered = !DownloadNetworkMonitor.isNetworkUnmetered(context)
        while (activeCount() < limit) {
            val next = synchronized(downloads) {
                downloads.lastOrNull { it.status == DownloadStatus.QUEUED && (!it.unmeteredOnly || !isMetered) }
            } ?: break
            next.status = DownloadStatus.CONNECTING
            next.speedBytesPerSec = 0L
            persistDownload(next)
            onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showProgressNotification(context, next)
            DownloadForegroundService.start(context)
            val future = executor.submit { DownloadWorker.runDownload(context, next) }
            futures[next.id] = future
        }
    }

    fun enqueueBlob(context: Context, base64: String, filename: String, mimeType: String) {
        val id = idCounter.getAndIncrement()
        val item = DownloadItem(id = id, url = "blob:", filename = filename, userAgent = "")
        item.startedAt = System.currentTimeMillis()
        synchronized(downloads) { downloads.add(0, item) }
        onDownloadsChanged?.invoke()
        DownloadNotificationHelper.showProgressNotification(context, item)
        DownloadForegroundService.start(context)
        val safMode = DownloadFileHelper.isSafCustomMode(context)
        val future = executor.submit {
            try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                var finalFilename = filename
                if (finalFilename.endsWith(".bin") || !finalFilename.contains(".")) {
                    val ext = DownloadFileHelper.detectExtFromMagicBytes(bytes.copyOf(minOf(bytes.size, 512)))
                        ?: android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    if (ext != null) finalFilename = "${finalFilename.removeSuffix(".bin")}.$ext"
                }
                item.filename = finalFilename
                val destDir = if (safMode) DownloadFileHelper.tempDownloadDir(context) else DownloadFileHelper.resolveDownloadDir()
                destDir.mkdirs()
                val destFile = DownloadFileHelper.uniqueFile(destDir, finalFilename)
                item.filename = destFile.name
                item.file = destFile
                item.totalBytes = bytes.size.toLong()
                java.io.FileOutputStream(destFile).use { it.write(bytes) }
                item.bytesDownloaded = bytes.size.toLong()
                if (safMode) {
                    DownloadWorker.moveTempToSaf(context, item)
                } else {
                    item.status = DownloadStatus.COMPLETE
                    persistDownload(item)
                    onDownloadsChanged?.invoke()
                    DownloadNotificationHelper.showCompleteNotification(context, item)
                    tryDequeueNext(context)
                }
            } catch (e: Throwable) {
                DownloadWorker.fail(context, item, e.message ?: context.getString(R.string.download_error_unknown))
            }
        }
        futures[id] = future
    }

    fun enqueue(context: Context, url: String, filename: String, userAgent: String, referer: String = "", cookies: String = "", retryEnabled: Boolean = true, unmeteredOnly: Boolean = false, splitParts: Int = 32, multithreadingParts: Int = 4, locationMode: String = "default", customLocationUri: String? = null) {
        val id = idCounter.getAndIncrement()
        val item = DownloadItem(id = id, url = url, filename = filename, userAgent = userAgent, referer = referer, cookies = cookies, retryEnabled = retryEnabled, unmeteredOnly = unmeteredOnly, splitParts = splitParts, multithreadingParts = multithreadingParts, locationMode = locationMode, customLocationUri = customLocationUri)
        item.startedAt = System.currentTimeMillis()

        if (item.unmeteredOnly && !DownloadNetworkMonitor.isNetworkUnmetered(context)) {
            item.status = DownloadStatus.PAUSED
            item.waitingForUnmetered = true
            synchronized(downloads) { downloads.add(0, item) }
            DownloadNetworkMonitor.unmeteredPausedIds.add(id)
            persistDownload(item)
            onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showWaitingUnmeteredNotification(context, item)
            DownloadForegroundService.start(context)
            return
        }

        if (activeCount() >= concurrentLimit(context)) {
            item.status = DownloadStatus.QUEUED
            synchronized(downloads) { downloads.add(0, item) }
            persistDownload(item)
            onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showQueuedNotification(context, item)
            DownloadForegroundService.start(context)
            return
        }

        synchronized(downloads) { downloads.add(0, item) }
        onDownloadsChanged?.invoke()
        DownloadNotificationHelper.showProgressNotification(context, item)
        DownloadForegroundService.start(context)
        val future = executor.submit { DownloadWorker.runDownload(context, item) }
        futures[id] = future
    }

    fun cancel(context: Context, id: Int) {
        remove(context, id)
    }

    fun pause(context: Context, id: Int) {
        val item = synchronized(downloads) { downloads.find { it.id == id } } ?: return

        if (item.status == DownloadStatus.QUEUED) {
            item.status = DownloadStatus.PAUSED
            item.waitingForUnmetered = false
            persistDownload(item)
            onDownloadsChanged?.invoke()
            return
        }

        pauseRequested.add(id)
        futures[id]?.cancel(true)
        futures.remove(id)

        if (item.status == DownloadStatus.RETRYING || item.status == DownloadStatus.CONNECTING) {
            item.status = DownloadStatus.PAUSED
            item.retryDelaySec = 0
            item.retryAttempt = 0
            item.waitingForUnmetered = false
            DownloadNetworkMonitor.unmeteredPausedIds.remove(id)
            persistDownload(item)
            onDownloadsChanged?.invoke()
            tryDequeueNext(context)
        } else if (item.waitingForUnmetered) {
            DownloadNetworkMonitor.unmeteredPausedIds.remove(id)
            item.waitingForUnmetered = false
            item.status = DownloadStatus.PAUSED
            context.getSystemService(NotificationManager::class.java).cancel(id)
            persistDownload(item)
            onDownloadsChanged?.invoke()
        } else if (item.waitingForNetwork) {
            DownloadNetworkMonitor.networkWaitingIds.remove(id)
            item.waitingForNetwork = false
            item.status = DownloadStatus.PAUSED
            context.getSystemService(NotificationManager::class.java).cancel(id)
            persistDownload(item)
            onDownloadsChanged?.invoke()
        }
    }

    fun resume(context: Context, id: Int) {
        val item = synchronized(downloads) { downloads.find { it.id == id } } ?: return
        if (item.status != DownloadStatus.PAUSED) return
        pauseRequested.remove(id)
        DownloadNetworkMonitor.unmeteredPausedIds.remove(id)
        DownloadNetworkMonitor.networkWaitingIds.remove(id)

        if (item.unmeteredOnly && !DownloadNetworkMonitor.isNetworkUnmetered(context)) {
            DownloadNetworkMonitor.unmeteredPausedIds.add(id)
            item.waitingForUnmetered = true
            persistDownload(item)
            onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showWaitingUnmeteredNotification(context, item)
            return
        }

        if (activeCount() >= concurrentLimit(context)) {
            item.status = DownloadStatus.QUEUED
            item.waitingForUnmetered = false
            item.waitingForNetwork = false
            persistDownload(item)
            onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showQueuedNotification(context, item)
            return
        }

        item.waitingForUnmetered = false
        item.waitingForNetwork = false
        item.status = DownloadStatus.CONNECTING
        item.speedBytesPerSec = 0L
        persistDownload(item)
        onDownloadsChanged?.invoke()
        DownloadNotificationHelper.showProgressNotification(context, item)
        DownloadForegroundService.start(context)
        val future = executor.submit { DownloadWorker.runDownload(context, item) }
        futures[item.id] = future
    }

    fun remove(context: Context, id: Int, deleteFile: Boolean = false) {
        removedIds.add(id)
        pauseRequested.remove(id)
        DownloadNetworkMonitor.unmeteredPausedIds.remove(id)
        DownloadNetworkMonitor.networkWaitingIds.remove(id)
        val item = synchronized(downloads) { downloads.find { it.id == id } }
        futures[id]?.cancel(true)
        futures.remove(id)
        context.getSystemService(NotificationManager::class.java).cancel(id)
        if (deleteFile) {
            item?.file?.delete()
            item?.contentUri?.let { uriStr ->
                runCatching {
                    val ctx = appContext ?: return@runCatching
                    val docFile = DocumentFile.fromSingleUri(ctx, Uri.parse(uriStr))
                    docFile?.delete()
                }
            }
        }
        val tempDir = DownloadFileHelper.tempDownloadDir(appContext)
        item?.filename?.let { name ->
            File(tempDir, name).takeIf { it.exists() }?.delete()
        }
        synchronized(downloads) { downloads.removeIf { it.id == id } }
        deletePersistedDownload(id)
        onDownloadsChanged?.invoke()
    }

    fun clearCompleted() {
        val toDelete = synchronized(downloads) {
            val ids = downloads.filter {
                it.status != DownloadStatus.DOWNLOADING &&
                it.status != DownloadStatus.PAUSED &&
                it.status != DownloadStatus.MOVING &&
                it.status != DownloadStatus.ALLOCATING &&
                it.status != DownloadStatus.CONNECTING &&
                it.status != DownloadStatus.RETRYING &&
                it.status != DownloadStatus.QUEUED
            }.map { it.id }
            downloads.removeAll {
                it.status != DownloadStatus.DOWNLOADING &&
                it.status != DownloadStatus.PAUSED &&
                it.status != DownloadStatus.MOVING &&
                it.status != DownloadStatus.ALLOCATING &&
                it.status != DownloadStatus.CONNECTING &&
                it.status != DownloadStatus.RETRYING &&
                it.status != DownloadStatus.QUEUED
            }
            ids
        }
        toDelete.forEach { deletePersistedDownload(it) }
        onDownloadsChanged?.invoke()
    }

    fun retryFailed(context: Context, id: Int) {
        val item = synchronized(downloads) { downloads.find { it.id == id } } ?: return
        if (item.status != DownloadStatus.FAILED) return
        item.retryAttempt = 0
        item.retryDelaySec = 0
        item.errorMessage = null
        item.speedBytesPerSec = 0L

        if (item.unmeteredOnly && !DownloadNetworkMonitor.isNetworkUnmetered(context)) {
            DownloadNetworkMonitor.unmeteredPausedIds.add(id)
            item.status = DownloadStatus.PAUSED
            item.waitingForUnmetered = true
            persistDownload(item)
            onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showWaitingUnmeteredNotification(context, item)
            DownloadForegroundService.start(context)
            return
        }

        if (activeCount() >= concurrentLimit(context)) {
            item.status = DownloadStatus.QUEUED
            persistDownload(item)
            onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showQueuedNotification(context, item)
            DownloadForegroundService.start(context)
            return
        }

        item.waitingForUnmetered = false
        item.status = DownloadStatus.CONNECTING
        persistDownload(item)
        onDownloadsChanged?.invoke()
        DownloadNotificationHelper.showProgressNotification(context, item)
        DownloadForegroundService.start(context)
        val future = executor.submit { DownloadWorker.runDownload(context, item) }
        futures[item.id] = future
    }

    fun updateDownloadUrl(id: Int, newUrl: String) {
        val item = synchronized(downloads) { downloads.find { it.id == id } } ?: return
        item.url = newUrl
        onDownloadsChanged?.invoke()
    }

    fun onUnmeteredOnlyChanged(context: Context, enabled: Boolean) {
        DownloadNetworkMonitor.onUnmeteredOnlyChanged(context, enabled)
    }
}

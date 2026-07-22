package com.jhaiian.clint.downloads

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * Owns the download queue, its persisted state, and the lifetime of every in-flight transfer.
 *
 * All state lives in [downloadsFlow], a [StateFlow] that replaces the previous mutable list plus
 * change-callback pair. Consumers collect it directly instead of registering a callback, and every
 * mutation goes through [publish]/[updateItem]/[addNew] so the flow only ever sees new, immutable
 * snapshots (an object already published to a [StateFlow] must never be mutated in place, or
 * conflation will treat the change as a no-op).
 *
 * [pause]/[resume]/[remove]/[enqueue] and friends stay plain, non-suspend functions so existing
 * callers (broadcast receivers, click listeners, other non-coroutine call sites) don't need to
 * change; each one launches its work on [applicationScope] internally.
 */
object ClintDownloadManager {

    internal const val CHANNEL_ID = "clint_downloads"
    internal const val EVENT_CHANNEL_ID = "clint_download_events_v2"

    internal val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .build()

    /** Application-scoped: downloads must keep running independently of any single screen's lifecycle. */
    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val idCounter = AtomicInteger(1)

    /** Tracks the coroutine running each download so [pause]/[remove] can cancel it. */
    internal val activeJobs: MutableMap<Int, Job> = ConcurrentHashMap()

    /**
     * Tracks the [SpeedLimiter] backing each currently-running transfer, keyed the same way and
     * with the same lifetime as [activeJobs], so [updateDownloadSettings] can change an active
     * download's throttle in place instead of waiting for it to restart.
     */
    internal val activeSpeedLimiters: MutableMap<Int, SpeedLimiter> = ConcurrentHashMap()

    /** Cooperative "please stop and preserve partial progress" signal, checked by [DownloadWorker]. */
    internal val pauseRequested: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Marks a download as fully deleted, so any in-flight work knows not to persist further. */
    internal val removedIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    internal var appContext: Context? = null
    private var initialized = false

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadsFlow: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private fun addNew(item: DownloadItem) {
        _downloads.update { listOf(item) + it }
    }

    internal fun publish(item: DownloadItem) {
        _downloads.update { list -> list.map { if (it.id == item.id) item else it } }
    }

    /** Applies [transform] to the current snapshot of [id], if present. Used by callers that don't already hold a working copy. */
    internal fun updateItem(id: Int, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    internal suspend fun persistDownload(item: DownloadItem) {
        val ctx = appContext ?: return
        DownloadPersistence.persistDownload(ctx, item, removedIds)
    }

    /** Lightweight progress checkpoint so a killed process resumes near where it left off; see [DownloadPersistence.checkpointProgress]. */
    internal fun checkpointProgress(id: Int, bytesDownloaded: Long, completedPartsMask: Long, partOffsets: String) {
        if (id in removedIds) return
        val ctx = appContext ?: return
        DownloadPersistence.checkpointProgress(ctx, id, bytesDownloaded, completedPartsMask, partOffsets)
    }

    private suspend fun deletePersistedDownload(id: Int) {
        val ctx = appContext ?: return
        DownloadPersistence.deletePersistedDownload(ctx, id)
    }

    fun createNotificationChannel(context: Context) {
        DownloadNotificationHelper.createNotificationChannel(context)
    }

    /**
     * Loads persisted downloads and starts the network monitor. Returns the [Job] doing this work
     * so callers that can't suspend (like [DownloadBootReceiver]) can still wait for completion via
     * [Job.invokeOnCompletion], pairing it with `goAsync()`.
     */
    fun init(context: Context): Job {
        appContext = context.applicationContext
        if (initialized) return Job().apply { complete() }
        initialized = true
        val appCtx = context.applicationContext
        return applicationScope.launch {
            loadDownloads(appCtx)
            DownloadNetworkMonitor.register(appCtx)
            DownloadScheduleMonitor.scheduleNextCheck(appCtx)
            DownloadCustomScheduleMonitor.rearmAll(appCtx)
            tryDequeueNext(appCtx)
        }
    }

    private suspend fun loadDownloads(context: Context) {
        val loaded = DownloadPersistence.loadDownloads(context).map { item ->
            if (item.url == "blob:" && item.status == DownloadStatus.QUEUED) {
                val expired = item.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = context.getString(R.string.download_error_blob_expired)
                )
                DownloadPersistence.persistDownload(context, expired, removedIds)
                expired
            } else {
                item
            }
        }
        _downloads.update { loaded }
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

    internal fun activeCount(): Int =
        downloadsFlow.value.count { it.status in DownloadStatus.ACTIVELY_WORKING }

    /** Returns the first unfinished download already queued for [url], if any, so callers can warn before starting a duplicate. */
    fun findActiveDownloadForUrl(url: String): DownloadItem? =
        downloadsFlow.value.firstOrNull { it.url == url && it.status in DownloadStatus.NOT_FINISHED }

    internal fun tryDequeueNext(context: Context) {
        val limit = concurrentLimit(context)
        val isMetered = !DownloadNetworkMonitor.isNetworkUnmetered(context)
        val globalWindowOpen = DownloadScheduleMonitor.isWithinWindow(context)
        while (activeCount() < limit) {
            val next = downloadsFlow.value.lastOrNull {
                it.status == DownloadStatus.QUEUED && (!it.unmeteredOnly || !isMetered) &&
                    (globalWindowOpen || it.scheduledStartAtMillis > 0L)
            } ?: break
            val updated = next.copy(status = DownloadStatus.CONNECTING, speedBytesPerSec = 0L)
            publish(updated)
            DownloadNotificationHelper.showProgressNotification(context, updated)
            DownloadForegroundService.start(context)
            launchDownload(context, updated)
        }
    }

    /** Launches the coroutine that persists and runs one download attempt-chain, tracked in [activeJobs]. */
    private fun launchDownload(context: Context, item: DownloadItem) {
        activeSpeedLimiters[item.id] = SpeedLimiter(item.speedLimitBytesPerSec)
        val job = applicationScope.launch {
            persistDownload(item)
            DownloadWorker.run(context, item)
        }
        activeJobs[item.id] = job
        job.invokeOnCompletion {
            activeJobs.remove(item.id, job)
            activeSpeedLimiters.remove(item.id)
        }
    }

    /**
     * Applies a [transform] to [item] using whatever retry/unmetered/speed-limit settings are
     * currently live for its id, so a change saved through [updateDownloadSettings] while this
     * transfer is in flight survives the worker's own next progress publish instead of being
     * reverted by it. Every other field comes from [item] untouched.
     */
    internal fun withLiveSettings(item: DownloadItem): DownloadItem {
        val current = downloadsFlow.value.find { it.id == item.id } ?: return item
        return item.copy(
            retryEnabled = current.retryEnabled,
            unmeteredOnly = current.unmeteredOnly,
            speedLimitBytesPerSec = current.speedLimitBytesPerSec
        )
    }

    /**
     * Updates the subset of a download's settings that can change safely while it's queued,
     * paused, or actively transferring: retry-on-failure, Wi-Fi/unmetered-only, and the speed
     * limit. Unlike the filename, destination, or part count, none of these are tied to bytes
     * already written, so nothing needs to restart. Takes effect immediately: the change is
     * published and persisted right away, and if this download is actively transferring right
     * now, its live [SpeedLimiter] is retuned in place so a lowered or raised cap is honored on
     * the very next chunk rather than the next attempt.
     */
    fun updateDownloadSettings(
        context: Context,
        id: Int,
        retryEnabled: Boolean,
        unmeteredOnly: Boolean,
        speedLimitBytesPerSec: Long
    ) {
        val item = downloadsFlow.value.find { it.id == id } ?: return
        val updated = item.copy(
            retryEnabled = retryEnabled,
            unmeteredOnly = unmeteredOnly,
            speedLimitBytesPerSec = speedLimitBytesPerSec
        )
        publish(updated)
        applicationScope.launch { persistDownload(updated) }
        activeSpeedLimiters[id]?.updateLimit(speedLimitBytesPerSec)
    }

    fun enqueueBlob(context: Context, base64: String, filename: String, mimeType: String) {
        val id = idCounter.getAndIncrement()
        val item = DownloadItem(
            id = id, url = "blob:", filename = filename, userAgent = "",
            startedAt = System.currentTimeMillis()
        )
        addNew(item)
        DownloadNotificationHelper.showProgressNotification(context, item)
        DownloadForegroundService.start(context)
        val directCustomDir = DownloadFileHelper.resolveDirectCustomDir(context)
        val safMode = DownloadFileHelper.isSafCustomMode(context) && directCustomDir == null
        val job = applicationScope.launch {
            var current = item
            try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                var finalFilename = current.filename
                if (finalFilename.endsWith(".bin") || !finalFilename.contains(".")) {
                    val ext = DownloadFileHelper.detectExtFromMagicBytes(bytes.copyOf(minOf(bytes.size, 512)))
                        ?: android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    if (ext != null) finalFilename = "${finalFilename.removeSuffix(".bin")}.$ext"
                }
                val destDir = directCustomDir
                    ?: if (safMode) DownloadFileHelper.tempDownloadDir(context) else DownloadFileHelper.resolveDownloadDir()
                destDir.mkdirs()
                val destFile = DownloadFileHelper.uniqueFile(destDir, finalFilename)
                java.io.FileOutputStream(destFile).use { it.write(bytes) }
                current = current.copy(
                    filename = destFile.name,
                    file = destFile,
                    totalBytes = bytes.size.toLong(),
                    bytesDownloaded = bytes.size.toLong()
                )
                if (safMode) {
                    DownloadWorker.moveTempToSaf(context, current)
                } else {
                    current = current.copy(status = DownloadStatus.COMPLETE)
                    persistDownload(current)
                    publish(current)
                    DownloadNotificationHelper.showCompleteNotification(context, current)
                    tryDequeueNext(context)
                }
            } catch (e: Throwable) {
                DownloadWorker.fail(context, current, e.message ?: context.getString(R.string.download_error_unknown))
            }
        }
        activeJobs[id] = job
        job.invokeOnCompletion { activeJobs.remove(id, job) }
    }

    fun enqueue(
        context: Context,
        url: String,
        filename: String,
        userAgent: String,
        referer: String = "",
        cookies: String = "",
        retryEnabled: Boolean = true,
        unmeteredOnly: Boolean = false,
        splitParts: Int = 32,
        multithreadingParts: Int = 4,
        speedLimitBytesPerSec: Long = 0L,
        locationMode: String = "default",
        customLocationUri: String? = null,
        scheduledStartAtMillis: Long = 0L
    ) {
        val id = idCounter.getAndIncrement()
        val baseItem = DownloadItem(
            id = id, url = url, filename = filename, userAgent = userAgent, referer = referer,
            cookies = cookies, retryEnabled = retryEnabled, unmeteredOnly = unmeteredOnly,
            splitParts = splitParts, multithreadingParts = multithreadingParts,
            speedLimitBytesPerSec = speedLimitBytesPerSec,
            locationMode = locationMode, customLocationUri = customLocationUri,
            startedAt = System.currentTimeMillis(), scheduledStartAtMillis = scheduledStartAtMillis
        )

        if (scheduledStartAtMillis > System.currentTimeMillis()) {
            val item = baseItem.copy(status = DownloadStatus.PAUSED, waitingForCustomSchedule = true)
            addNew(item)
            applicationScope.launch { persistDownload(item) }
            DownloadCustomScheduleMonitor.schedule(context, id, scheduledStartAtMillis)
            DownloadNotificationHelper.showWaitingCustomScheduleNotification(context, item)
            DownloadForegroundService.start(context)
            return
        }

        if (scheduledStartAtMillis == 0L && !DownloadScheduleMonitor.isWithinWindow(context)) {
            val item = baseItem.copy(status = DownloadStatus.PAUSED, waitingForSchedule = true)
            addNew(item)
            DownloadScheduleMonitor.scheduleWaitingIds.add(id)
            applicationScope.launch { persistDownload(item) }
            DownloadNotificationHelper.showWaitingScheduleNotification(context, item)
            DownloadForegroundService.start(context)
            return
        }

        if (baseItem.unmeteredOnly && !DownloadNetworkMonitor.isNetworkUnmetered(context)) {
            val item = baseItem.copy(status = DownloadStatus.PAUSED, waitingForUnmetered = true)
            addNew(item)
            DownloadNetworkMonitor.unmeteredPausedIds.add(id)
            applicationScope.launch { persistDownload(item) }
            DownloadNotificationHelper.showWaitingUnmeteredNotification(context, item)
            DownloadForegroundService.start(context)
            return
        }

        if (activeCount() >= concurrentLimit(context)) {
            val item = baseItem.copy(status = DownloadStatus.QUEUED)
            addNew(item)
            applicationScope.launch { persistDownload(item) }
            DownloadNotificationHelper.showQueuedNotification(context, item)
            DownloadForegroundService.start(context)
            return
        }

        addNew(baseItem)
        DownloadNotificationHelper.showProgressNotification(context, baseItem)
        DownloadForegroundService.start(context)
        launchDownload(context, baseItem)
    }

    fun cancel(context: Context, id: Int) {
        remove(context, id)
    }

    fun pause(context: Context, id: Int) {
        val item = downloadsFlow.value.find { it.id == id } ?: return

        if (item.status == DownloadStatus.QUEUED) {
            val updated = item.copy(status = DownloadStatus.PAUSED, waitingForUnmetered = false)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
            return
        }

        // Cooperative only: the worker polls pauseRequested itself so it can finish its current
        // chunk cleanly and compute an accurate resume point. Cancelling activeJobs[id] here would
        // unwind mid-transfer before that bookkeeping runs; remove() below is the hard-stop path.
        pauseRequested.add(id)

        if (item.status == DownloadStatus.RETRYING || item.status == DownloadStatus.CONNECTING) {
            val updated = item.copy(
                status = DownloadStatus.PAUSED, retryDelaySec = 0, retryAttempt = 0, waitingForUnmetered = false
            )
            DownloadNetworkMonitor.unmeteredPausedIds.remove(id)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
            tryDequeueNext(context)
        } else if (item.waitingForUnmetered) {
            DownloadNetworkMonitor.unmeteredPausedIds.remove(id)
            val updated = item.copy(waitingForUnmetered = false, status = DownloadStatus.PAUSED)
            context.getSystemService(NotificationManager::class.java).cancel(id)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
        } else if (item.waitingForNetwork) {
            DownloadNetworkMonitor.networkWaitingIds.remove(id)
            val updated = item.copy(waitingForNetwork = false, status = DownloadStatus.PAUSED)
            context.getSystemService(NotificationManager::class.java).cancel(id)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
        } else if (item.waitingForSchedule) {
            DownloadScheduleMonitor.scheduleWaitingIds.remove(id)
            val updated = item.copy(waitingForSchedule = false, status = DownloadStatus.PAUSED)
            context.getSystemService(NotificationManager::class.java).cancel(id)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
        } else if (item.waitingForCustomSchedule) {
            DownloadCustomScheduleMonitor.cancel(context, id)
            val updated = item.copy(waitingForCustomSchedule = false, status = DownloadStatus.PAUSED)
            context.getSystemService(NotificationManager::class.java).cancel(id)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
        }
    }

    fun resume(context: Context, id: Int) {
        val item = downloadsFlow.value.find { it.id == id } ?: return
        if (item.status != DownloadStatus.PAUSED) return
        pauseRequested.remove(id)
        DownloadNetworkMonitor.unmeteredPausedIds.remove(id)
        DownloadNetworkMonitor.networkWaitingIds.remove(id)
        DownloadScheduleMonitor.scheduleWaitingIds.remove(id)
        DownloadCustomScheduleMonitor.cancel(context, id)

        if (item.scheduledStartAtMillis > System.currentTimeMillis()) {
            val updated = item.copy(
                waitingForCustomSchedule = true, waitingForUnmetered = false, waitingForNetwork = false,
                waitingForSchedule = false
            )
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
            DownloadCustomScheduleMonitor.schedule(context, id, item.scheduledStartAtMillis)
            DownloadNotificationHelper.showWaitingCustomScheduleNotification(context, updated)
            return
        }

        if (item.scheduledStartAtMillis == 0L && !DownloadScheduleMonitor.isWithinWindow(context)) {
            DownloadScheduleMonitor.scheduleWaitingIds.add(id)
            val updated = item.copy(
                waitingForSchedule = true, waitingForUnmetered = false, waitingForNetwork = false,
                waitingForCustomSchedule = false
            )
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
            DownloadNotificationHelper.showWaitingScheduleNotification(context, updated)
            return
        }

        if (item.unmeteredOnly && !DownloadNetworkMonitor.isNetworkUnmetered(context)) {
            DownloadNetworkMonitor.unmeteredPausedIds.add(id)
            val updated = item.copy(waitingForUnmetered = true, waitingForCustomSchedule = false)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
            DownloadNotificationHelper.showWaitingUnmeteredNotification(context, updated)
            return
        }

        if (activeCount() >= concurrentLimit(context)) {
            val updated = item.copy(
                status = DownloadStatus.QUEUED, waitingForUnmetered = false, waitingForNetwork = false,
                waitingForCustomSchedule = false
            )
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
            DownloadNotificationHelper.showQueuedNotification(context, updated)
            return
        }

        val updated = item.copy(
            waitingForUnmetered = false, waitingForNetwork = false, waitingForCustomSchedule = false,
            status = DownloadStatus.CONNECTING, speedBytesPerSec = 0L
        )
        publish(updated)
        DownloadNotificationHelper.showProgressNotification(context, updated)
        DownloadForegroundService.start(context)
        launchDownload(context, updated)
    }

    fun remove(context: Context, id: Int, deleteFile: Boolean = false) {
        removedIds.add(id)
        pauseRequested.remove(id)
        DownloadNetworkMonitor.unmeteredPausedIds.remove(id)
        DownloadNetworkMonitor.networkWaitingIds.remove(id)
        DownloadCustomScheduleMonitor.cancel(context, id)
        val item = downloadsFlow.value.find { it.id == id }
        activeJobs[id]?.cancel()
        context.getSystemService(NotificationManager::class.java).cancel(id)
        _downloads.update { list -> list.filterNot { it.id == id } }
        val appCtx = appContext
        applicationScope.launch {
            if (deleteFile) {
                item?.file?.delete()
                item?.contentUri?.let { uriStr ->
                    runCatching {
                        val ctx = appCtx ?: return@runCatching
                        val docFile = DocumentFile.fromSingleUri(ctx, Uri.parse(uriStr))
                        docFile?.delete()
                    }
                }
            }
            val tempDir = DownloadFileHelper.tempDownloadDir(appCtx)
            item?.filename?.let { name ->
                File(tempDir, name).takeIf { it.exists() }?.delete()
            }
            deletePersistedDownload(id)
        }
    }

    fun clearCompleted() {
        var toDelete: List<Int> = emptyList()
        _downloads.update { list ->
            val (keep, remove) = list.partition { it.status in DownloadStatus.NOT_FINISHED }
            toDelete = remove.map { it.id }
            keep
        }
        applicationScope.launch {
            toDelete.forEach { deletePersistedDownload(it) }
        }
    }

    fun retryFailed(context: Context, id: Int) {
        val item = downloadsFlow.value.find { it.id == id } ?: return
        if (item.status != DownloadStatus.FAILED) return
        var updated = item.copy(retryAttempt = 0, retryDelaySec = 0, errorMessage = null, speedBytesPerSec = 0L)

        if (item.scheduledStartAtMillis == 0L && !DownloadScheduleMonitor.isWithinWindow(context)) {
            DownloadScheduleMonitor.scheduleWaitingIds.add(id)
            updated = updated.copy(status = DownloadStatus.PAUSED, waitingForSchedule = true)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
            DownloadNotificationHelper.showWaitingScheduleNotification(context, updated)
            DownloadForegroundService.start(context)
            return
        }

        if (updated.unmeteredOnly && !DownloadNetworkMonitor.isNetworkUnmetered(context)) {
            DownloadNetworkMonitor.unmeteredPausedIds.add(id)
            updated = updated.copy(status = DownloadStatus.PAUSED, waitingForUnmetered = true)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
            DownloadNotificationHelper.showWaitingUnmeteredNotification(context, updated)
            DownloadForegroundService.start(context)
            return
        }

        if (activeCount() >= concurrentLimit(context)) {
            updated = updated.copy(status = DownloadStatus.QUEUED)
            publish(updated)
            applicationScope.launch { persistDownload(updated) }
            DownloadNotificationHelper.showQueuedNotification(context, updated)
            DownloadForegroundService.start(context)
            return
        }

        updated = updated.copy(waitingForUnmetered = false, status = DownloadStatus.CONNECTING)
        publish(updated)
        DownloadNotificationHelper.showProgressNotification(context, updated)
        DownloadForegroundService.start(context)
        launchDownload(context, updated)
    }

    fun updateDownloadUrl(id: Int, newUrl: String) {
        updateItem(id) { it.copy(url = newUrl) }
    }

    fun onUnmeteredOnlyChanged(context: Context, enabled: Boolean) {
        DownloadNetworkMonitor.onUnmeteredOnlyChanged(context, enabled)
    }
}

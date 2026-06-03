package com.jhaiian.clint.downloads

import android.app.NotificationManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.io.SequenceInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal object DownloadWorker {

    private const val MIN_PART_BYTES = 512 * 1024L
    private const val MAX_PART_RETRIES = 3
    private const val RAMP_UP_INTERVAL_MS = 3000L

    fun moveTempToSaf(context: Context, item: DownloadItem) {
        val treeUri = DownloadFileHelper.getSafTreeUri(context, item)
        if (treeUri == null) {
            if (item.activeStartedAt > 0L) {
                item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                item.activeStartedAt = 0L
            }
            item.completedAt = System.currentTimeMillis()
            item.status = DownloadStatus.COMPLETE
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showCompleteNotification(context, item)
            ClintDownloadManager.tryDequeueNext(context)
            return
        }

        val tempFile = item.file ?: run {
            if (item.activeStartedAt > 0L) {
                item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                item.activeStartedAt = 0L
            }
            item.completedAt = System.currentTimeMillis()
            item.status = DownloadStatus.COMPLETE
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showCompleteNotification(context, item)
            ClintDownloadManager.tryDequeueNext(context)
            return
        }

        item.status = DownloadStatus.MOVING
        item.moveProgress = 0
        ClintDownloadManager.persistDownload(item)
        ClintDownloadManager.onDownloadsChanged?.invoke()
        DownloadNotificationHelper.showMovingNotification(context, item)

        try {
            val docDir = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IOException(context.getString(R.string.download_error_saf_no_access))
            if (!docDir.canWrite()) throw IOException(context.getString(R.string.download_error_saf_not_writable))

            val finalName = DownloadFileHelper.uniqueSafName(docDir, item.filename)
            val docFile = docDir.createFile("application/octet-stream", finalName)
                ?: throw IOException(context.getString(R.string.download_error_saf_create_failed))

            docFile.name?.let { item.filename = it }

            val totalBytes = tempFile.length()
            var bytesCopied = 0L
            val buffer = ByteArray(65536)

            context.contentResolver.openOutputStream(docFile.uri)?.use { output ->
                tempFile.inputStream().use { input ->
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            docFile.delete()
                            throw InterruptedException()
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            val newProgress = ((bytesCopied * 100) / totalBytes).toInt()
                            if (newProgress != item.moveProgress) {
                                item.moveProgress = newProgress
                                DownloadNotificationHelper.showMovingNotification(context, item)
                                ClintDownloadManager.onDownloadsChanged?.invoke()
                            }
                        }
                    }
                }
            } ?: throw IOException(context.getString(R.string.download_error_saf_no_stream))

            tempFile.delete()
            item.file = null
            item.contentUri = docFile.uri.toString()
            item.moveProgress = 100
            if (item.activeStartedAt > 0L) {
                item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                item.activeStartedAt = 0L
            }
            item.completedAt = System.currentTimeMillis()
            item.status = DownloadStatus.COMPLETE
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showCompleteNotification(context, item)
            ClintDownloadManager.tryDequeueNext(context)
            scanCompletedFile(context, item)
        } catch (e: Throwable) {
            tempFile.delete()
            if (item.status != DownloadStatus.PAUSED) {
                fail(context, item, e.message ?: context.getString(R.string.download_error_unknown))
            }
        }
    }

    fun runDownload(context: Context, item: DownloadItem) {
        if (item.unmeteredOnly && !DownloadNetworkMonitor.isNetworkUnmetered(context)) {
            DownloadNetworkMonitor.unmeteredPausedIds.add(item.id)
            item.status = DownloadStatus.PAUSED
            item.waitingForUnmetered = true
            item.speedBytesPerSec = 0L
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            context.getSystemService(NotificationManager::class.java).cancel(item.id)
            DownloadNotificationHelper.showWaitingUnmeteredNotification(context, item)
            return
        }

        val safMode = DownloadFileHelper.isSafCustomMode(context, item)
        val isResumingFile = item.bytesDownloaded > 0L && item.file?.exists() == true

        try {
            item.status = DownloadStatus.CONNECTING
            item.speedBytesPerSec = 0L
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showProgressNotification(context, item)

            val request = Request.Builder()
                .url(item.url)
                .header("User-Agent", item.userAgent)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .apply {
                    if (item.referer.isNotEmpty()) header("Referer", item.referer)
                    if (item.cookies.isNotEmpty()) header("Cookie", item.cookies)
                    if (isResumingFile) header("Range", "bytes=${item.bytesDownloaded}-")
                }
                .build()

            val response = ClintDownloadManager.httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                fail(context, item, "Server error ${response.code}")
                return
            }

            val serverAcceptedRange = response.code == 206

            if (isResumingFile && !serverAcceptedRange) {
                item.bytesDownloaded = 0L
                item.resumable = false
                item.file?.delete()
                item.file = null
            }

            val effectiveResume = isResumingFile && serverAcceptedRange
            val body = response.body ?: run { fail(context, item, "Empty response"); return }

            val inputStream: java.io.InputStream
            val outputFile: File

            if (effectiveResume) {
                inputStream = body.byteStream()
                outputFile = item.file!!
            } else {
                item.resumable = response.header("Accept-Ranges")?.trim()?.lowercase() == "bytes"
                item.totalBytes = body.contentLength()
                item.bytesDownloaded = 0L
                ClintDownloadManager.onDownloadsChanged?.invoke()

                val contentType = response.header("Content-Type")?.substringBefore(";")?.trim() ?: ""
                val contentDisposition = response.header("Content-Disposition") ?: ""

                val rawStream = body.byteStream()
                val peekBuf = ByteArray(512)
                val peekRead = rawStream.read(peekBuf, 0, 512).coerceAtLeast(0)
                val peekBytes = peekBuf.copyOf(peekRead)

                if (item.filename.endsWith(".bin") || !item.filename.contains(".")) {
                    val magicExt = DownloadFileHelper.detectExtFromMagicBytes(peekBytes)
                    val fixedExt = magicExt
                        ?: MimeTypeMap.getSingleton()
                            .getExtensionFromMimeType(contentType)
                            ?.takeIf { it != "bin" }
                        ?: run {
                            val cdFilename = DownloadFileHelper.extractFilenameFromContentDisposition(contentDisposition)
                            val cdExt = cdFilename?.substringAfterLast('.')
                                ?.takeIf { it.isNotEmpty() && it.length <= 10 && it != "bin" }
                            cdExt ?: run {
                                val guessed = URLUtil.guessFileName(item.url, contentDisposition, contentType)
                                guessed.substringAfterLast('.').takeIf { it.isNotEmpty() && it != "bin" }
                            }
                        }
                    if (fixedExt != null) {
                        item.filename = "${item.filename.removeSuffix(".bin")}.$fixedExt"
                    }
                }

                val reuseExistingFile = item.file?.let { f ->
                    f.exists() && item.totalBytes > 0L && f.length() == item.totalBytes
                } == true

                val destFile: File
                if (reuseExistingFile) {
                    destFile = item.file!!
                } else {
                    val destDir = if (safMode) DownloadFileHelper.tempDownloadDir(context) else DownloadFileHelper.resolveDownloadDir()
                    destDir.mkdirs()
                    val newFile = DownloadFileHelper.uniqueFile(destDir, item.filename)
                    item.filename = newFile.name
                    item.file = newFile
                    destFile = newFile
                }

                if (item.totalBytes > 0L) {
                    if (!reuseExistingFile) {
                        if (!preAllocateFile(context, item, destFile)) return
                    }
                    if (ClintDownloadManager.pauseRequested.remove(item.id)) {
                        item.status = DownloadStatus.PAUSED
                        item.speedBytesPerSec = 0L
                        ClintDownloadManager.persistDownload(item)
                        ClintDownloadManager.onDownloadsChanged?.invoke()
                        context.getSystemService(NotificationManager::class.java).cancel(item.id)
                        if (item.id in DownloadNetworkMonitor.unmeteredPausedIds) {
                            DownloadNotificationHelper.showWaitingUnmeteredNotification(context, item)
                        } else {
                            DownloadNotificationHelper.showPausedNotification(context, item)
                        }
                        ClintDownloadManager.tryDequeueNext(context)
                        return
                    }

                    val splitParts = item.splitParts
                    val simultaneousParts = item.multithreadingParts

                    val effectiveParts = (item.totalBytes / MIN_PART_BYTES)
                        .toInt()
                        .coerceAtMost(splitParts)
                        .coerceAtLeast(1)

                    if (item.resumable && effectiveParts > 1 && !item.parallelRateLimited) {
                        body.close()
                        item.status = DownloadStatus.DOWNLOADING
                        item.activeStartedAt = System.currentTimeMillis()
                        ClintDownloadManager.persistDownload(item)
                        ClintDownloadManager.onDownloadsChanged?.invoke()
                        runParallelDownload(context, item, destFile, effectiveParts, simultaneousParts, safMode)
                        return
                    }
                }

                inputStream = SequenceInputStream(ByteArrayInputStream(peekBytes), rawStream)
                outputFile = destFile
            }

            item.status = DownloadStatus.DOWNLOADING
            item.activeStartedAt = System.currentTimeMillis()
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()

            val writeStartPos = if (effectiveResume) item.bytesDownloaded else 0L
            val buffer = ByteArray(65536)
            var lastNotifyBytes = writeStartPos
            var lastSpeedBytes = item.bytesDownloaded
            var lastSpeedTime = System.currentTimeMillis()

            inputStream.use { input ->
                RandomAccessFile(outputFile, "rw").use { raf ->
                    raf.seek(writeStartPos)
                    while (true) {
                        if (ClintDownloadManager.pauseRequested.remove(item.id)) {
                            if (item.activeStartedAt > 0L) {
                                item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                                item.activeStartedAt = 0L
                            }
                            item.status = DownloadStatus.PAUSED
                            item.speedBytesPerSec = 0L
                            ClintDownloadManager.persistDownload(item)
                            ClintDownloadManager.onDownloadsChanged?.invoke()
                            context.getSystemService(NotificationManager::class.java).cancel(item.id)
                            if (item.id in DownloadNetworkMonitor.unmeteredPausedIds) {
                                DownloadNotificationHelper.showWaitingUnmeteredNotification(context, item)
                            } else {
                                DownloadNotificationHelper.showPausedNotification(context, item)
                            }
                            ClintDownloadManager.tryDequeueNext(context)
                            return
                        }
                        if (Thread.currentThread().isInterrupted) {
                            outputFile.delete()
                            ClintDownloadManager.persistDownload(item)
                            ClintDownloadManager.onDownloadsChanged?.invoke()
                            return
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        item.bytesDownloaded += read
                        if (item.bytesDownloaded - lastNotifyBytes > 65536) {
                            lastNotifyBytes = item.bytesDownloaded
                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSpeedTime
                            if (elapsed >= 400) {
                                val delta = item.bytesDownloaded - lastSpeedBytes
                                item.speedBytesPerSec = if (elapsed > 0) delta * 1000L / elapsed else 0L
                                lastSpeedBytes = item.bytesDownloaded
                                lastSpeedTime = now
                            }
                            DownloadNotificationHelper.showProgressNotification(context, item)
                            ClintDownloadManager.onDownloadsChanged?.invoke()
                        }
                    }
                }
            }

            if (safMode) {
                moveTempToSaf(context, item)
            } else {
                if (item.activeStartedAt > 0L) {
                    item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                    item.activeStartedAt = 0L
                }
                item.completedAt = System.currentTimeMillis()
                item.status = DownloadStatus.COMPLETE
                ClintDownloadManager.persistDownload(item)
                ClintDownloadManager.onDownloadsChanged?.invoke()
                DownloadNotificationHelper.showCompleteNotification(context, item)
                ClintDownloadManager.tryDequeueNext(context)
                scanCompletedFile(context, item)
            }
        } catch (e: Throwable) {
            if (ClintDownloadManager.pauseRequested.remove(item.id)) {
                if (item.activeStartedAt > 0L) {
                    item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                    item.activeStartedAt = 0L
                }
                item.status = DownloadStatus.PAUSED
                item.speedBytesPerSec = 0L
                ClintDownloadManager.persistDownload(item)
                ClintDownloadManager.onDownloadsChanged?.invoke()
                context.getSystemService(NotificationManager::class.java).cancel(item.id)
                if (item.id in DownloadNetworkMonitor.unmeteredPausedIds) {
                    DownloadNotificationHelper.showWaitingUnmeteredNotification(context, item)
                } else {
                    DownloadNotificationHelper.showPausedNotification(context, item)
                }
                ClintDownloadManager.tryDequeueNext(context)
            } else if (item.status != DownloadStatus.PAUSED) {
                fail(context, item, e.message ?: "Unknown error")
            }
        }
    }

    private fun runParallelDownload(
        context: Context,
        item: DownloadItem,
        outputFile: File,
        totalParts: Int,
        simultaneousParts: Int,
        safMode: Boolean
    ) {
        val partSize = item.totalBytes / totalParts
        val partBytesDownloaded = Array(totalParts) { AtomicLong(0L) }
        val partCompleted = Array(totalParts) { AtomicBoolean(false) }
        val firstError = AtomicReference<String?>(null)
        val latch = CountDownLatch(totalParts)
        val rateLimitDetected = AtomicBoolean(false)
        val rateLimitUntilMs = AtomicLong(0L)
        val maxSafeConcurrency = AtomicInteger(simultaneousParts)

        val tpe = ThreadPoolExecutor(
            1,
            simultaneousParts,
            1L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue()
        )
        tpe.allowCoreThreadTimeOut(true)

        for (i in 0 until totalParts) {
            val partIndex = i
            val start = partIndex.toLong() * partSize
            val end = if (partIndex == totalParts - 1) item.totalBytes - 1L else (partIndex.toLong() + 1L) * partSize - 1L

            tpe.submit {
                var attempt = 0
                while (attempt < MAX_PART_RETRIES) {
                    if (firstError.get() != null
                        || Thread.currentThread().isInterrupted
                        || ClintDownloadManager.pauseRequested.contains(item.id)
                        || rateLimitDetected.get()
                    ) break

                    val rateLimitWait = (rateLimitUntilMs.get() - System.currentTimeMillis()).coerceAtLeast(0L)
                    if (rateLimitWait > 0L) {
                        try { Thread.sleep(rateLimitWait) } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                        if (firstError.get() != null
                            || Thread.currentThread().isInterrupted
                            || ClintDownloadManager.pauseRequested.contains(item.id)
                            || rateLimitDetected.get()
                        ) break
                    }

                    try {
                        val request = Request.Builder()
                            .url(item.url)
                            .header("User-Agent", item.userAgent)
                            .header("Accept", "*/*")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("Range", "bytes=$start-$end")
                            .apply {
                                if (item.referer.isNotEmpty()) header("Referer", item.referer)
                                if (item.cookies.isNotEmpty()) header("Cookie", item.cookies)
                            }
                            .build()

                        val response = ClintDownloadManager.httpClient.newCall(request).execute()
                        if (response.code == 429) {
                            val retryAfterSec = response.header("Retry-After")?.trim()?.toLongOrNull()
                                ?: (5L shl attempt.coerceAtMost(4))
                            rateLimitUntilMs.updateAndGet { existing ->
                                maxOf(existing, System.currentTimeMillis() + retryAfterSec.coerceIn(1L, 300L) * 1000L)
                            }
                            response.close()
                            val current = tpe.corePoolSize
                            if (current > 1) {
                                maxSafeConcurrency.updateAndGet { minOf(it, current - 1) }
                                tpe.corePoolSize = current - 1
                            } else {
                                rateLimitDetected.set(true)
                                break
                            }
                            attempt++
                            continue
                        }
                        if (response.code != 206 && !response.isSuccessful) {
                            attempt++
                            if (attempt >= MAX_PART_RETRIES) {
                                firstError.compareAndSet(null, "Server error ${response.code}")
                            } else {
                                Thread.sleep(attempt * 500L)
                            }
                            continue
                        }

                        val body = response.body ?: run {
                            attempt++
                            if (attempt >= MAX_PART_RETRIES) {
                                firstError.compareAndSet(null, "Empty response on part $partIndex")
                            } else {
                                Thread.sleep(attempt * 500L)
                            }
                            continue
                        }

                        partBytesDownloaded[partIndex].set(0L)

                        val buffer = ByteArray(32768)
                        var partSucceeded = false
                        body.byteStream().use { input ->
                            RandomAccessFile(outputFile, "rw").use { raf ->
                                raf.seek(start)
                                while (true) {
                                    if (Thread.currentThread().isInterrupted
                                        || firstError.get() != null
                                        || ClintDownloadManager.pauseRequested.contains(item.id)
                                        || rateLimitDetected.get()
                                    ) break
                                    val read = input.read(buffer)
                                    if (read == -1) { partSucceeded = true; break }
                                    raf.write(buffer, 0, read)
                                    partBytesDownloaded[partIndex].addAndGet(read.toLong())
                                }
                            }
                        }
                        if (partSucceeded) partCompleted[partIndex].set(true)
                        break
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    } catch (e: Throwable) {
                        attempt++
                        if (attempt >= MAX_PART_RETRIES) {
                            firstError.compareAndSet(null, e.message ?: "Download error")
                        } else {
                            try { Thread.sleep(attempt * 500L) } catch (ie: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            }
                        }
                    }
                }
                latch.countDown()
            }
        }

        var lastSpeedTotal = 0L
        var lastSpeedTime = System.currentTimeMillis()
        var lastNotifyTotal = 0L
        var lastRampUpTime = System.currentTimeMillis()
        var lastDynamicTime = System.currentTimeMillis()
        var lastDynamicTotal = 0L
        var lastDynamicSpeed = 0L
        var didShutdown = false

        while (!latch.await(200, TimeUnit.MILLISECONDS)) {
            val total = partBytesDownloaded.sumOf { it.get() }
            item.bytesDownloaded = total

            if (total - lastNotifyTotal > 65536) {
                lastNotifyTotal = total
                val now = System.currentTimeMillis()
                val elapsed = now - lastSpeedTime
                if (elapsed >= 400) {
                    item.speedBytesPerSec = if (elapsed > 0) (total - lastSpeedTotal) * 1000L / elapsed else 0L
                    lastSpeedTotal = total
                    lastSpeedTime = now
                }
                DownloadNotificationHelper.showProgressNotification(context, item)
                ClintDownloadManager.onDownloadsChanged?.invoke()
            }

            val now = System.currentTimeMillis()

            if (now - lastRampUpTime >= RAMP_UP_INTERVAL_MS) {
                lastRampUpTime = now
                val current = tpe.corePoolSize
                val safe = maxSafeConcurrency.get()
                if (current < safe && now > rateLimitUntilMs.get()) {
                    tpe.corePoolSize = current + 1
                }
            }

            if (now - lastDynamicTime >= 2000) {
                val elapsed = now - lastDynamicTime
                val currentSpeed = if (elapsed > 0) (total - lastDynamicTotal) * 1000L / elapsed else 0L
                if (lastDynamicSpeed > 0 && currentSpeed > 0) {
                    val current = tpe.corePoolSize
                    val safe = maxSafeConcurrency.get()
                    when {
                        currentSpeed > lastDynamicSpeed * 1.1 && current < safe -> {
                            tpe.corePoolSize = current + 1
                        }
                        currentSpeed < lastDynamicSpeed * 0.9 && current > 1 -> {
                            tpe.corePoolSize = (current - 1).coerceAtLeast(1)
                        }
                    }
                }
                lastDynamicTotal = total
                lastDynamicSpeed = currentSpeed
                lastDynamicTime = now
            }

            val shouldStop = ClintDownloadManager.pauseRequested.contains(item.id)
                || firstError.get() != null
                || Thread.currentThread().isInterrupted
                || rateLimitDetected.get()

            if (shouldStop) {
                tpe.shutdownNow()
                didShutdown = true
                break
            }
        }

        tpe.shutdown()
        if (didShutdown) {
            try { latch.await(5, TimeUnit.SECONDS) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }

        if (ClintDownloadManager.pauseRequested.remove(item.id)) {
            var firstIncompleteIdx = 0
            while (firstIncompleteIdx < totalParts && partCompleted[firstIncompleteIdx].get()) firstIncompleteIdx++
            val resumeOffset = firstIncompleteIdx.toLong() * partSize
            if (resumeOffset > 0L && firstIncompleteIdx < totalParts) {
                item.bytesDownloaded = resumeOffset
            } else {
                outputFile.delete()
                item.file = null
                item.bytesDownloaded = 0L
            }
            if (item.activeStartedAt > 0L) {
                item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                item.activeStartedAt = 0L
            }
            item.status = DownloadStatus.PAUSED
            item.speedBytesPerSec = 0L
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            context.getSystemService(NotificationManager::class.java).cancel(item.id)
            if (item.id in DownloadNetworkMonitor.unmeteredPausedIds) {
                DownloadNotificationHelper.showWaitingUnmeteredNotification(context, item)
            } else {
                DownloadNotificationHelper.showPausedNotification(context, item)
            }
            ClintDownloadManager.tryDequeueNext(context)
            return
        }

        if (rateLimitDetected.get() && !Thread.currentThread().isInterrupted && firstError.get() == null) {
            var firstIncompleteForRateLimit = 0
            while (firstIncompleteForRateLimit < totalParts && partCompleted[firstIncompleteForRateLimit].get()) firstIncompleteForRateLimit++
            val rateLimitResumeOffset = firstIncompleteForRateLimit.toLong() * partSize
            if (rateLimitResumeOffset > 0L && firstIncompleteForRateLimit < totalParts) {
                item.bytesDownloaded = rateLimitResumeOffset
            } else {
                item.bytesDownloaded = partBytesDownloaded[0].get()
            }
            item.parallelRateLimited = true
            item.speedBytesPerSec = 0L
            if (item.activeStartedAt > 0L) {
                item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                item.activeStartedAt = 0L
            }
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            val waitMs = (rateLimitUntilMs.get() - System.currentTimeMillis()).coerceAtLeast(0L)
            if (waitMs > 0L) {
                try { Thread.sleep(waitMs) } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
            if (!Thread.currentThread().isInterrupted) {
                runDownload(context, item)
            }
            return
        }

        if (Thread.currentThread().isInterrupted) {
            outputFile.delete()
            item.file = null
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            return
        }

        val error = firstError.get()
        if (error != null) {
            var firstIncompleteIdx = 0
            while (firstIncompleteIdx < totalParts && partCompleted[firstIncompleteIdx].get()) firstIncompleteIdx++
            val resumeOffset = firstIncompleteIdx.toLong() * partSize
            if (resumeOffset > 0L && firstIncompleteIdx < totalParts) {
                item.bytesDownloaded = resumeOffset
            } else {
                item.bytesDownloaded = partBytesDownloaded[0].get()
            }
            fail(context, item, error)
            return
        }

        item.bytesDownloaded = item.totalBytes

        if (safMode) {
            moveTempToSaf(context, item)
        } else {
            if (item.activeStartedAt > 0L) {
                item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                item.activeStartedAt = 0L
            }
            item.completedAt = System.currentTimeMillis()
            item.status = DownloadStatus.COMPLETE
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showCompleteNotification(context, item)
            ClintDownloadManager.tryDequeueNext(context)
            scanCompletedFile(context, item)
        }
    }

    fun preAllocateFile(context: Context, item: DownloadItem, file: File): Boolean {
        item.status = DownloadStatus.ALLOCATING
        item.allocationProgress = 0
        ClintDownloadManager.persistDownload(item)
        ClintDownloadManager.onDownloadsChanged?.invoke()
        DownloadNotificationHelper.showAllocationNotification(context, item)

        return try {
            if (Thread.currentThread().isInterrupted || item.id in ClintDownloadManager.removedIds) {
                file.delete()
                ClintDownloadManager.persistDownload(item)
                ClintDownloadManager.onDownloadsChanged?.invoke()
                return false
            }
            RandomAccessFile(file, "rw").use { raf -> raf.setLength(item.totalBytes) }
            item.allocationProgress = 100
            ClintDownloadManager.onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showAllocationNotification(context, item)
            true
        } catch (e: Throwable) {
            file.delete()
            fail(context, item, e.message ?: context.getString(R.string.download_error_unknown), scheduleRetry = false)
            false
        }
    }

    private fun isServerError(msg: String): Boolean {
        val code = Regex("""Server error (\d+)""").find(msg)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        return code in 400..499 && code != 429
    }

    fun fail(context: Context, item: DownloadItem, msg: String, scheduleRetry: Boolean = true) {
        val ctx = ClintDownloadManager.appContext ?: context

        if (!isServerError(msg) &&
            item.id !in ClintDownloadManager.removedIds &&
            !DownloadNetworkMonitor.isNetworkAvailable(ctx)
        ) {
            if (item.activeStartedAt > 0L) {
                item.activeElapsedMs += System.currentTimeMillis() - item.activeStartedAt
                item.activeStartedAt = 0L
            }
            item.status = DownloadStatus.PAUSED
            item.waitingForNetwork = true
            item.speedBytesPerSec = 0L
            item.retryAttempt = 0
            item.retryDelaySec = 0
            item.errorMessage = null
            DownloadNetworkMonitor.networkWaitingIds.add(item.id)
            context.getSystemService(NotificationManager::class.java).cancel(item.id)
            DownloadNotificationHelper.showWaitingNetworkNotification(ctx, item)
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            ClintDownloadManager.tryDequeueNext(ctx)
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val retryEnabled = item.retryEnabled
        val retryUnrecoverable = prefs.getBoolean(
            DownloadSettingsFragment.PREF_RETRY_UNRECOVERABLE,
            DownloadSettingsFragment.DEFAULT_RETRY_UNRECOVERABLE
        )
        val retryCount = prefs.getInt(
            DownloadSettingsFragment.PREF_RETRY_COUNT,
            DownloadSettingsFragment.DEFAULT_RETRY_COUNT
        )
        val retryInterval = prefs.getInt(
            DownloadSettingsFragment.PREF_RETRY_INTERVAL,
            DownloadSettingsFragment.DEFAULT_RETRY_INTERVAL
        ).toLong()

        val serverError = isServerError(msg)
        val canRetry = scheduleRetry &&
            retryEnabled &&
            (retryUnrecoverable || !serverError) &&
            item.id !in ClintDownloadManager.removedIds &&
            (retryCount == 0 || item.retryAttempt < retryCount)

        if (canRetry) {
            item.retryAttempt++
            item.status = DownloadStatus.RETRYING
            item.retryDelaySec = retryInterval.toInt()
            item.lastErrorWasServerError = serverError
            item.errorMessage = null
            item.speedBytesPerSec = 0L
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            if (item.retryAttempt == 1) {
                DownloadNotificationHelper.showRetryingNotification(ctx, item)
            }
            ClintDownloadManager.scheduledExecutor.schedule({
                if (item.id !in ClintDownloadManager.removedIds && item.status == DownloadStatus.RETRYING) {
                    if (ClintDownloadManager.pauseRequested.remove(item.id)) {
                        item.status = DownloadStatus.PAUSED
                        item.retryDelaySec = 0
                        ClintDownloadManager.persistDownload(item)
                        ClintDownloadManager.onDownloadsChanged?.invoke()
                        return@schedule
                    }
                    item.retryDelaySec = 0
                    val future = ClintDownloadManager.executor.submit { runDownload(context, item) }
                    ClintDownloadManager.futures[item.id] = future
                }
            }, retryInterval, TimeUnit.SECONDS)
        } else {
            item.retryAttempt = 0
            item.status = DownloadStatus.FAILED
            item.lastErrorWasServerError = serverError
            item.errorMessage = msg
            ClintDownloadManager.persistDownload(item)
            ClintDownloadManager.onDownloadsChanged?.invoke()
            DownloadNotificationHelper.showFailedNotification(context, item)
            ClintDownloadManager.tryDequeueNext(context)
        }
    }

    private fun scanCompletedFile(context: Context, item: DownloadItem) {
        val file = item.file
        if (file != null && file.exists()) {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            return
        }
        val contentUri = item.contentUri ?: return
        val path = try {
            val uri = Uri.parse(contentUri)
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":")
            if (parts.size == 2 && parts[0].equals("primary", ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory().absolutePath}/${parts[1]}"
            } else null
        } catch (_: Throwable) { null } ?: return
        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
    }
}

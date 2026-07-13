package com.jhaiian.clint.quiver

import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.jhaiian.clint.R
import com.jhaiian.clint.downloads.ClintDownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

// Sealed hierarchy emitted by FilterListDownloader.download to communicate
// progress and the final result back to the UI without callbacks.
sealed class FilterListDownloadProgress {
    data class Progress(val bytesRead: Long, val totalBytes: Long) : FilterListDownloadProgress()
    data class Success(
        val file: File,
        val bytesTotal: Long,
        val ruleCount: Long,
        // HTTP caching headers captured from the response for future conditional requests.
        val etag: String? = null,
        val lastModified: String? = null
    ) : FilterListDownloadProgress()
}

class FilterListDownloadException(message: String) : Exception(message)

internal object FilterListDownloader {

    // Progress events are throttled to avoid flooding the main thread with
    // updates when downloading large lists over a fast connection.
    private const val PROGRESS_EMIT_INTERVAL_MS = 80L

    // Returns the canonical on-disk path for a filter list identified by its
    // database row ID. The file lives inside the app's private files directory
    // so it is not accessible to other apps and is preserved across app updates.
    fun localFileFor(context: Context, filterListId: Long): File {
        val dir = File(context.applicationContext.filesDir, "quiver_guard")
        return File(dir, "filter_list_$filterListId.txt")
    }

    // Counts non-blank, non-comment lines in a downloaded filter file to report
    // an approximate rule count in the UI. Comments start with '!' and section
    // headers are bracketed lines like "[Adblock Plus 2.0]".
    private fun countRules(file: File): Long {
        var count = 0L
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("!")) continue
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) continue
                count++
            }
        }
        return count
    }

    // Downloads the filter list to a temporary file first and then atomically
    // moves it to the final location. Writing to a .part file means that a
    // partially downloaded file never replaces a previously working one if the
    // download is interrupted.
    // The browser's cookie jar and user-agent string are forwarded so servers
    // that gate list access behind authentication can still be reached.
    fun download(context: Context, filterList: FilterList): Flow<FilterListDownloadProgress> = flow {
        val appContext = context.applicationContext
        val targetFile = localFileFor(appContext, filterList.id)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        targetFile.parentFile?.mkdirs()

        val cookie = try {
            CookieManager.getInstance().getCookie(filterList.downloadUrl)
        } catch (_: Exception) {
            null
        }
        val userAgent = WebSettings.getDefaultUserAgent(appContext)

        val requestBuilder = Request.Builder()
            .url(filterList.downloadUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "text/plain, */*;q=0.8")
        if (!cookie.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookie)
        }

        val call = ClintDownloadManager.httpClient.newCall(requestBuilder.build())
        // Cancel the OkHttp call when the coroutine is cancelled so the
        // underlying socket is released promptly instead of waiting for a timeout.
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw FilterListDownloadException(
                        appContext.getString(R.string.quiver_guard_download_error_http, response.code)
                    )
                }
                val body = response.body
                    ?: throw FilterListDownloadException(
                        appContext.getString(R.string.quiver_guard_download_error_empty)
                    )

                val responseEtag = response.header("ETag")
                val responseLastModified = response.header("Last-Modified")

                val totalBytes = body.contentLength()
                var bytesRead = 0L
                var lastEmitMillis = 0L
                emit(FilterListDownloadProgress.Progress(0L, totalBytes))

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastEmitMillis >= PROGRESS_EMIT_INTERVAL_MS || bytesRead == totalBytes) {
                                lastEmitMillis = now
                                emit(FilterListDownloadProgress.Progress(bytesRead, totalBytes))
                            }
                        }
                        output.flush()
                    }
                }

                // Prefer an atomic rename for reliability. Fall back to copy-and-delete
                // on file systems where rename across directories is not supported.
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                currentCoroutineContext().ensureActive()
                val ruleCount = countRules(targetFile)
                emit(FilterListDownloadProgress.Success(
                    targetFile,
                    targetFile.length(),
                    ruleCount,
                    responseEtag,
                    responseLastModified
                ))
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: FilterListDownloadException) {
            tempFile.delete()
            throw e
        } catch (_: Exception) {
            tempFile.delete()
            throw FilterListDownloadException(appContext.getString(R.string.quiver_guard_download_error_network))
        }
    }.flowOn(Dispatchers.IO)
}

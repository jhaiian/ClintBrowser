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

// Result of checking a single filter list for updates.
sealed class FilterListUpdateItemResult {
    // The list was skipped because it has not been downloaded yet.
    data class Skipped(val filterList: FilterList) : FilterListUpdateItemResult()
    // The server confirmed the list has not changed (HTTP 304 or matching ETag).
    data class UpToDate(val filterList: FilterList) : FilterListUpdateItemResult()
    data class Updated(
        val filterList: FilterList,
        val newRuleCount: Long,
        val newFileSizeBytes: Long,
        val newEtag: String?,
        val newLastModified: String?
    ) : FilterListUpdateItemResult()
    data class Failed(val filterList: FilterList, val message: String) : FilterListUpdateItemResult()
}

// Events emitted by FilterListUpdateChecker.checkAndUpdateAll as each list is processed.
sealed class FilterListUpdateEvent {
    data class CheckingList(
        val filterList: FilterList,
        val index: Int,
        val total: Int
    ) : FilterListUpdateEvent()
    data class DownloadingList(
        val filterList: FilterList,
        val bytesRead: Long,
        val totalBytes: Long
    ) : FilterListUpdateEvent()
    data class ItemComplete(val result: FilterListUpdateItemResult) : FilterListUpdateEvent()
}

internal object FilterListUpdateChecker {

    // A file shorter than 100 bytes or with fewer than 3 non-empty lines cannot
    // be a valid filter list and is rejected to avoid replacing a working file
    // with a server-side error response.
    private const val MIN_VALID_FILE_BYTES = 100L
    private const val MIN_VALID_LINE_COUNT = 3
    private const val PROGRESS_EMIT_INTERVAL_MS = 80L

    // Iterates through every downloaded filter list sequentially, emitting
    // progress events for each one so the update dialog can show which list is
    // currently being checked. Lists that have never been downloaded are skipped.
    // When forceUpdate is true, conditional HTTP headers are omitted so the server
    // always responds with full content rather than 304, and the ETag equality
    // short-circuit in the response path is also bypassed.
    fun checkAndUpdateAll(
        context: Context,
        filterLists: List<FilterList>,
        forceUpdate: Boolean = false
    ): Flow<FilterListUpdateEvent> = flow {
        val appContext = context.applicationContext
        val downloadedLists = filterLists.filter { it.isDownloaded }
        val total = downloadedLists.size

        for ((index, filterList) in downloadedLists.withIndex()) {
            currentCoroutineContext().ensureActive()
            emit(FilterListUpdateEvent.CheckingList(filterList, index, total))

            val result = tryCheckAndUpdate(appContext, filterList, forceUpdate) { bytesRead, totalBytes ->
                emit(FilterListUpdateEvent.DownloadingList(filterList, bytesRead, totalBytes))
            }
            currentCoroutineContext().ensureActive()
            emit(FilterListUpdateEvent.ItemComplete(result))
        }
    }.flowOn(Dispatchers.IO)

    // Performs an HTTP request for a single filter list. When forceUpdate is false,
    // a conditional request is made using the ETag or Last-Modified header from the
    // previous download so the server can respond with 304 when the list has not changed.
    // When forceUpdate is true, no conditional headers are sent and the ETag equality
    // short-circuit in the response is also skipped, guaranteeing a full re-download
    // and ensuring the result is always Updated (not UpToDate) on success.
    private suspend fun tryCheckAndUpdate(
        context: Context,
        filterList: FilterList,
        forceUpdate: Boolean = false,
        onDownloadProgress: suspend (Long, Long) -> Unit
    ): FilterListUpdateItemResult {
        val targetFile = FilterListDownloader.localFileFor(context, filterList.id)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.update.part")

        return try {
            val cookie = try {
                CookieManager.getInstance().getCookie(filterList.downloadUrl)
            } catch (_: Exception) {
                null
            }
            val userAgent = WebSettings.getDefaultUserAgent(context)

            val requestBuilder = Request.Builder()
                .url(filterList.downloadUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/plain, */*;q=0.8")

            if (!cookie.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookie)
            }
            // Conditional headers are only sent for regular update checks. Force update
            // omits them so the server always returns the full current content.
            if (!forceUpdate) {
                if (!filterList.etag.isNullOrBlank()) {
                    requestBuilder.header("If-None-Match", filterList.etag)
                } else if (!filterList.lastModified.isNullOrBlank()) {
                    requestBuilder.header("If-Modified-Since", filterList.lastModified)
                }
            }

            val call = ClintDownloadManager.httpClient.newCall(requestBuilder.build())
            currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

            call.execute().use { response ->
                if (response.code == 304) {
                    return FilterListUpdateItemResult.UpToDate(filterList)
                }

                if (!response.isSuccessful) {
                    return FilterListUpdateItemResult.Failed(
                        filterList,
                        context.getString(R.string.quiver_guard_download_error_http, response.code)
                    )
                }

                val body = response.body
                    ?: return FilterListUpdateItemResult.Failed(
                        filterList,
                        context.getString(R.string.quiver_guard_download_error_empty)
                    )

                val responseEtag = response.header("ETag")
                val responseLastModified = response.header("Last-Modified")

                // Some servers return 200 with the same ETag instead of 304. For a
                // regular check this is treated as up-to-date to avoid an unnecessary
                // file replacement. For force update this check is skipped so the file
                // is always replaced and recompilation is always triggered.
                if (!forceUpdate &&
                    !responseEtag.isNullOrBlank() &&
                    responseEtag == filterList.etag &&
                    !filterList.etag.isNullOrBlank()
                ) {
                    return FilterListUpdateItemResult.UpToDate(filterList)
                }

                val totalBytes = body.contentLength()
                var bytesRead = 0L
                var lastEmitMillis = 0L
                tempFile.parentFile?.mkdirs()
                onDownloadProgress(0L, totalBytes)

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
                            if (now - lastEmitMillis >= PROGRESS_EMIT_INTERVAL_MS) {
                                lastEmitMillis = now
                                onDownloadProgress(bytesRead, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                // Validate the downloaded content before replacing the working file.
                // This prevents a server-side error page from wiping out a valid list.
                if (!isValidFilterFile(tempFile)) {
                    tempFile.delete()
                    return FilterListUpdateItemResult.Failed(
                        filterList,
                        context.getString(R.string.filter_list_update_validation_failed, filterList.name)
                    )
                }

                val newRuleCount = countFilterRules(tempFile)
                val newFileSize = tempFile.length()

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                FilterListUpdateItemResult.Updated(
                    filterList,
                    newRuleCount,
                    newFileSize,
                    responseEtag,
                    responseLastModified
                )
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (_: Exception) {
            tempFile.delete()
            FilterListUpdateItemResult.Failed(
                filterList,
                context.getString(R.string.quiver_guard_download_error_network)
            )
        }
    }

    // A filter list is considered valid when the file exists, is at least 100 bytes,
    // and contains at least three non-empty lines. This catches truncated downloads
    // and HTML error pages without doing a full parse.
    private fun isValidFilterFile(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_VALID_FILE_BYTES) return false
        var lineCount = 0
        return try {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isNotBlank()) lineCount++
                    if (lineCount >= MIN_VALID_LINE_COUNT) return@useLines
                }
            }
            lineCount > 0
        } catch (_: Exception) {
            false
        }
    }

    // Counts actionable rules in the file. Comment lines (starting with '!') and
    // section headers (bracketed lines) are excluded from the count.
    private fun countFilterRules(file: File): Long {
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
}

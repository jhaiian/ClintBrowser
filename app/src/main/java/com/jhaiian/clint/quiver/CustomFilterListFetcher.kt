package com.jhaiian.clint.quiver

import android.content.Context
import android.net.Uri
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
import java.util.UUID

// Sealed hierarchy emitted by CustomFilterListFetcher.fetch to report byte-level
// download progress and a final parsed result back to the add-list dialog.
sealed class CustomFilterListFetchProgress {
    data class Progress(val bytesRead: Long, val totalBytes: Long) : CustomFilterListFetchProgress()
    data class Success(
        val file: File,
        val bytesTotal: Long,
        val ruleCount: Long,
        // Key/value pairs extracted from the list header (e.g. "Title", "Version").
        val metadata: Map<String, String>
    ) : CustomFilterListFetchProgress()
}

class CustomFilterListFetchException(message: String) : Exception(message)

internal object CustomFilterListFetcher {

    // Progress events are throttled so the dialog update rate stays reasonable
    // even when downloading from a fast local connection.
    private const val PROGRESS_EMIT_INTERVAL_MS = 80L

    // Validates that the string is a well-formed HTTP or HTTPS URL with a non-empty host.
    fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        val host = Uri.parse(trimmed).host
        return !host.isNullOrBlank()
    }

    // Creates a unique temporary file in the app's cache directory. Using a UUID
    // name ensures concurrent fetches do not collide with each other.
    private fun tempFileFor(context: Context): File {
        val dir = File(context.applicationContext.cacheDir, "quiver_guard_fetch")
        dir.mkdirs()
        return File(dir, "fetch_${UUID.randomUUID()}.txt")
    }

    // Downloads the URL to a temporary file, validates that it is a filter list
    // (not an HTML error page and not empty), and emits progress updates at a
    // throttled rate. The caller is responsible for moving the file to its final
    // location on success.
    fun fetch(context: Context, url: String): Flow<CustomFilterListFetchProgress> = flow {
        val appContext = context.applicationContext
        val tempFile = tempFileFor(appContext)

        val cookie = try {
            CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }
        val userAgent = WebSettings.getDefaultUserAgent(appContext)

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/plain, */*;q=0.8")
        if (!cookie.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookie)
        }

        val call = ClintDownloadManager.httpClient.newCall(requestBuilder.build())
        // Tie the OkHttp call lifetime to the coroutine so cancellation releases
        // the socket without waiting for a network timeout.
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw CustomFilterListFetchException(
                        appContext.getString(R.string.quiver_guard_download_error_http, response.code)
                    )
                }
                val body = response.body
                    ?: throw CustomFilterListFetchException(
                        appContext.getString(R.string.quiver_guard_download_error_empty)
                    )

                val totalBytes = body.contentLength()
                var bytesRead = 0L
                var lastEmitMillis = 0L
                emit(CustomFilterListFetchProgress.Progress(0L, totalBytes))

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
                                emit(CustomFilterListFetchProgress.Progress(bytesRead, totalBytes))
                            }
                        }
                        output.flush()
                    }
                }

                currentCoroutineContext().ensureActive()

                if (FilterListContentValidator.looksLikeHtml(tempFile)) {
                    throw CustomFilterListFetchException(
                        appContext.getString(R.string.filter_list_add_error_invalid_format)
                    )
                }

                val analysis = FilterListContentValidator.analyzeFile(tempFile)
                // Reject files that contain no actionable rules; an empty file cannot
                // contribute anything useful to the compiled database.
                if (analysis.ruleCount <= 0L) {
                    throw CustomFilterListFetchException(
                        appContext.getString(R.string.filter_list_add_error_invalid_format)
                    )
                }

                emit(
                    CustomFilterListFetchProgress.Success(
                        tempFile,
                        tempFile.length(),
                        analysis.ruleCount,
                        analysis.metadata
                    )
                )
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: CustomFilterListFetchException) {
            tempFile.delete()
            throw e
        } catch (_: Exception) {
            tempFile.delete()
            throw CustomFilterListFetchException(appContext.getString(R.string.quiver_guard_download_error_network))
        }
    }.flowOn(Dispatchers.IO)
}

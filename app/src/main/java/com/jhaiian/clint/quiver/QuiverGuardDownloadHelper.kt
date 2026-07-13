package com.jhaiian.clint.quiver

import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jhaiian.clint.R
import com.jhaiian.clint.downloads.formatFileSize
import com.jhaiian.clint.ui.ClintToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Launches a download coroutine for the given filter list, shows a progress
// dialog with byte-level progress, and persists the result to the database on
// success. The cancel button on the dialog cancels the coroutine, which in turn
// cancels the OkHttp call and deletes the partial download file.
internal fun QuiverGuardActivity.startFilterListDownload(filterList: FilterList) {
    // Prevent duplicate downloads if the user taps the download button twice.
    if (isDownloadInProgress(filterList.id)) return
    val activity = this
    markDownloading(filterList.id, true)

    val progressView = layoutInflater.inflate(R.layout.dialog_quiver_guard_download_progress, null)
    val progressBar = progressView.findViewById<LinearProgressIndicator>(R.id.quiver_download_progress_bar)
    val progressText = progressView.findViewById<TextView>(R.id.quiver_download_progress_text)
    // Start indeterminate until the content-length header is received.
    progressBar.isIndeterminate = true
    progressText.text = getString(R.string.quiver_guard_download_progress_starting)

    var downloadJob: Job? = null

    val dialog = MaterialAlertDialogBuilder(activity, getDialogTheme())
        .setTitle(getString(R.string.quiver_guard_download_dialog_title, filterList.name))
        .setView(progressView)
        .setCancelable(false)
        .setNegativeButton(getString(R.string.action_cancel)) { _, _ -> downloadJob?.cancel() }
        .create()
        .also { applyStatusBarFlagToDialog(it) }
    dialog.show()

    downloadJob = activityScope.launch {
        var didSucceed = false
        try {
            FilterListDownloader.download(applicationContext, filterList).collect { progress ->
                when (progress) {
                    is FilterListDownloadProgress.Progress -> {
                        if (progress.totalBytes > 0) {
                            // Switch the progress bar from indeterminate to determinate
                            // once we know the content length.
                            progressBar.isIndeterminate = false
                            val percent = ((progress.bytesRead * 100) / progress.totalBytes).toInt()
                            progressBar.progress = percent
                            progressText.text = getString(
                                R.string.quiver_guard_download_progress_known,
                                formatFileSize(progress.bytesRead),
                                formatFileSize(progress.totalBytes),
                                percent
                            )
                        } else {
                            progressBar.isIndeterminate = true
                            progressText.text = getString(
                                R.string.quiver_guard_download_progress_unknown,
                                formatFileSize(progress.bytesRead)
                            )
                        }
                    }
                    is FilterListDownloadProgress.Success -> {
                        didSucceed = true
                        val downloadedAt = System.currentTimeMillis()
                        // Persist all download metadata including ETag and Last-Modified for future
                        // conditional HTTP requests during update checks.
                        database().updateDownloadResult(
                            filterList.id,
                            progress.file.absolutePath,
                            progress.bytesTotal,
                            downloadedAt,
                            progress.ruleCount,
                            progress.etag,
                            progress.lastModified
                        )
                        // Auto-enable the list after a successful download so it is immediately
                        // available for the user to include in the next compile.
                        onFilterListDownloaded(
                            filterList.copy(
                                localPath = progress.file.absolutePath,
                                fileSizeBytes = progress.bytesTotal,
                                downloadedAt = downloadedAt,
                                ruleCount = progress.ruleCount,
                                isEnabled = true,
                                etag = progress.etag,
                                lastModified = progress.lastModified
                            )
                        )
                    }
                }
            }
            if (didSucceed) {
                dialog.dismiss()
                ClintToast.show(activity, getString(R.string.quiver_guard_download_success_toast, filterList.name), R.drawable.ic_check_24)
            } else {
                dialog.dismiss()
                ClintToast.show(activity, getString(R.string.quiver_guard_download_error_toast, filterList.name), R.drawable.ic_warning_24)
            }
        } catch (e: CancellationException) {
            dialog.dismiss()
            ClintToast.show(activity, getString(R.string.quiver_guard_download_cancelled_toast), R.drawable.ic_warning_24)
            throw e
        } catch (e: FilterListDownloadException) {
            dialog.dismiss()
            ClintToast.show(activity, e.message ?: getString(R.string.quiver_guard_download_error_toast, filterList.name), R.drawable.ic_warning_24)
        } finally {
            markDownloading(filterList.id, false)
        }
    }
}

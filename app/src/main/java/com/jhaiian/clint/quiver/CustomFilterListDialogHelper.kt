package com.jhaiian.clint.quiver

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jhaiian.clint.R
import com.jhaiian.clint.downloads.formatFileSize
import com.jhaiian.clint.ui.ClintToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Shows a two-stage dialog for adding a custom filter list:
//
//   Stage 1 – "Fetch" button: user enters a URL, the list is downloaded to a
//   temp file and validated. The URL field shows an error if the URL is not
//   valid HTTP(S) or if the fetch fails.
//
//   Stage 2 – "Add" button: the title field is pre-filled from the list header
//   metadata if available. User confirms, the temp file is moved to its final
//   location, the database row is created, and the list is added to the adapter.
//
// The dialog is non-cancellable during an active fetch to prevent the user from
// dismissing while a coroutine is mid-download. On dismiss the temp file is
// always deleted so no orphan files accumulate in the cache directory.
internal fun QuiverGuardActivity.showAddCustomFilterListDialog() {
    val activity = this
    val dialogView = layoutInflater.inflate(R.layout.dialog_add_filter_list, null)

    val tilUrl = dialogView.findViewById<TextInputLayout>(R.id.til_filter_list_url)
    val etUrl = dialogView.findViewById<TextInputEditText>(R.id.et_filter_list_url)
    val etTitle = dialogView.findViewById<TextInputEditText>(R.id.et_filter_list_title)
    val progressBar = dialogView.findViewById<LinearProgressIndicator>(R.id.filter_list_add_progress_bar)
    val progressText = dialogView.findViewById<TextView>(R.id.filter_list_add_progress_text)

    var isFetched = false
    var fetchedFile: File? = null
    var fetchedSize = 0L
    var fetchedRuleCount = 0L
    var fetchJob: Job? = null

    val dialog = MaterialAlertDialogBuilder(activity, getDialogTheme())
        .setTitle(getString(R.string.filter_list_add_dialog_title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.action_cancel)) { _, _ -> fetchJob?.cancel() }
        .setPositiveButton(getString(R.string.filter_list_add_action_fetch), null)
        .create()
        .also { applyStatusBarFlagToDialog(it) }

    // Delete the temp file whenever the dialog is dismissed to ensure no partial
    // download survives a cancellation or an error.
    dialog.setOnDismissListener {
        fetchJob?.cancel()
        fetchedFile?.delete()
    }

    dialog.setOnShowListener {
        val positiveBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        val negativeBtn = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
        positiveBtn.isEnabled = false

        // The positive button stays enabled only when the URL or title field is
        // non-empty depending on which stage is active.
        fun updateButtonEnabled() {
            val urlText = etUrl.text?.toString()?.trim().orEmpty()
            val titleText = etTitle.text?.toString()?.trim().orEmpty()
            positiveBtn.isEnabled = if (isFetched) titleText.isNotEmpty() else urlText.isNotEmpty()
        }

        // Resets to Stage 1 when the user edits the URL after a completed fetch.
        fun resetFetchState() {
            fetchedFile?.delete()
            fetchedFile = null
            fetchedSize = 0L
            fetchedRuleCount = 0L
            isFetched = false
            positiveBtn.text = getString(R.string.filter_list_add_action_fetch)
        }

        // Disables all inputs and shows the progress bar while a download is active.
        fun setFetching(active: Boolean) {
            positiveBtn.isEnabled = !active
            negativeBtn.isEnabled = true
            etUrl.isEnabled = !active
            etTitle.isEnabled = !active
            dialog.setCancelable(!active)
            progressBar.visibility = if (active) View.VISIBLE else View.GONE
            progressText.visibility = if (active) View.VISIBLE else View.GONE
            if (active) {
                progressBar.isIndeterminate = true
                progressText.text = getString(R.string.quiver_guard_download_progress_starting)
            }
        }

        // Called on a successful fetch. Advances to Stage 2, pre-fills the title
        // from the "Title:" metadata comment if present.
        fun onFetchSuccess(file: File, sizeBytes: Long, ruleCount: Long, metadata: Map<String, String>) {
            isFetched = true
            fetchedFile = file
            fetchedSize = sizeBytes
            fetchedRuleCount = ruleCount
            val titleFromMetadata = metadata.entries
                .firstOrNull { it.key.equals("Title", ignoreCase = true) }
                ?.value
            if (!titleFromMetadata.isNullOrBlank()) {
                etTitle.setText(titleFromMetadata)
            }
            tilUrl.error = null
            positiveBtn.text = getString(R.string.filter_list_add_action_add)
            setFetching(false)
            updateButtonEnabled()
        }

        // Resets to Stage 1 and displays the error message below the URL field.
        fun onFetchError(message: String) {
            fetchedFile?.delete()
            fetchedFile = null
            isFetched = false
            positiveBtn.text = getString(R.string.filter_list_add_action_fetch)
            tilUrl.error = message
            setFetching(false)
            updateButtonEnabled()
        }

        // Launches the CustomFilterListFetcher coroutine, converting the flow
        // progress events into UI updates.
        fun doFetch(url: String) {
            tilUrl.error = null
            setFetching(true)
            fetchJob = activityScope.launch {
                try {
                    CustomFilterListFetcher.fetch(applicationContext, url).collect { progress ->
                        when (progress) {
                            is CustomFilterListFetchProgress.Progress -> {
                                if (progress.totalBytes > 0) {
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
                            is CustomFilterListFetchProgress.Success -> {
                                onFetchSuccess(progress.file, progress.bytesTotal, progress.ruleCount, progress.metadata)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    setFetching(false)
                    updateButtonEnabled()
                    throw e
                } catch (e: CustomFilterListFetchException) {
                    onFetchError(e.message ?: getString(R.string.quiver_guard_download_error_network))
                }
            }
        }

        positiveBtn.setOnClickListener {
            if (isFetched) {
                // Stage 2: copy temp file to permanent location and persist the database row.
                val file = fetchedFile ?: return@setOnClickListener
                val title = etTitle.text?.toString()?.trim().orEmpty()
                if (title.isEmpty()) return@setOnClickListener
                val url = etUrl.text?.toString()?.trim().orEmpty()
                val sizeBytes = fetchedSize
                val ruleCount = fetchedRuleCount
                positiveBtn.isEnabled = false
                negativeBtn.isEnabled = false
                dialog.setCancelable(false)
                activityScope.launch {
                    try {
                        val added = withContext(Dispatchers.IO) {
                            val newId = database().addCustomFilterList(title, url)
                            if (newId <= 0L) throw IllegalStateException()
                            val targetFile = FilterListDownloader.localFileFor(applicationContext, newId)
                            targetFile.parentFile?.mkdirs()
                            // Copy rather than rename because the temp file may be
                            // in a different filesystem partition (cache vs files).
                            file.copyTo(targetFile, overwrite = true)
                            file.delete()
                            val downloadedAt = System.currentTimeMillis()
                            database().updateDownloadResult(newId, targetFile.absolutePath, sizeBytes, downloadedAt, ruleCount)
                            FilterList(
                                id = newId, name = title, downloadUrl = url,
                                isEnabled = false,
                                localPath = targetFile.absolutePath,
                                fileSizeBytes = sizeBytes, downloadedAt = downloadedAt,
                                ruleCount = ruleCount, isCustom = true
                            )
                        }
                        // Null out the local reference so the dismiss listener does not
                        // delete the file that was just moved to its final location.
                        fetchedFile = null
                        onFilterListAdded(added)
                        dialog.dismiss()
                        ClintToast.show(activity, getString(R.string.filter_list_add_success_toast, title), R.drawable.ic_check_24)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        positiveBtn.isEnabled = true
                        negativeBtn.isEnabled = true
                        dialog.setCancelable(true)
                        ClintToast.show(activity, getString(R.string.filter_list_add_error_save), R.drawable.ic_warning_24)
                    }
                }
            } else {
                // Stage 1: validate and fetch.
                val url = etUrl.text?.toString()?.trim().orEmpty()
                if (!CustomFilterListFetcher.isValidUrl(url)) {
                    tilUrl.error = getString(R.string.filter_list_add_error_invalid_url)
                    return@setOnClickListener
                }
                doFetch(url)
            }
        }

        // Editing the URL after a successful fetch resets to Stage 1 so the user
        // fetches the new URL before they can add it.
        etUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFetched) {
                    resetFetchState()
                }
                tilUrl.error = null
                updateButtonEnabled()
            }
        })

        etTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateButtonEnabled()
            }
        })
    }

    dialog.show()
}

package com.jhaiian.clint.quiver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.jhaiian.clint.R
import com.jhaiian.clint.util.formatFileSize
import com.jhaiian.clint.ui.ClintToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Opens the system file picker for the "Add from file" FAB menu item.
internal fun QuiverGuardActivity.launchAddFilterListFromFile() {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    try {
        filePickerLauncher.launch(intent)
    } catch (_: ActivityNotFoundException) {
        ClintToast.show(this, getString(R.string.filter_list_add_file_picker_unavailable), R.drawable.ic_warning_24)
    }
}

// Copies and validates the picked file off the main thread, then either opens
// the title-confirmation dialog or surfaces an error toast. Called from the
// file picker's ActivityResult callback.
internal fun QuiverGuardActivity.handlePickedFilterListFile(uri: Uri) {
    activityScope.launch {
        val result = withContext(Dispatchers.IO) {
            LocalFilterListImporter.import(applicationContext, uri)
        }
        when (result) {
            is LocalFilterListImportResult.Success -> showAddLocalFilterListDialog(result)
            is LocalFilterListImportResult.Error -> {
                ClintToast.show(this@handlePickedFilterListFile, getString(result.messageResId), R.drawable.ic_warning_24)
            }
        }
    }
}

// Single-stage counterpart to showAddCustomFilterListDialog's Stage 2: the file
// has already been copied and validated by the time this shows, so the user
// only confirms or edits the title pre-filled from the picked file's name.
private fun QuiverGuardActivity.showAddLocalFilterListDialog(imported: LocalFilterListImportResult.Success) {
    val activity = this
    val dialogView = layoutInflater.inflate(R.layout.dialog_add_filter_list_file, null)

    val tvSummary = dialogView.findViewById<TextView>(R.id.tv_filter_list_file_summary)
    val etTitle = dialogView.findViewById<TextInputEditText>(R.id.et_filter_list_file_title)

    tvSummary.text = getString(
        R.string.filter_list_add_file_summary,
        imported.ruleCount,
        formatFileSize(imported.sizeBytes)
    )
    etTitle.setText(imported.suggestedTitle)
    etTitle.setSelection(etTitle.text?.length ?: 0)

    var pendingFile: File? = imported.file

    val dialog = MaterialAlertDialogBuilder(activity, getDialogTheme())
        .setTitle(getString(R.string.filter_list_add_file_dialog_title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.filter_list_add_action_add), null)
        .create()
        .also { applyStatusBarFlagToDialog(it) }

    // Delete the imported temp file on any dismissal path that isn't a
    // successful save, mirroring showAddCustomFilterListDialog's cleanup.
    dialog.setOnDismissListener { pendingFile?.delete() }

    dialog.setOnShowListener {
        val positiveBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        val negativeBtn = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
        positiveBtn.isEnabled = etTitle.text?.toString()?.trim().orEmpty().isNotEmpty()

        etTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                positiveBtn.isEnabled = s?.toString()?.trim().orEmpty().isNotEmpty()
            }
        })

        positiveBtn.setOnClickListener {
            val file = pendingFile ?: return@setOnClickListener
            val title = etTitle.text?.toString()?.trim().orEmpty()
            if (title.isEmpty()) return@setOnClickListener
            positiveBtn.isEnabled = false
            negativeBtn.isEnabled = false
            dialog.setCancelable(false)
            activityScope.launch {
                try {
                    val added = withContext(Dispatchers.IO) {
                        val newId = database().addLocalFilterList(title)
                        if (newId <= 0L) throw IllegalStateException()
                        val targetFile = FilterListDownloader.localFileFor(applicationContext, newId)
                        targetFile.parentFile?.mkdirs()
                        file.copyTo(targetFile, overwrite = true)
                        file.delete()
                        val downloadedAt = System.currentTimeMillis()
                        database().updateDownloadResult(newId, targetFile.absolutePath, imported.sizeBytes, downloadedAt, imported.ruleCount)
                        FilterList(
                            id = newId, name = title, downloadUrl = "",
                            isEnabled = false,
                            localPath = targetFile.absolutePath,
                            fileSizeBytes = imported.sizeBytes, downloadedAt = downloadedAt,
                            ruleCount = imported.ruleCount, isCustom = true
                        )
                    }
                    // Null out the local reference so the dismiss listener does not
                    // delete the file that was just moved to its final location.
                    pendingFile = null
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
        }
    }

    dialog.show()
}

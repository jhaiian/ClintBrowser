package com.jhaiian.clint.downloads

import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import com.jhaiian.clint.ui.ClintToast
import okhttp3.Request
import java.io.File

private const val DIALOG_DEFAULT_PATH = "/storage/emulated/0/Download/"

internal fun DownloadsActivity.showManualDownloadDialog() {
    val dialogView = layoutInflater.inflate(R.layout.dialog_download_manual, null)

    val tilUrl = dialogView.findViewById<TextInputLayout>(R.id.til_manual_url)
    val etUrl = dialogView.findViewById<TextInputEditText>(R.id.et_manual_url)
    val etFilename = dialogView.findViewById<TextInputEditText>(R.id.et_manual_filename)
    val etExtension = dialogView.findViewById<TextInputEditText>(R.id.et_manual_extension)
    val dropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.download_manual_location_dropdown)
    val folderRow = dialogView.findViewById<LinearLayout>(R.id.download_manual_folder_row)
    val folderPathText = dialogView.findViewById<TextView>(R.id.download_manual_folder_path)
    val folderIcon = dialogView.findViewById<ImageView>(R.id.download_manual_folder_icon)
    val switchRetry = dialogView.findViewById<Switch>(R.id.switch_manual_retry)
    val rowRetry = dialogView.findViewById<LinearLayout>(R.id.row_manual_retry)
    val switchUnmetered = dialogView.findViewById<Switch>(R.id.switch_manual_unmetered)
    val rowUnmetered = dialogView.findViewById<LinearLayout>(R.id.row_manual_unmetered)
    val sliderSplit = dialogView.findViewById<Slider>(R.id.slider_manual_split_parts)
    val textSplitSummary = dialogView.findViewById<TextView>(R.id.text_manual_split_summary)
    val sliderMulti = dialogView.findViewById<Slider>(R.id.slider_manual_multi_parts)
    val textMultiSummary = dialogView.findViewById<TextView>(R.id.text_manual_multi_summary)
    val tvFileSize = dialogView.findViewById<TextView>(R.id.tv_manual_file_size)
    val tvStorageInfo = dialogView.findViewById<TextView>(R.id.tv_manual_storage_info)

    tvFileSize.tag = -1L

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)

    var currentMode = prefs.getString(
        DownloadSettingsFragment.PREF_DOWNLOAD_LOCATION_MODE,
        DownloadSettingsFragment.MODE_DEFAULT
    ) ?: DownloadSettingsFragment.MODE_DEFAULT
    var currentCustomUri: Uri? = prefs.getString(DownloadSettingsFragment.PREF_DOWNLOAD_CUSTOM_URI, null)
        ?.let { Uri.parse(it) }

    val locationOptions = listOf(
        getString(R.string.download_location_option_default),
        getString(R.string.download_location_option_custom)
    )
    dropdown.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, locationOptions))
    dropdown.setText(if (currentMode == DownloadSettingsFragment.MODE_CUSTOM) locationOptions[1] else locationOptions[0], false)

    fun applyLocationMode(mode: String) {
        if (mode == DownloadSettingsFragment.MODE_CUSTOM) {
            folderPathText.text = currentCustomUri?.let { uriToDisplayPath(it) }
                ?: getString(R.string.download_location_tap_to_choose)
            folderRow.isEnabled = true
            folderRow.isClickable = true
            folderIcon.alpha = 1f
            folderPathText.alpha = 1f
        } else {
            folderPathText.text = DIALOG_DEFAULT_PATH
            folderRow.isEnabled = false
            folderRow.isClickable = false
            folderIcon.alpha = 0.4f
            folderPathText.alpha = 0.4f
        }
        updateStorageInfo(this, tvStorageInfo, mode, currentCustomUri)
    }
    applyLocationMode(currentMode)

    switchRetry.isChecked = prefs.getBoolean(DownloadSettingsFragment.PREF_RETRY_ENABLED, DownloadSettingsFragment.DEFAULT_RETRY_ENABLED)
    switchUnmetered.isChecked = prefs.getBoolean(DownloadSettingsFragment.PREF_UNMETERED_ONLY, DownloadSettingsFragment.DEFAULT_UNMETERED_ONLY)
    rowRetry.setOnClickListener { switchRetry.isChecked = !switchRetry.isChecked }
    rowUnmetered.setOnClickListener { switchUnmetered.isChecked = !switchUnmetered.isChecked }

    val initSplit = prefs.getInt(DownloadSettingsFragment.PREF_SPLIT_PARTS, DownloadSettingsFragment.DEFAULT_SPLIT_PARTS)
        .toFloat().coerceIn(sliderSplit.valueFrom, sliderSplit.valueTo)
    sliderSplit.value = initSplit
    textSplitSummary.text = resources.getQuantityString(R.plurals.download_split_parts_value, initSplit.toInt(), initSplit.toInt())
    sliderSplit.addOnChangeListener { _, value, _ ->
        textSplitSummary.text = resources.getQuantityString(R.plurals.download_split_parts_value, value.toInt(), value.toInt())
    }

    val initMulti = prefs.getInt(DownloadSettingsFragment.PREF_MULTITHREADING_PARTS, DownloadSettingsFragment.DEFAULT_MULTITHREADING_PARTS)
        .toFloat().coerceIn(sliderMulti.valueFrom, sliderMulti.valueTo)
    sliderMulti.value = initMulti
    textMultiSummary.text = resources.getQuantityString(R.plurals.download_multithreading_value, initMulti.toInt(), initMulti.toInt())
    sliderMulti.addOnChangeListener { _, value, _ ->
        textMultiSummary.text = resources.getQuantityString(R.plurals.download_multithreading_value, value.toInt(), value.toInt())
    }

    var fetchedFilename: String? = null
    var isFetched = false

    val dialog = MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.download_dialog_title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.download_manual_fetch), null)
        .create()

    dialog.setOnShowListener {
        val positiveBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        positiveBtn.isEnabled = false

        fun setFetching(active: Boolean) {
            positiveBtn.isEnabled = !active
            positiveBtn.text = if (active) getString(R.string.download_manual_fetching) else getString(R.string.download_manual_fetch)
            etUrl.isEnabled = !active
        }

        fun onFetchSuccess(rawFilename: String, contentLength: Long) {
            runOnUiThread {
                isFetched = true
                fetchedFilename = rawFilename
                val dot = rawFilename.lastIndexOf('.')
                etFilename.setText(if (dot > 0) rawFilename.substring(0, dot) else rawFilename)
                etExtension.setText(if (dot > 0) rawFilename.substring(dot + 1) else "")
                tilUrl.error = null
                positiveBtn.isEnabled = true
                positiveBtn.text = getString(R.string.action_download)
                etUrl.isEnabled = true
                tvFileSize.tag = contentLength
                tvFileSize.visibility = View.VISIBLE
                tvFileSize.text = if (contentLength > 0L) {
                    getString(R.string.download_dialog_file_size_value, formatFileSize(contentLength))
                } else {
                    getString(R.string.download_dialog_file_size_unknown)
                }
            }
        }

        fun onFetchError(message: String) {
            runOnUiThread {
                isFetched = false
                tilUrl.error = message
                setFetching(false)
                positiveBtn.isEnabled = etUrl.text?.isNotBlank() == true
            }
        }

        fun doFetch(url: String) {
            tilUrl.error = null
            setFetching(true)
            val ua = android.webkit.WebSettings.getDefaultUserAgent(this)
            ClintDownloadManager.executor.submit {
                try {
                    val headRequest = Request.Builder()
                        .url(url)
                        .head()
                        .header("User-Agent", ua)
                        .build()
                    val headResponse = ClintDownloadManager.httpClient.newCall(headRequest).execute()
                    headResponse.use { resp ->
                        if (!resp.isSuccessful && resp.code != 405) {
                            onFetchError(getString(R.string.download_manual_error_network))
                            return@submit
                        }
                        val contentDisposition = resp.header("Content-Disposition") ?: ""
                        val contentType = resp.header("Content-Type")?.substringBefore(";")?.trim() ?: ""
                        var contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L

                        if (contentLength <= 0L) {
                            try {
                                val rangeRequest = Request.Builder()
                                    .url(url)
                                    .get()
                                    .header("User-Agent", ua)
                                    .header("Range", "bytes=0-0")
                                    .build()
                                val rangeResponse = ClintDownloadManager.httpClient.newCall(rangeRequest).execute()
                                contentLength = rangeResponse.header("Content-Range")
                                    ?.substringAfterLast('/')
                                    ?.toLongOrNull()
                                    ?: rangeResponse.header("Content-Length")?.toLongOrNull()
                                    ?: -1L
                                rangeResponse.close()
                            } catch (_: Exception) { }
                        }

                        val resolvedFilename = resolveFilename(url, contentDisposition, contentType)
                        onFetchSuccess(resolvedFilename, contentLength)
                    }
                } catch (_: Exception) {
                    onFetchError(getString(R.string.download_manual_error_network))
                }
            }
        }

        positiveBtn.setOnClickListener {
            val url = etUrl.text?.toString()?.trim() ?: ""
            if (isFetched) {
                val contentLength = tvFileSize.tag as? Long ?: -1L
                val storageError = checkStorageAvailable(this, contentLength, currentMode, currentCustomUri)
                if (storageError != null) {
                    MaterialAlertDialogBuilder(this, getDialogTheme())
                        .setTitle(getString(R.string.download_error_storage_title))
                        .setMessage(storageError)
                        .setPositiveButton(getString(R.string.action_ok), null)
                        .show()
                    return@setOnClickListener
                }
                val nameText = etFilename.text?.toString()?.trim() ?: ""
                val extText = etExtension.text?.toString()?.trim() ?: ""
                val resolvedFilename = if (extText.isNotEmpty()) "$nameText.$extText" else nameText
                val splitParts = sliderSplit.value.toInt()
                val multithreadingParts = sliderMulti.value.toInt()
                val ua = android.webkit.WebSettings.getDefaultUserAgent(this)
                performManualDownload(
                    url = url,
                    filename = resolvedFilename,
                    userAgent = ua,
                    retryEnabled = switchRetry.isChecked,
                    unmeteredOnly = switchUnmetered.isChecked,
                    splitParts = splitParts,
                    multithreadingParts = multithreadingParts,
                    locationMode = currentMode,
                    customLocationUri = currentCustomUri?.toString(),
                    onDismiss = {
                        ClintToast.show(this, getString(R.string.toast_downloading, resolvedFilename), R.drawable.ic_download_24)
                        dialog.dismiss()
                    },
                    onRename = {
                        etFilename.requestFocus()
                        etFilename.selectAll()
                        val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        imm.showSoftInput(etFilename, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                )
            } else {
                if (!URLUtil.isValidUrl(url)) {
                    tilUrl.error = getString(R.string.download_manual_error_invalid_url)
                    return@setOnClickListener
                }
                doFetch(url)
            }
        }

        etUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.isNotBlank() == true
                positiveBtn.isEnabled = hasText
                if (isFetched) {
                    isFetched = false
                    fetchedFilename = null
                    positiveBtn.text = getString(R.string.download_manual_fetch)
                    etFilename.text?.clear()
                    etExtension.text?.clear()
                    tvFileSize.visibility = View.GONE
                    tvFileSize.tag = -1L
                }
                tilUrl.error = null
            }
        })

        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO && positiveBtn.isEnabled) {
                positiveBtn.performClick()
                true
            } else false
        }
    }

    val folderPickerLauncher = manualFolderPickerLauncher

    fun launchFolderPicker() {
        manualFolderPickerCallback = { uri ->
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString(DownloadSettingsFragment.PREF_DOWNLOAD_CUSTOM_URI, uri.toString()).apply()
            currentCustomUri = uri
            folderPathText.text = uriToDisplayPath(uri)
            updateStorageInfo(this, tvStorageInfo, currentMode, currentCustomUri)
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        folderPickerLauncher.launch(intent)
    }

    dropdown.setOnItemClickListener { _, _, position, _ ->
        currentMode = if (position == 1) DownloadSettingsFragment.MODE_CUSTOM else DownloadSettingsFragment.MODE_DEFAULT
        applyLocationMode(currentMode)
        if (currentMode == DownloadSettingsFragment.MODE_CUSTOM) launchFolderPicker()
    }

    folderRow.setOnClickListener {
        if (currentMode == DownloadSettingsFragment.MODE_CUSTOM) launchFolderPicker()
    }

    dialog.show()
}

private fun resolveFilename(url: String, contentDisposition: String, contentType: String): String {
    if (contentDisposition.isNotBlank()) {
        val cdFilename = DownloadFileHelper.extractFilenameFromContentDisposition(contentDisposition)
        if (!cdFilename.isNullOrBlank()) return cdFilename
    }
    val guessed = URLUtil.guessFileName(url, contentDisposition, contentType)
    if (!guessed.isNullOrBlank() && guessed != "downloadfile") return guessed
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType)
    val nameFromUrl = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
        .ifBlank { "download" }
    return if (ext != null && !nameFromUrl.contains('.')) "$nameFromUrl.$ext" else nameFromUrl
}

private fun uriToDisplayPath(uri: Uri): String {
    val path = uri.lastPathSegment ?: return uri.toString()
    return when {
        path.startsWith("primary:") -> "/storage/emulated/0/${path.removePrefix("primary:")}/"
        path.contains(":") -> {
            val parts = path.split(":", limit = 2)
            "/storage/${parts[0]}/${parts[1]}/"
        }
        else -> uri.toString()
    }
}

private fun DownloadsActivity.performManualDownload(
    url: String,
    filename: String,
    userAgent: String,
    retryEnabled: Boolean,
    unmeteredOnly: Boolean,
    splitParts: Int,
    multithreadingParts: Int,
    locationMode: String,
    customLocationUri: String?,
    onDismiss: () -> Unit,
    onRename: () -> Unit
) {
    val cm = getSystemService(android.net.ConnectivityManager::class.java)
    val isMetered = cm?.isActiveNetworkMetered ?: false
    if (unmeteredOnly && isMetered) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.download_metered_warning_title))
            .setMessage(getString(R.string.download_metered_warning_message))
            .setPositiveButton(getString(R.string.action_yes)) { _, _ ->
                checkConflictAndEnqueueManual(url, filename, userAgent, retryEnabled, false, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
            }
            .setNegativeButton(getString(R.string.action_no)) { _, _ ->
                checkConflictAndEnqueueManual(url, filename, userAgent, retryEnabled, true, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
            }
            .setNeutralButton(getString(R.string.action_cancel), null)
            .show()
        return
    }
    checkConflictAndEnqueueManual(url, filename, userAgent, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
}

private fun DownloadsActivity.checkConflictAndEnqueueManual(
    url: String,
    filename: String,
    userAgent: String,
    retryEnabled: Boolean,
    unmeteredOnly: Boolean,
    splitParts: Int,
    multithreadingParts: Int,
    locationMode: String,
    customLocationUri: String?,
    onDismiss: () -> Unit,
    onRename: () -> Unit
) {
    val isSaf = locationMode == DownloadSettingsFragment.MODE_CUSTOM
    val fileExists = if (isSaf) {
        val treeUri = customLocationUri?.let { Uri.parse(it) } ?: DownloadFileHelper.getSafTreeUri(this)
        treeUri?.let { DocumentFile.fromTreeUri(this, it)?.findFile(filename) } != null
    } else {
        File(DownloadFileHelper.resolveDownloadDir(), filename).exists()
    }
    if (!fileExists) {
        onDismiss()
        ClintDownloadManager.enqueue(this, url, filename, userAgent, "", "", retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri)
        return
    }
    val items = arrayOf(
        getString(R.string.download_conflict_add_duplicate),
        getString(R.string.download_conflict_override),
        getString(R.string.download_conflict_rename)
    )
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.download_conflict_title))
        .setItems(items) { _, which ->
            when (which) {
                0 -> {
                    onDismiss()
                    ClintDownloadManager.enqueue(this, url, filename, userAgent, "", "", retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri)
                }
                1 -> {
                    deleteExistingManual(filename, locationMode, customLocationUri)
                    onDismiss()
                    ClintDownloadManager.enqueue(this, url, filename, userAgent, "", "", retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri)
                }
                2 -> onRename()
            }
        }
        .setNegativeButton(getString(R.string.action_cancel), null)
        .show()
}

private fun DownloadsActivity.deleteExistingManual(
    filename: String,
    locationMode: String,
    customLocationUri: String?
) {
    val matchingIds = synchronized(ClintDownloadManager.downloads) {
        ClintDownloadManager.downloads.filter { it.filename == filename }.map { it.id }
    }
    matchingIds.forEach { ClintDownloadManager.remove(this, it, deleteFile = true) }
    val isSaf = locationMode == DownloadSettingsFragment.MODE_CUSTOM
    if (isSaf) {
        val treeUri = customLocationUri?.let { Uri.parse(it) } ?: DownloadFileHelper.getSafTreeUri(this)
        treeUri?.let { DocumentFile.fromTreeUri(this, it)?.findFile(filename)?.delete() }
    } else {
        File(DownloadFileHelper.resolveDownloadDir(), filename).delete()
    }
}

package com.jhaiian.clint.browser.delegates

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.downloads.checkStorageAvailable
import com.jhaiian.clint.downloads.formatFileSize
import com.jhaiian.clint.downloads.updateStorageInfo
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import com.jhaiian.clint.ui.ClintToast
import okhttp3.Request

private const val DIALOG_DEFAULT_PATH = "/storage/emulated/0/Download/"

internal fun MainActivity.showDownloadDialog(
    url: String,
    filename: String,
    userAgent: String,
    referer: String,
    cookies: String
) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_download_request, null)
    var currentMode = DownloadSettingsFragment.MODE_DEFAULT
    var currentCustomUri: Uri? = null
    setupDownloadDialogView(dialogView, url, filename, showOptions = true, userAgent = userAgent,
        onModeChanged = { mode, uri -> currentMode = mode; currentCustomUri = uri })

    val dialog = MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.download_dialog_title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.action_download), null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val contentLength = dialogView.findViewById<TextView>(R.id.tv_download_file_size).tag as? Long ?: -1L
            val storageError = checkStorageAvailable(this, contentLength, currentMode, currentCustomUri)
            if (storageError != null) {
                MaterialAlertDialogBuilder(this, getDialogTheme())
                    .setTitle(getString(R.string.download_error_storage_title))
                    .setMessage(storageError)
                    .setPositiveButton(getString(R.string.action_ok), null)
                    .show()
                return@setOnClickListener
            }
            val resolvedFilename = resolveFilenameFromDialog(dialogView)
            ClintToast.show(this, getString(R.string.toast_downloading, resolvedFilename), R.drawable.ic_download_24)
            val retryEnabled = dialogView.findViewById<Switch>(R.id.switch_dialog_retry).isChecked
            val unmeteredOnly = dialogView.findViewById<Switch>(R.id.switch_dialog_unmetered).isChecked
            val splitParts = dialogView.findViewById<Slider>(R.id.slider_dialog_split_parts).value.toInt()
            val multithreadingParts = dialogView.findViewById<Slider>(R.id.slider_dialog_multi_parts).value.toInt()
            initiateDownload(
                url, resolvedFilename, userAgent, referer, cookies,
                retryEnabled, unmeteredOnly, splitParts, multithreadingParts,
                currentMode, currentCustomUri?.toString(),
                onDismiss = { dialog.dismiss() },
                onRename = {
                    val etFilename = dialogView.findViewById<TextInputEditText>(R.id.et_dl_filename)
                    etFilename.requestFocus()
                    etFilename.selectAll()
                    val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    imm.showSoftInput(etFilename, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            )
        }
    }

    dialog.show()
}

internal fun MainActivity.showDownloadDialogForBlob(
    base64: String,
    filename: String,
    mimeType: String
) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_download_request, null)
    setupDownloadDialogView(dialogView, getString(R.string.download_dialog_blob_label), filename, showOptions = false)

    val dialog = MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.download_dialog_title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.action_download), null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val resolvedFilename = resolveFilenameFromDialog(dialogView)
            ClintToast.show(this, getString(R.string.toast_downloading, resolvedFilename), R.drawable.ic_download_24)
            dialog.dismiss()
            ClintDownloadManager.enqueueBlob(this, base64, resolvedFilename, mimeType)
        }
    }

    dialog.show()
}

private fun MainActivity.setupDownloadDialogView(
    dialogView: View,
    url: String,
    filename: String,
    showOptions: Boolean,
    userAgent: String = "",
    onModeChanged: ((String, Uri?) -> Unit)? = null
) {
    val tvUrl = dialogView.findViewById<TextView>(R.id.tv_download_url)
    val ivLinkIcon = dialogView.findViewById<ImageView>(R.id.iv_download_link_icon)
    val etFilename = dialogView.findViewById<TextInputEditText>(R.id.et_dl_filename)
    val etExtension = dialogView.findViewById<TextInputEditText>(R.id.et_dl_extension)
    val dropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.download_dialog_location_dropdown)
    val folderRow = dialogView.findViewById<LinearLayout>(R.id.download_dialog_folder_row)
    val folderPathText = dialogView.findViewById<TextView>(R.id.download_dialog_folder_path)
    val folderIcon = dialogView.findViewById<ImageView>(R.id.download_dialog_folder_icon)
    val optionsSection = dialogView.findViewById<View>(R.id.download_dialog_options_section)
    val tvFileSize = dialogView.findViewById<TextView>(R.id.tv_download_file_size)
    val tvStorageInfo = dialogView.findViewById<TextView>(R.id.tv_download_storage_info)

    tvUrl.text = url

    ivLinkIcon.setOnClickListener {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.download_dialog_link_clip_label), url))
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            ClintToast.show(this, getString(R.string.download_dialog_link_copied), R.drawable.ic_copy_24)
        }
    }

    val dot = filename.lastIndexOf('.')
    etFilename.setText(if (dot > 0) filename.substring(0, dot) else filename)
    etExtension.setText(if (dot > 0) filename.substring(dot + 1) else "")

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    var currentMode = prefs.getString(
        DownloadSettingsFragment.PREF_DOWNLOAD_LOCATION_MODE,
        DownloadSettingsFragment.MODE_DEFAULT
    ) ?: DownloadSettingsFragment.MODE_DEFAULT
    var currentCustomUri: Uri? = prefs.getString(DownloadSettingsFragment.PREF_DOWNLOAD_CUSTOM_URI, null)
        ?.let { Uri.parse(it) }

    onModeChanged?.invoke(currentMode, currentCustomUri)

    val locationOptions = listOf(
        getString(R.string.download_location_option_default),
        getString(R.string.download_location_option_custom)
    )
    dropdown.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, locationOptions))
    dropdown.setText(if (currentMode == DownloadSettingsFragment.MODE_CUSTOM) locationOptions[1] else locationOptions[0], false)

    fun applyMode(mode: String) {
        if (mode == DownloadSettingsFragment.MODE_CUSTOM) {
            folderPathText.text = currentCustomUri?.let { dialogUriToDisplayPath(it) }
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

    applyMode(currentMode)

    fun launchPicker() {
        downloadDialogFolderPickerCallback = { uri ->
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString(DownloadSettingsFragment.PREF_DOWNLOAD_CUSTOM_URI, uri.toString()).apply()
            currentCustomUri = uri
            folderPathText.text = dialogUriToDisplayPath(uri)
            updateStorageInfo(this, tvStorageInfo, currentMode, currentCustomUri)
            onModeChanged?.invoke(currentMode, currentCustomUri)
        }
        launchDownloadDialogFolderPicker()
    }

    dropdown.setOnItemClickListener { _, _, position, _ ->
        currentMode = if (position == 1) DownloadSettingsFragment.MODE_CUSTOM else DownloadSettingsFragment.MODE_DEFAULT
        applyMode(currentMode)
        onModeChanged?.invoke(currentMode, currentCustomUri)
        if (currentMode == DownloadSettingsFragment.MODE_CUSTOM) launchPicker()
    }

    folderRow.setOnClickListener {
        if (currentMode == DownloadSettingsFragment.MODE_CUSTOM) launchPicker()
    }

    if (showOptions && optionsSection != null) {
        optionsSection.visibility = View.VISIBLE

        val switchRetry = dialogView.findViewById<Switch>(R.id.switch_dialog_retry)
        val rowRetry = dialogView.findViewById<LinearLayout>(R.id.row_dialog_retry)
        val switchUnmetered = dialogView.findViewById<Switch>(R.id.switch_dialog_unmetered)
        val rowUnmetered = dialogView.findViewById<LinearLayout>(R.id.row_dialog_unmetered)
        val sliderSplit = dialogView.findViewById<Slider>(R.id.slider_dialog_split_parts)
        val textSplitSummary = dialogView.findViewById<TextView>(R.id.text_dialog_split_summary)
        val sliderMulti = dialogView.findViewById<Slider>(R.id.slider_dialog_multi_parts)
        val textMultiSummary = dialogView.findViewById<TextView>(R.id.text_dialog_multi_summary)

        switchRetry.isChecked = prefs.getBoolean(DownloadSettingsFragment.PREF_RETRY_ENABLED, DownloadSettingsFragment.DEFAULT_RETRY_ENABLED)
        switchUnmetered.isChecked = prefs.getBoolean(DownloadSettingsFragment.PREF_UNMETERED_ONLY, DownloadSettingsFragment.DEFAULT_UNMETERED_ONLY)
        rowRetry.setOnClickListener { switchRetry.isChecked = !switchRetry.isChecked }
        rowUnmetered.setOnClickListener { switchUnmetered.isChecked = !switchUnmetered.isChecked }

        val initSplit = prefs.getInt(DownloadSettingsFragment.PREF_SPLIT_PARTS, DownloadSettingsFragment.DEFAULT_SPLIT_PARTS).toFloat()
            .coerceIn(sliderSplit.valueFrom, sliderSplit.valueTo)
        sliderSplit.value = initSplit
        textSplitSummary.text = resources.getQuantityString(R.plurals.download_split_parts_value, initSplit.toInt(), initSplit.toInt())
        sliderSplit.addOnChangeListener { _, value, _ ->
            textSplitSummary.text = resources.getQuantityString(R.plurals.download_split_parts_value, value.toInt(), value.toInt())
        }

        val initMulti = prefs.getInt(DownloadSettingsFragment.PREF_MULTITHREADING_PARTS, DownloadSettingsFragment.DEFAULT_MULTITHREADING_PARTS).toFloat()
            .coerceIn(sliderMulti.valueFrom, sliderMulti.valueTo)
        sliderMulti.value = initMulti
        textMultiSummary.text = resources.getQuantityString(R.plurals.download_multithreading_value, initMulti.toInt(), initMulti.toInt())
        sliderMulti.addOnChangeListener { _, value, _ ->
            textMultiSummary.text = resources.getQuantityString(R.plurals.download_multithreading_value, value.toInt(), value.toInt())
        }
    } else {
        optionsSection?.visibility = View.GONE
    }

    fetchFileSizeAsync(url, userAgent, tvFileSize)
}

private fun resolveFilenameFromDialog(dialogView: View): String {
    val name = dialogView.findViewById<TextInputEditText>(R.id.et_dl_filename).text?.toString()?.trim() ?: ""
    val ext = dialogView.findViewById<TextInputEditText>(R.id.et_dl_extension).text?.toString()?.trim() ?: ""
    return if (ext.isNotEmpty()) "$name.$ext" else name
}

private fun dialogUriToDisplayPath(uri: Uri): String {
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

private fun fetchFileSizeAsync(url: String, userAgent: String, tvFileSize: TextView) {
    val context = tvFileSize.context
    tvFileSize.tag = -1L
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        tvFileSize.text = context.getString(R.string.download_dialog_file_size_unknown)
        return
    }
    tvFileSize.text = context.getString(R.string.download_dialog_file_size_fetching)
    ClintDownloadManager.executor.submit {
        val ua = userAgent.ifEmpty { "Mozilla/5.0" }
        val contentLength = tryHeadForSize(url, ua).takeIf { it > 0L }
            ?: tryRangeGetForSize(url, ua)
        ClintDownloadManager.mainHandler.post {
            tvFileSize.tag = contentLength
            tvFileSize.text = if (contentLength > 0L) {
                context.getString(R.string.download_dialog_file_size_value, formatFileSize(contentLength))
            } else {
                context.getString(R.string.download_dialog_file_size_unknown)
            }
        }
    }
}

private fun tryHeadForSize(url: String, userAgent: String): Long {
    return try {
        val request = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", userAgent)
            .build()
        val response = ClintDownloadManager.httpClient.newCall(request).execute()
        val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
        response.close()
        len
    } catch (_: Throwable) { -1L }
}

private fun tryRangeGetForSize(url: String, userAgent: String): Long {
    return try {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-0")
            .build()
        val response = ClintDownloadManager.httpClient.newCall(request).execute()
        val total = response.header("Content-Range")
            ?.substringAfterLast('/')
            ?.toLongOrNull()
            ?: response.header("Content-Length")?.toLongOrNull()
            ?: -1L
        response.close()
        total
    } catch (_: Throwable) { -1L }
}

internal fun MainActivity.launchDownloadDialogFolderPicker() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
    }
    downloadDialogFolderPickerLauncher.launch(intent)
}

package com.jhaiian.clint.downloads

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
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import com.jhaiian.clint.ui.ClintToast

private const val DIALOG_DEFAULT_PATH = "/storage/emulated/0/Download/"

internal fun DownloadsActivity.showRedownloadDialog(item: DownloadItem) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_download_request, null)

    val tvUrl = dialogView.findViewById<TextView>(R.id.tv_download_url)
    val ivLinkIcon = dialogView.findViewById<ImageView>(R.id.iv_download_link_icon)
    val etFilename = dialogView.findViewById<TextInputEditText>(R.id.et_dl_filename)
    val etExtension = dialogView.findViewById<TextInputEditText>(R.id.et_dl_extension)
    val dropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.download_dialog_location_dropdown)
    val folderRow = dialogView.findViewById<LinearLayout>(R.id.download_dialog_folder_row)
    val folderPathText = dialogView.findViewById<TextView>(R.id.download_dialog_folder_path)
    val folderIcon = dialogView.findViewById<ImageView>(R.id.download_dialog_folder_icon)
    val optionsSection = dialogView.findViewById<View>(R.id.download_dialog_options_section)
    val switchRetry = dialogView.findViewById<Switch>(R.id.switch_dialog_retry)
    val rowRetry = dialogView.findViewById<LinearLayout>(R.id.row_dialog_retry)
    val switchUnmetered = dialogView.findViewById<Switch>(R.id.switch_dialog_unmetered)
    val rowUnmetered = dialogView.findViewById<LinearLayout>(R.id.row_dialog_unmetered)
    val sliderSplit = dialogView.findViewById<Slider>(R.id.slider_dialog_split_parts)
    val textSplitSummary = dialogView.findViewById<TextView>(R.id.text_dialog_split_summary)
    val sliderMulti = dialogView.findViewById<Slider>(R.id.slider_dialog_multi_parts)
    val textMultiSummary = dialogView.findViewById<TextView>(R.id.text_dialog_multi_summary)
    val etSpeedLimit = dialogView.findViewById<TextInputEditText>(R.id.et_dialog_speed_limit)
    val speedLimitDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.speed_limit_unit_dropdown_dialog)

    tvUrl.text = item.url

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)

    ivLinkIcon.setOnClickListener {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.download_dialog_link_clip_label), item.url))
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            ClintToast.show(this, getString(R.string.download_dialog_link_copied), R.drawable.ic_copy_24)
        }
    }

    val dot = item.filename.lastIndexOf('.')
    etFilename.setText(if (dot > 0) item.filename.substring(0, dot) else item.filename)
    etExtension.setText(if (dot > 0) item.filename.substring(dot + 1) else "")

    var currentMode = item.locationMode.ifBlank {
        prefs.getString(DownloadSettingsFragment.PREF_DOWNLOAD_LOCATION_MODE, DownloadSettingsFragment.MODE_DEFAULT)
            ?: DownloadSettingsFragment.MODE_DEFAULT
    }
    var currentCustomUri: Uri? = (item.customLocationUri ?: prefs.getString(DownloadSettingsFragment.PREF_DOWNLOAD_CUSTOM_URI, null))
        ?.let { Uri.parse(it) }

    val locationOptions = listOf(
        getString(R.string.download_location_option_default),
        getString(R.string.download_location_option_custom)
    )
    dropdown.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, locationOptions))
    dropdown.setText(if (currentMode == DownloadSettingsFragment.MODE_CUSTOM) locationOptions[1] else locationOptions[0], false)

    fun applyMode(mode: String) {
        if (mode == DownloadSettingsFragment.MODE_CUSTOM) {
            folderPathText.text = currentCustomUri?.let { redownloadUriToDisplayPath(it) }
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
    }
    applyMode(currentMode)

    fun launchPicker() {
        manualFolderPickerCallback = { uri ->
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString(DownloadSettingsFragment.PREF_DOWNLOAD_CUSTOM_URI, uri.toString()).apply()
            currentCustomUri = uri
            folderPathText.text = redownloadUriToDisplayPath(uri)
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        manualFolderPickerLauncher.launch(intent)
    }

    dropdown.setOnItemClickListener { _, _, position, _ ->
        currentMode = if (position == 1) DownloadSettingsFragment.MODE_CUSTOM else DownloadSettingsFragment.MODE_DEFAULT
        applyMode(currentMode)
        if (currentMode == DownloadSettingsFragment.MODE_CUSTOM) launchPicker()
    }

    folderRow.setOnClickListener {
        if (currentMode == DownloadSettingsFragment.MODE_CUSTOM) launchPicker()
    }

    optionsSection?.visibility = View.VISIBLE

    switchRetry.isChecked = item.retryEnabled
    switchUnmetered.isChecked = item.unmeteredOnly
    rowRetry.setOnClickListener { switchRetry.isChecked = !switchRetry.isChecked }
    rowUnmetered.setOnClickListener { switchUnmetered.isChecked = !switchUnmetered.isChecked }

    val initSplit = item.splitParts.toFloat().coerceIn(sliderSplit.valueFrom, sliderSplit.valueTo)
    sliderSplit.value = initSplit
    textSplitSummary.text = resources.getQuantityString(R.plurals.download_split_parts_value, initSplit.toInt(), initSplit.toInt())
    sliderSplit.addOnChangeListener { _, value, _ ->
        textSplitSummary.text = resources.getQuantityString(R.plurals.download_split_parts_value, value.toInt(), value.toInt())
    }

    val initMulti = item.multithreadingParts.toFloat().coerceIn(sliderMulti.valueFrom, sliderMulti.valueTo)
    sliderMulti.value = initMulti
    textMultiSummary.text = resources.getQuantityString(R.plurals.download_multithreading_value, initMulti.toInt(), initMulti.toInt())
    sliderMulti.addOnChangeListener { _, value, _ ->
        textMultiSummary.text = resources.getQuantityString(R.plurals.download_multithreading_value, value.toInt(), value.toInt())
    }

    val (initSpeedLimitAmount, initSpeedLimitUnit) = speedLimitBytesToAmountAndUnit(this, item.speedLimitBytesPerSec)
    if (initSpeedLimitAmount > 0) etSpeedLimit.setText(initSpeedLimitAmount.toString())
    val speedLimitUnitOptions = listOf(getString(R.string.speed_limit_unit_kb), getString(R.string.speed_limit_unit_mb))
    speedLimitDropdown.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, speedLimitUnitOptions))
    speedLimitDropdown.setText(if (initSpeedLimitUnit == SPEED_LIMIT_UNIT_MB) speedLimitUnitOptions[1] else speedLimitUnitOptions[0], false)

    val dialog = MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.download_dialog_title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.action_download), null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val fat32Error = checkFat32FileSizeLimit(this, item.totalBytes, currentMode, currentCustomUri)
            if (fat32Error != null) {
                MaterialAlertDialogBuilder(this, getDialogTheme())
                    .setTitle(getString(R.string.download_error_fat32_title))
                    .setMessage(fat32Error)
                    .setPositiveButton(getString(R.string.action_ok), null)
                    .show()
                return@setOnClickListener
            }
            val nameText = etFilename.text?.toString()?.trim() ?: ""
            val extText = etExtension.text?.toString()?.trim() ?: ""
            val resolvedFilename = if (extText.isNotEmpty()) "$nameText.$extText" else nameText
            val splitParts = sliderSplit.value.toInt()
            val multithreadingParts = sliderMulti.value.toInt()
            val speedLimitAmount = etSpeedLimit.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val speedLimitUnit = if (speedLimitDropdown.text.toString() == speedLimitUnitOptions[1]) SPEED_LIMIT_UNIT_MB else SPEED_LIMIT_UNIT_KB
            val speedLimitBytesPerSec = resolveSpeedLimitBytesPerSec(this, speedLimitAmount, speedLimitUnit)
            performRedownload(
                item = item,
                filename = resolvedFilename,
                retryEnabled = switchRetry.isChecked,
                unmeteredOnly = switchUnmetered.isChecked,
                splitParts = splitParts,
                multithreadingParts = multithreadingParts,
                speedLimitBytesPerSec = speedLimitBytesPerSec,
                locationMode = currentMode,
                customLocationUri = currentCustomUri?.toString(),
                onDismiss = {
                    ClintToast.show(this, getString(R.string.toast_downloading, resolvedFilename), R.drawable.ic_download_24)
                    dialog.dismiss()
                    ClintDownloadManager.remove(this, item.id, true)
                    lastRefreshMs = 0L
                }
            )
        }
    }

    dialog.show()
}

private fun redownloadUriToDisplayPath(uri: Uri): String {
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

private fun DownloadsActivity.performRedownload(
    item: DownloadItem,
    filename: String,
    retryEnabled: Boolean,
    unmeteredOnly: Boolean,
    splitParts: Int,
    multithreadingParts: Int,
    speedLimitBytesPerSec: Long,
    locationMode: String,
    customLocationUri: String?,
    onDismiss: () -> Unit
) {
    val cm = getSystemService(android.net.ConnectivityManager::class.java)
    val isMetered = cm?.isActiveNetworkMetered ?: false
    if (unmeteredOnly && isMetered) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.download_metered_warning_title))
            .setMessage(getString(R.string.download_metered_warning_message))
            .setPositiveButton(getString(R.string.action_yes)) { _, _ ->
                onDismiss()
                ClintDownloadManager.enqueue(this, item.url, filename, item.userAgent, item.referer, item.cookies, retryEnabled, false, splitParts, multithreadingParts, speedLimitBytesPerSec, locationMode, customLocationUri)
            }
            .setNegativeButton(getString(R.string.action_no)) { _, _ ->
                onDismiss()
                ClintDownloadManager.enqueue(this, item.url, filename, item.userAgent, item.referer, item.cookies, retryEnabled, true, splitParts, multithreadingParts, speedLimitBytesPerSec, locationMode, customLocationUri)
            }
            .setNeutralButton(getString(R.string.action_cancel), null)
            .show()
        return
    }
    onDismiss()
    ClintDownloadManager.enqueue(this, item.url, filename, item.userAgent, item.referer, item.cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, speedLimitBytesPerSec, locationMode, customLocationUri)
}

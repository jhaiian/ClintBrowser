package com.jhaiian.clint.browser.delegates

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.downloads.DownloadFileHelper
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import java.io.File

private const val PREF_BATTERY_OPT_ASKED = "battery_opt_asked"

internal fun MainActivity.initiateDownload(
    url: String,
    filename: String,
    userAgent: String,
    referer: String,
    cookies: String,
    retryEnabled: Boolean,
    unmeteredOnly: Boolean,
    splitParts: Int,
    multithreadingParts: Int,
    locationMode: String,
    customLocationUri: String?,
    onDismiss: () -> Unit = {},
    onRename: () -> Unit = {}
) {
    if (unmeteredOnly && isNetworkMetered()) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.download_metered_warning_title))
            .setMessage(getString(R.string.download_metered_warning_message))
            .setPositiveButton(getString(R.string.action_yes)) { _, _ ->
                proceedWithDownload(url, filename, userAgent, referer, cookies, retryEnabled, false, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
            }
            .setNegativeButton(getString(R.string.action_no)) { _, _ ->
                proceedWithDownload(url, filename, userAgent, referer, cookies, retryEnabled, true, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
            }
            .setNeutralButton(getString(R.string.action_cancel), null)
            .show()
        return
    }
    proceedWithDownload(url, filename, userAgent, referer, cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
}

private fun MainActivity.proceedWithDownload(
    url: String,
    filename: String,
    userAgent: String,
    referer: String,
    cookies: String,
    retryEnabled: Boolean,
    unmeteredOnly: Boolean,
    splitParts: Int,
    multithreadingParts: Int,
    locationMode: String,
    customLocationUri: String?,
    onDismiss: () -> Unit,
    onRename: () -> Unit
) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val pm = getSystemService(PowerManager::class.java)
    if (!prefs.getBoolean(PREF_BATTERY_OPT_ASKED, false) &&
        !pm.isIgnoringBatteryOptimizations(packageName)
    ) {
        prefs.edit().putBoolean(PREF_BATTERY_OPT_ASKED, true).apply()
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.battery_opt_rationale_title))
            .setMessage(getString(R.string.battery_opt_rationale_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                doEnqueueDownload(url, filename, userAgent, referer, cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
            }
            .setNegativeButton(getString(R.string.action_not_now)) { _, _ ->
                doEnqueueDownload(url, filename, userAgent, referer, cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
            }
            .show()
        return
    }
    doEnqueueDownload(url, filename, userAgent, referer, cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
}

private fun MainActivity.doEnqueueDownload(
    url: String,
    filename: String,
    userAgent: String,
    referer: String,
    cookies: String,
    retryEnabled: Boolean,
    unmeteredOnly: Boolean,
    splitParts: Int,
    multithreadingParts: Int,
    locationMode: String,
    customLocationUri: String?,
    onDismiss: () -> Unit,
    onRename: () -> Unit
) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        checkConflictAndEnqueue(url, filename, userAgent, referer, cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
        return
    }

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        == PackageManager.PERMISSION_GRANTED
    ) {
        checkConflictAndEnqueue(url, filename, userAgent, referer, cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri, onDismiss, onRename)
        return
    }

    pendingDownload = MainActivity.PendingDownload(url, filename, userAgent, referer, cookies)

    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.download_storage_permission_title))
        .setMessage(getString(R.string.download_storage_permission_message))
        .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
            onDismiss()
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
            pendingDownload = null
            onDismiss()
        }
        .show()
}

private fun MainActivity.checkConflictAndEnqueue(
    url: String,
    filename: String,
    userAgent: String,
    referer: String,
    cookies: String,
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
        val treeUri = customLocationUri?.let { Uri.parse(it) }
            ?: DownloadFileHelper.getSafTreeUri(this)
        treeUri?.let { DocumentFile.fromTreeUri(this, it)?.findFile(filename) } != null
    } else {
        File(DownloadFileHelper.resolveDownloadDir(), filename).exists()
    }

    if (!fileExists) {
        onDismiss()
        ClintDownloadManager.enqueue(this, url, filename, userAgent, referer, cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri)
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
                    ClintDownloadManager.enqueue(this, url, filename, userAgent, referer, cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri)
                }
                1 -> {
                    deleteExistingDownload(filename, locationMode, customLocationUri)
                    onDismiss()
                    ClintDownloadManager.enqueue(this, url, filename, userAgent, referer, cookies, retryEnabled, unmeteredOnly, splitParts, multithreadingParts, locationMode, customLocationUri)
                }
                2 -> {
                    onRename()
                }
            }
        }
        .setNegativeButton(getString(R.string.action_cancel), null)
        .show()
}

private fun MainActivity.deleteExistingDownload(
    filename: String,
    locationMode: String,
    customLocationUri: String?
) {
    val matchingIds = ClintDownloadManager.downloadsFlow.value.filter { it.filename == filename }.map { it.id }
    matchingIds.forEach { ClintDownloadManager.remove(this, it, deleteFile = true) }

    val isSaf = locationMode == DownloadSettingsFragment.MODE_CUSTOM
    if (isSaf) {
        val treeUri = customLocationUri?.let { Uri.parse(it) }
            ?: DownloadFileHelper.getSafTreeUri(this)
        treeUri?.let { DocumentFile.fromTreeUri(this, it)?.findFile(filename)?.delete() }
    } else {
        File(DownloadFileHelper.resolveDownloadDir(), filename).delete()
    }
}

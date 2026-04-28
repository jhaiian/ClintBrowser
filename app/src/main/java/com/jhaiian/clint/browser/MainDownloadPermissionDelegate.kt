package com.jhaiian.clint.browser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.downloads.ClintDownloadManager

internal fun MainActivity.handleDownloadRequest(
    url: String,
    filename: String,
    userAgent: String,
    referer: String,
    cookies: String
) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        ClintDownloadManager.enqueue(this, url, filename, userAgent, referer, cookies)
        return
    }

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        == PackageManager.PERMISSION_GRANTED
    ) {
        ClintDownloadManager.enqueue(this, url, filename, userAgent, referer, cookies)
        return
    }

    pendingDownload = MainActivity.PendingDownload(url, filename, userAgent, referer, cookies)

    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.download_storage_permission_title))
        .setMessage(getString(R.string.download_storage_permission_message))
        .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
            pendingDownload = null
        }
        .show()
}

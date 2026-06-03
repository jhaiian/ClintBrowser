package com.jhaiian.clint.downloads

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.TextView
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import java.util.Locale

internal fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

internal fun formatStorageBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}

internal fun resolveVolumePathFromUri(uri: Uri): String? {
    val segment = uri.lastPathSegment ?: return null
    return when {
        segment.startsWith("primary:") -> Environment.getExternalStorageDirectory().absolutePath
        segment.contains(":") -> "/storage/${segment.substringBefore(":")}"
        else -> null
    }
}

internal fun updateStorageInfo(context: Context, tvStorage: TextView, mode: String, customUri: Uri?) {
    try {
        val path = if (mode == DownloadSettingsFragment.MODE_CUSTOM) {
            customUri?.let { resolveVolumePathFromUri(it) }
        } else {
            Environment.getExternalStorageDirectory().absolutePath
        }
        if (path == null) {
            tvStorage.text = context.getString(R.string.download_dialog_storage_unavailable)
            return
        }
        val stat = StatFs(path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val used = total - free
        val freePercent = if (total > 0L) free * 100.0 / total else 0.0
        tvStorage.text = context.getString(
            R.string.download_dialog_storage_value,
            formatStorageBytes(used),
            formatStorageBytes(total),
            formatStorageBytes(free),
            String.format(Locale.US, "%.1f", freePercent)
        )
    } catch (_: Throwable) {
        tvStorage.text = context.getString(R.string.download_dialog_storage_unavailable)
    }
}

internal fun checkStorageAvailable(
    context: Context,
    contentLength: Long,
    mode: String,
    customUri: Uri?
): String? {
    if (contentLength <= 0L) return null
    val emulatedFree = try {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (_: Throwable) { return null }

    return when {
        mode != DownloadSettingsFragment.MODE_CUSTOM -> {
            if (emulatedFree < contentLength) {
                context.getString(
                    R.string.download_error_storage_direct,
                    formatStorageBytes(contentLength),
                    formatStorageBytes(emulatedFree)
                )
            } else null
        }
        customUri?.lastPathSegment?.startsWith("primary:") == true -> {
            val required = contentLength * 2
            if (emulatedFree < required) {
                context.getString(
                    R.string.download_error_storage_emulated,
                    formatStorageBytes(required),
                    formatStorageBytes(emulatedFree)
                )
            } else null
        }
        else -> {
            if (emulatedFree < contentLength) {
                return context.getString(
                    R.string.download_error_storage_temp,
                    formatStorageBytes(contentLength),
                    formatStorageBytes(emulatedFree)
                )
            }
            val destPath = customUri?.let { resolveVolumePathFromUri(it) } ?: return null
            val destFree = try {
                val stat = StatFs(destPath)
                stat.availableBlocksLong * stat.blockSizeLong
            } catch (_: Throwable) { return null }
            if (destFree < contentLength) {
                context.getString(
                    R.string.download_error_storage_dest,
                    formatStorageBytes(contentLength),
                    formatStorageBytes(destFree)
                )
            } else null
        }
    }
}

package com.jhaiian.clint.downloads

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import com.jhaiian.clint.util.DEFAULT_MEASUREMENT_SYSTEM
import com.jhaiian.clint.util.MEASUREMENT_SYSTEM_DECIMAL
import com.jhaiian.clint.util.PREF_MEASUREMENT_SYSTEM
import com.jhaiian.clint.util.formatStorageBytes
import java.io.File
import java.util.Locale

internal const val SPEED_LIMIT_UNIT_KB = "KB"
internal const val SPEED_LIMIT_UNIT_MB = "MB"
internal const val DEFAULT_SPEED_LIMIT_UNIT = SPEED_LIMIT_UNIT_KB

/**
 * Converts a user-entered speed limit ([amount] of [unit], "KB" or "MB") into bytes/sec, using the
 * same binary-vs-decimal base as [com.jhaiian.clint.util.formatFileSize] so "1 MB/s" means the same
 * thing here as it does everywhere else size is displayed. [amount] <= 0 always means unlimited.
 */
internal fun resolveSpeedLimitBytesPerSec(context: Context, amount: Int, unit: String): Long {
    if (amount <= 0) return 0L
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val isDecimal = prefs.getString(PREF_MEASUREMENT_SYSTEM, DEFAULT_MEASUREMENT_SYSTEM) == MEASUREMENT_SYSTEM_DECIMAL
    val kb = if (isDecimal) 1000L else 1024L
    val multiplier = if (unit == SPEED_LIMIT_UNIT_MB) kb * kb else kb
    return amount.toLong() * multiplier
}

/**
 * Best-effort inverse of [resolveSpeedLimitBytesPerSec], used to pre-fill an edit field from a
 * previously resolved rate (e.g. redownloading an item that already carries a speed limit).
 * Prefers MB when the value divides evenly, since that's how most limits set through the dialog
 * are entered; otherwise falls back to KB.
 */
internal fun speedLimitBytesToAmountAndUnit(context: Context, bytesPerSec: Long): Pair<Int, String> {
    if (bytesPerSec <= 0L) return 0 to DEFAULT_SPEED_LIMIT_UNIT
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val isDecimal = prefs.getString(PREF_MEASUREMENT_SYSTEM, DEFAULT_MEASUREMENT_SYSTEM) == MEASUREMENT_SYSTEM_DECIMAL
    val kb = if (isDecimal) 1000L else 1024L
    val mb = kb * kb
    return if (bytesPerSec % mb == 0L) {
        (bytesPerSec / mb).toInt() to SPEED_LIMIT_UNIT_MB
    } else {
        maxOf(1L, bytesPerSec / kb).toInt() to SPEED_LIMIT_UNIT_KB
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

    if (mode != DownloadSettingsFragment.MODE_CUSTOM) {
        return if (emulatedFree < contentLength) {
            context.getString(
                R.string.download_error_storage_direct,
                formatStorageBytes(contentLength),
                formatStorageBytes(emulatedFree)
            )
        } else null
    }

    // Doubling (or a separate temp-file check) is only needed for the SAF temp-then-copy
    // workflow. Whenever this device/permission combination lets the app write straight to the
    // destination, the transfer never stages a duplicate copy, so only the single content length
    // is required - matching the write path DownloadWorker actually takes for a fresh download.
    val canWriteDirectly = DownloadFileHelper.canWriteSharedStorageDirectly(context)

    return if (customUri?.lastPathSegment?.startsWith("primary:") == true) {
        // Destination is on the same physical volume as the temp file (or as the direct write).
        if (canWriteDirectly) {
            if (emulatedFree < contentLength) {
                context.getString(
                    R.string.download_error_storage_direct,
                    formatStorageBytes(contentLength),
                    formatStorageBytes(emulatedFree)
                )
            } else null
        } else {
            val required = contentLength * 2
            if (emulatedFree < required) {
                context.getString(
                    R.string.download_error_storage_emulated,
                    formatStorageBytes(required),
                    formatStorageBytes(emulatedFree)
                )
            } else null
        }
    } else {
        val destPath = customUri?.let { resolveVolumePathFromUri(it) } ?: return null
        val destFree = try {
            val stat = StatFs(destPath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Throwable) { return null }
        if (!canWriteDirectly && emulatedFree < contentLength) {
            return context.getString(
                R.string.download_error_storage_temp,
                formatStorageBytes(contentLength),
                formatStorageBytes(emulatedFree)
            )
        }
        if (destFree < contentLength) {
            context.getString(
                R.string.download_error_storage_dest,
                formatStorageBytes(contentLength),
                formatStorageBytes(destFree)
            )
        } else null
    }
}

/** FAT32 cannot address a single file at or above 4 GiB; a download that crosses this size on such a card fails partway through instead of at the point where the user could still choose a different destination. */
private const val FAT32_MAX_FILE_SIZE = 4L * 1024 * 1024 * 1024

private val FAT32_FS_TYPES = setOf("vfat", "fat", "fat32", "msdos")

/**
 * Warns before a download starts if it would exceed FAT32's single-file limit on the chosen
 * destination. Only fires for a custom location on a physically removable, FAT32-formatted
 * volume; the default location and the primary internal volume are never affected regardless of
 * their reported filesystem, since they're the emulated internal partition rather than a real
 * FAT32 card.
 */
internal fun checkFat32FileSizeLimit(
    context: Context,
    contentLength: Long,
    mode: String,
    customUri: Uri?
): String? {
    if (contentLength < FAT32_MAX_FILE_SIZE) return null
    if (mode != DownloadSettingsFragment.MODE_CUSTOM) return null
    val segment = customUri?.lastPathSegment ?: return null
    if (segment.startsWith("primary:")) return null
    val destPath = resolveVolumePathFromUri(customUri) ?: return null
    if (!isRemovableVolume(context, destPath)) return null
    val fsType = mountedFsType(destPath)?.lowercase(Locale.US) ?: return null
    if (fsType !in FAT32_FS_TYPES) return null
    return context.getString(R.string.download_error_fat32_message, formatStorageBytes(contentLength))
}

private fun isRemovableVolume(context: Context, path: String): Boolean {
    return try {
        val storageManager = context.getSystemService(StorageManager::class.java) ?: return false
        storageManager.getStorageVolume(File(path))?.isRemovable == true
    } catch (_: Throwable) { false }
}

/**
 * Android has no public API for a volume's filesystem type, so this reads the kernel's mount
 * table directly and matches the longest mount point that prefixes [path]. Returns null if the
 * file can't be read or nothing matches, which the caller treats as "unknown" rather than
 * blocking the download.
 */
private fun mountedFsType(path: String): String? {
    val canonicalPath = try { File(path).canonicalPath } catch (_: Throwable) { path }
    return try {
        File("/proc/mounts").bufferedReader().useLines { lines ->
            var bestMountPoint = ""
            var bestFsType: String? = null
            for (line in lines) {
                val fields = line.split(" ")
                if (fields.size < 3) continue
                val mountPoint = fields[1]
                val matches = canonicalPath == mountPoint || canonicalPath.startsWith("$mountPoint/")
                if (matches && mountPoint.length > bestMountPoint.length) {
                    bestMountPoint = mountPoint
                    bestFsType = fields[2]
                }
            }
            bestFsType
        }
    } catch (_: Throwable) { null }
}

/**
 * Approximates decoded size straight from the encoded string length so the FAT32 check can run
 * before a blob payload is base64-decoded in full.
 */
internal fun estimateBase64DecodedSize(base64: String): Long {
    val trimmed = base64.trimEnd()
    val padding = when {
        trimmed.endsWith("==") -> 2
        trimmed.endsWith("=") -> 1
        else -> 0
    }
    return (trimmed.length.toLong() * 3 / 4) - padding
}

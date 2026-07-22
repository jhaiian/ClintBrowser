package com.jhaiian.clint.util

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Locale

const val PREF_MEASUREMENT_SYSTEM = "measurement_system"
const val MEASUREMENT_SYSTEM_BINARY = "binary"
const val MEASUREMENT_SYSTEM_DECIMAL = "decimal"
const val DEFAULT_MEASUREMENT_SYSTEM = MEASUREMENT_SYSTEM_BINARY

/** Cached in memory because size formatting is called frequently (progress updates) and shouldn't hit SharedPreferences each time. */
@Volatile
private var decimalUnitsEnabled = false

/** Bumped whenever the measurement system changes. Lists that diff on raw byte counts (e.g. RecyclerView DiffUtil) won't otherwise notice that already-bound size text needs re-rendering, since the bytes themselves didn't change. */
@Volatile
private var formatEpoch = 0

/** Lets a UI list detect a measurement system change and force a full rebind instead of relying on a byte-count diff. */
val measurementSystemEpoch: Int get() = formatEpoch

/** Reads the measurement system preference into the in-memory cache; call once at process start. */
fun loadMeasurementSystemPreference(context: Context) {
    decimalUnitsEnabled = PreferenceManager.getDefaultSharedPreferences(context)
        .getString(PREF_MEASUREMENT_SYSTEM, DEFAULT_MEASUREMENT_SYSTEM) == MEASUREMENT_SYSTEM_DECIMAL
}

/** Updates the in-memory cache immediately when the user changes the setting, so formatting reflects it without waiting for a restart. */
fun setMeasurementSystemDecimal(decimal: Boolean) {
    if (decimalUnitsEnabled != decimal) {
        decimalUnitsEnabled = decimal
        formatEpoch++
    }
}

/** Formats a byte count for compact, one-decimal display (progress text, notifications, list rows). */
fun formatFileSize(bytes: Long): String {
    val kb = if (decimalUnitsEnabled) 1000.0 else 1024.0
    val mb = kb * kb
    val gb = mb * kb
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

/** Formats a byte count with two-decimal precision for detail views (storage breakdowns, file properties). */
fun formatStorageBytes(bytes: Long): String {
    val kb = if (decimalUnitsEnabled) 1000.0 else 1024.0
    val mb = kb * kb
    val gb = mb * kb
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}

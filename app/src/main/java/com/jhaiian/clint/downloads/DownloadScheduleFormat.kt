package com.jhaiian.clint.downloads

import android.content.Context
import java.util.Date

/**
 * Formats [millis] as a locale- and 12/24-hour-aware "date, time" string (e.g. "Jan 5, 2027, 3:00 PM"),
 * using the platform's own date/time format providers so it stays consistent with the rest of the
 * system UI. Shared by the schedule dialogs, the downloads list row, and the waiting notification so
 * a single scheduled download reads the same way everywhere it's shown.
 */
internal fun formatScheduledDateTime(context: Context, millis: Long): String {
    val date = Date(millis)
    val datePart = android.text.format.DateFormat.getMediumDateFormat(context).format(date)
    val timePart = android.text.format.DateFormat.getTimeFormat(context).format(date)
    return "$datePart, $timePart"
}

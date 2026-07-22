package com.jhaiian.clint.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import java.util.Calendar
import java.util.TimeZone

/**
 * Wires a "Schedule This Download" switch plus its Date/Time rows inside a download dialog.
 * The picked instant is kept on [switchSchedule]'s tag as an epoch-millis [Long], mirroring how
 * this dialog already stashes the fetched file size on a view tag, so callers can read it back
 * with a plain `findViewById` at submit time without any extra state plumbing.
 *
 * [MaterialDatePicker] works in UTC regardless of device timezone, so the picked day is read back
 * through a UTC calendar and applied to the local working [Calendar] rather than used directly.
 */
internal fun wireScheduleThisDownloadRow(
    context: Context,
    fragmentManager: FragmentManager,
    rowSchedule: View,
    switchSchedule: Switch,
    containerDetails: View,
    rowDate: View,
    textDateValue: TextView,
    rowTime: View,
    textTimeValue: TextView
) {
    val calendar = Calendar.getInstance()

    fun refreshLabels() {
        textDateValue.text = android.text.format.DateFormat.getMediumDateFormat(context).format(calendar.time)
        textTimeValue.text = android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
        switchSchedule.tag = calendar.timeInMillis
    }

    rowSchedule.setOnClickListener { switchSchedule.isChecked = !switchSchedule.isChecked }

    switchSchedule.setOnCheckedChangeListener { _, checked ->
        containerDetails.visibility = if (checked) View.VISIBLE else View.GONE
        if (checked) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.MINUTE, 1)
            refreshLabels()
            maybeRequestExactAlarmPermission(context)
        } else {
            switchSchedule.tag = null
        }
    }

    rowDate.setOnClickListener {
        val utcSelection = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.download_schedule_date_picker_title)
            .setSelection(utcSelection)
            .build()
        picker.addOnPositiveButtonClickListener { selectedUtcMillis ->
            val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selectedUtcMillis }
            calendar.set(Calendar.YEAR, picked.get(Calendar.YEAR))
            calendar.set(Calendar.MONTH, picked.get(Calendar.MONTH))
            calendar.set(Calendar.DAY_OF_MONTH, picked.get(Calendar.DAY_OF_MONTH))
            refreshLabels()
        }
        picker.show(fragmentManager, "download_schedule_date_picker")
    }

    rowTime.setOnClickListener {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .setTitleText(R.string.download_schedule_time_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener {
            calendar.set(Calendar.HOUR_OF_DAY, picker.hour)
            calendar.set(Calendar.MINUTE, picker.minute)
            calendar.set(Calendar.SECOND, 0)
            refreshLabels()
        }
        picker.show(fragmentManager, "download_schedule_time_picker")
    }
}

/** Reads back the value [wireScheduleThisDownloadRow] stashed, or 0L if scheduling isn't enabled. */
internal fun readScheduledStartAtMillis(switchSchedule: Switch): Long =
    if (switchSchedule.isChecked) (switchSchedule.tag as? Long ?: 0L) else 0L

/**
 * On API 31+, an app needs the user's explicit "Alarms & reminders" grant before its alarms can fire
 * at an exact time rather than an OS-deferred approximation. Without it, "Schedule This Download" would
 * silently start late by anywhere from a few minutes to much longer, so this asks up front, the same
 * way the battery-optimization rationale in [com.jhaiian.clint.browser.delegates] does for downloads.
 */
private fun maybeRequestExactAlarmPermission(context: Context) {
    if (DownloadCustomScheduleMonitor.canScheduleExact(context)) return
    val activity = context as? ClintActivity ?: return
    MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
        .setTitle(activity.getString(R.string.download_schedule_exact_alarm_title))
        .setMessage(activity.getString(R.string.download_schedule_exact_alarm_message, activity.getString(R.string.app_name)))
        .setPositiveButton(activity.getString(R.string.action_allow)) { _, _ ->
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
        .setNegativeButton(activity.getString(R.string.action_not_now), null)
        .show()
}

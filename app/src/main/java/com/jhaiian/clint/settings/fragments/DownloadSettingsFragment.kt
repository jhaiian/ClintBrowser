package com.jhaiian.clint.settings.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.downloads.updateStorageInfo

class DownloadSettingsFragment : Fragment() {

    companion object {
        const val PREF_DOWNLOAD_LOCATION_MODE  = "download_location_mode"
        const val PREF_DOWNLOAD_CUSTOM_URI     = "download_custom_uri"
        const val MODE_DEFAULT                 = "default"
        const val MODE_CUSTOM                  = "custom"
        const val PREF_UNMETERED_ONLY          = "download_unmetered_only"
        const val DEFAULT_UNMETERED_ONLY       = false
        const val PREF_RETRY_ENABLED           = "download_retry_enabled"
        const val PREF_RETRY_UNRECOVERABLE     = "download_retry_unrecoverable"
        const val PREF_RETRY_COUNT             = "download_retry_count"
        const val PREF_RETRY_INTERVAL          = "download_retry_interval"
        const val DEFAULT_RETRY_ENABLED        = true
        const val DEFAULT_RETRY_UNRECOVERABLE  = false
        const val DEFAULT_RETRY_COUNT          = 0
        const val DEFAULT_RETRY_INTERVAL       = 5
        const val PREF_CONCURRENT_DOWNLOADS    = "download_concurrent_limit"
        const val DEFAULT_CONCURRENT_DOWNLOADS = 1
        const val PREF_SPLIT_PARTS             = "download_split_parts"
        const val DEFAULT_SPLIT_PARTS          = 32
        const val PREF_MULTITHREADING_PARTS    = "download_multithreading_parts"
        const val DEFAULT_MULTITHREADING_PARTS = 4
        const val PREF_PUSH_NOTIFICATIONS      = "download_push_notifications"
        const val DEFAULT_PUSH_NOTIFICATIONS   = true
        private const val DEFAULT_PATH         = "/storage/emulated/0/Download/"
    }

    private lateinit var dropdownLayout: TextInputLayout
    private lateinit var dropdown: AutoCompleteTextView
    private lateinit var folderRow: LinearLayout
    private lateinit var folderIcon: ImageView
    private lateinit var folderPathText: TextView
    private lateinit var tvStorageInfo: TextView

    private lateinit var rowUnmeteredOnly: LinearLayout
    private lateinit var switchUnmeteredOnly: Switch

    private lateinit var rowRetryEnabled: LinearLayout
    private lateinit var switchRetryEnabled: Switch
    private lateinit var rowRetryUnrecoverable: LinearLayout
    private lateinit var switchRetryUnrecoverable: Switch
    private lateinit var rowRetryCount: LinearLayout
    private lateinit var textRetryCountSummary: TextView
    private lateinit var rowRetryInterval: LinearLayout
    private lateinit var textRetryIntervalSummary: TextView

    private lateinit var rowIgnoreBatteryOpt: LinearLayout
    private lateinit var iconIgnoreBatteryOpt: ImageView
    private lateinit var textIgnoreBatteryOptSummary: TextView

    private lateinit var rowPushNotifications: LinearLayout
    private lateinit var switchPushNotifications: Switch

    private lateinit var sliderConcurrentDownloads: com.google.android.material.slider.Slider
    private lateinit var textConcurrentSummary: TextView
    private lateinit var sliderSplitParts: com.google.android.material.slider.Slider
    private lateinit var textSplitPartsSummary: TextView
    private lateinit var sliderMultithreadingParts: com.google.android.material.slider.Slider
    private lateinit var textMultithreadingSummary: TextView

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            saveCustomUri(uri)
            folderPathText.text = uriToDisplayPath(uri)
            updateStorageInfo(requireContext(), tvStorageInfo, MODE_CUSTOM, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_download_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dropdownLayout        = view.findViewById(R.id.download_location_dropdown_layout)
        dropdown              = view.findViewById(R.id.download_location_dropdown)
        folderRow             = view.findViewById(R.id.folder_selector_row)
        folderIcon            = view.findViewById(R.id.folder_icon)
        folderPathText        = view.findViewById(R.id.folder_path_text)
        tvStorageInfo         = view.findViewById(R.id.tv_download_settings_storage_info)
        rowUnmeteredOnly      = view.findViewById(R.id.row_unmetered_only)
        switchUnmeteredOnly   = view.findViewById(R.id.switch_unmetered_only)
        rowRetryEnabled       = view.findViewById(R.id.row_retry_enabled)
        switchRetryEnabled    = view.findViewById(R.id.switch_retry_enabled)
        rowRetryUnrecoverable = view.findViewById(R.id.row_retry_unrecoverable)
        switchRetryUnrecoverable = view.findViewById(R.id.switch_retry_unrecoverable)
        rowRetryCount         = view.findViewById(R.id.row_retry_count)
        textRetryCountSummary = view.findViewById(R.id.text_retry_count_summary)
        rowRetryInterval      = view.findViewById(R.id.row_retry_interval)
        textRetryIntervalSummary = view.findViewById(R.id.text_retry_interval_summary)
        rowIgnoreBatteryOpt      = view.findViewById(R.id.row_ignore_battery_opt)
        iconIgnoreBatteryOpt     = view.findViewById(R.id.icon_ignore_battery_opt)
        textIgnoreBatteryOptSummary = view.findViewById(R.id.text_ignore_battery_opt_summary)
        rowPushNotifications     = view.findViewById(R.id.row_push_notifications)
        switchPushNotifications  = view.findViewById(R.id.switch_push_notifications)
        sliderConcurrentDownloads = view.findViewById(R.id.slider_concurrent_downloads)
        textConcurrentSummary     = view.findViewById(R.id.text_concurrent_summary)
        sliderSplitParts          = view.findViewById(R.id.slider_split_parts)
        textSplitPartsSummary     = view.findViewById(R.id.text_split_parts_summary)
        sliderMultithreadingParts = view.findViewById(R.id.slider_multithreading_parts)
        textMultithreadingSummary = view.findViewById(R.id.text_multithreading_summary)

        val options = listOf(
            getString(R.string.download_location_option_default),
            getString(R.string.download_location_option_custom)
        )
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, options)
        dropdown.setAdapter(adapter)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val currentMode = prefs.getString(PREF_DOWNLOAD_LOCATION_MODE, MODE_DEFAULT) ?: MODE_DEFAULT

        dropdown.setText(if (currentMode == MODE_CUSTOM) options[1] else options[0], false)
        applyMode(currentMode, prefs)

        dropdown.setOnItemClickListener { _, _, position, _ ->
            val newMode = if (position == 1) MODE_CUSTOM else MODE_DEFAULT
            prefs.edit().putString(PREF_DOWNLOAD_LOCATION_MODE, newMode).apply()
            applyMode(newMode, prefs)
            if (newMode == MODE_CUSTOM) openFolderPicker()
        }

        folderRow.setOnClickListener {
            if ((prefs.getString(PREF_DOWNLOAD_LOCATION_MODE, MODE_DEFAULT) ?: MODE_DEFAULT) == MODE_CUSTOM) {
                openFolderPicker()
            }
        }

        switchUnmeteredOnly.isChecked = prefs.getBoolean(PREF_UNMETERED_ONLY, DEFAULT_UNMETERED_ONLY)
        rowUnmeteredOnly.setOnClickListener {
            val newVal = !switchUnmeteredOnly.isChecked
            prefs.edit().putBoolean(PREF_UNMETERED_ONLY, newVal).apply()
            switchUnmeteredOnly.isChecked = newVal
            com.jhaiian.clint.downloads.ClintDownloadManager.onUnmeteredOnlyChanged(requireContext(), newVal)
        }

        syncRetryUi(prefs)

        rowRetryEnabled.setOnClickListener {
            val newVal = !switchRetryEnabled.isChecked
            prefs.edit().putBoolean(PREF_RETRY_ENABLED, newVal).apply()
            syncRetryUi(prefs)
        }

        rowRetryUnrecoverable.setOnClickListener {
            val newVal = !switchRetryUnrecoverable.isChecked
            prefs.edit().putBoolean(PREF_RETRY_UNRECOVERABLE, newVal).apply()
            syncRetryUi(prefs)
        }

        rowRetryCount.setOnClickListener { showRetryCountDialog(prefs) }
        rowRetryInterval.setOnClickListener { showRetryIntervalDialog(prefs) }

        syncBatteryOptUi()

        rowIgnoreBatteryOpt.setOnClickListener {
            val pm = requireContext().getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(requireContext().packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            }
        }

        switchPushNotifications.isChecked = prefs.getBoolean(PREF_PUSH_NOTIFICATIONS, DEFAULT_PUSH_NOTIFICATIONS)
        rowPushNotifications.setOnClickListener {
            val newVal = !switchPushNotifications.isChecked
            prefs.edit().putBoolean(PREF_PUSH_NOTIFICATIONS, newVal).apply()
            switchPushNotifications.isChecked = newVal
        }

        val currentLimit = prefs.getInt(PREF_CONCURRENT_DOWNLOADS, DEFAULT_CONCURRENT_DOWNLOADS)
        sliderConcurrentDownloads.value = currentLimit.toFloat()
        syncConcurrentUi(currentLimit)

        sliderConcurrentDownloads.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val newLimit = value.toInt()
                prefs.edit().putInt(PREF_CONCURRENT_DOWNLOADS, newLimit).apply()
                syncConcurrentUi(newLimit)
            }
        }

        val currentSplitParts = prefs.getInt(PREF_SPLIT_PARTS, DEFAULT_SPLIT_PARTS)
        sliderSplitParts.value = currentSplitParts.toFloat()
        syncSplitPartsUi(currentSplitParts)

        sliderSplitParts.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val newVal = value.toInt()
                prefs.edit().putInt(PREF_SPLIT_PARTS, newVal).apply()
                syncSplitPartsUi(newVal)
            }
        }

        val currentMultithreadingParts = prefs.getInt(PREF_MULTITHREADING_PARTS, DEFAULT_MULTITHREADING_PARTS)
        sliderMultithreadingParts.value = currentMultithreadingParts.toFloat()
        syncMultithreadingUi(currentMultithreadingParts)

        sliderMultithreadingParts.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val newVal = value.toInt()
                prefs.edit().putInt(PREF_MULTITHREADING_PARTS, newVal).apply()
                syncMultithreadingUi(newVal)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncBatteryOptUi()
    }

    private fun syncConcurrentUi(limit: Int) {
        textConcurrentSummary.text = if (limit == 24) {
            getString(R.string.download_concurrent_value_easter_egg)
        } else {
            resources.getQuantityString(R.plurals.download_concurrent_value, limit, limit)
        }
    }

    private fun syncSplitPartsUi(parts: Int) {
        textSplitPartsSummary.text = resources.getQuantityString(R.plurals.download_split_parts_value, parts, parts)
    }

    private fun syncMultithreadingUi(parts: Int) {
        textMultithreadingSummary.text = resources.getQuantityString(R.plurals.download_multithreading_value, parts, parts)
    }

    private fun syncBatteryOptUi() {
        val pm = requireContext().getSystemService(PowerManager::class.java)
        val granted = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        if (granted) {
            rowIgnoreBatteryOpt.isEnabled = false
            rowIgnoreBatteryOpt.isClickable = false
            rowIgnoreBatteryOpt.alpha = 0.4f
            textIgnoreBatteryOptSummary.text = getString(R.string.download_ignore_battery_opt_granted)
        } else {
            rowIgnoreBatteryOpt.isEnabled = true
            rowIgnoreBatteryOpt.isClickable = true
            rowIgnoreBatteryOpt.alpha = 1f
            textIgnoreBatteryOptSummary.text = getString(R.string.download_ignore_battery_opt_summary)
        }
    }

    private fun syncRetryUi(prefs: android.content.SharedPreferences) {
        val retryEnabled = prefs.getBoolean(PREF_RETRY_ENABLED, DEFAULT_RETRY_ENABLED)
        val retryUnrecoverable = prefs.getBoolean(PREF_RETRY_UNRECOVERABLE, DEFAULT_RETRY_UNRECOVERABLE)
        val retryCount = prefs.getInt(PREF_RETRY_COUNT, DEFAULT_RETRY_COUNT)
        val retryInterval = prefs.getInt(PREF_RETRY_INTERVAL, DEFAULT_RETRY_INTERVAL)

        switchRetryEnabled.isChecked = retryEnabled
        switchRetryUnrecoverable.isChecked = retryUnrecoverable

        val dependentsAlpha = if (retryEnabled) 1f else 0.4f
        rowRetryUnrecoverable.isEnabled = retryEnabled
        rowRetryUnrecoverable.isClickable = retryEnabled
        rowRetryUnrecoverable.alpha = dependentsAlpha
        rowRetryCount.isEnabled = retryEnabled
        rowRetryCount.isClickable = retryEnabled
        rowRetryCount.alpha = dependentsAlpha
        rowRetryInterval.isEnabled = retryEnabled
        rowRetryInterval.isClickable = retryEnabled
        rowRetryInterval.alpha = dependentsAlpha

        textRetryCountSummary.text = if (retryCount == 0) {
            getString(R.string.download_retry_count_unlimited)
        } else {
            getString(R.string.download_retry_count_value, retryCount)
        }
        textRetryIntervalSummary.text = getString(R.string.download_retry_interval_value, retryInterval)
    }

    private fun showRetryCountDialog(prefs: android.content.SharedPreferences) {
        val ctx = requireContext()
        val current = prefs.getInt(PREF_RETRY_COUNT, DEFAULT_RETRY_COUNT)
        val dialogView = layoutInflater.inflate(R.layout.dialog_retry_count, null)
        val editText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_retry_count)
        editText.setText(current.toString())
        editText.selectAll()
        val dialogTheme = (activity as? ClintActivity)?.getDialogTheme() ?: 0
        val builder = if (dialogTheme != 0)
            MaterialAlertDialogBuilder(ctx, dialogTheme)
        else
            MaterialAlertDialogBuilder(ctx)
        builder.setTitle(getString(R.string.download_retry_count_dialog_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_ok)) { _, _ ->
                val value = editText.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: DEFAULT_RETRY_COUNT
                prefs.edit().putInt(PREF_RETRY_COUNT, value).apply()
                syncRetryUi(prefs)
            }
            .show()
        editText.post {
            editText.requestFocus()
            val imm = ctx.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showRetryIntervalDialog(prefs: android.content.SharedPreferences) {
        val ctx = requireContext()
        val current = prefs.getInt(PREF_RETRY_INTERVAL, DEFAULT_RETRY_INTERVAL)
        val dialogView = layoutInflater.inflate(R.layout.dialog_retry_interval, null)
        val editText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_retry_interval)
        editText.setText(current.toString())
        editText.selectAll()
        val dialogTheme = (activity as? ClintActivity)?.getDialogTheme() ?: 0
        val builder = if (dialogTheme != 0)
            MaterialAlertDialogBuilder(ctx, dialogTheme)
        else
            MaterialAlertDialogBuilder(ctx)
        builder.setTitle(getString(R.string.download_retry_interval_dialog_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_ok)) { _, _ ->
                val value = editText.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_RETRY_INTERVAL
                prefs.edit().putInt(PREF_RETRY_INTERVAL, value).apply()
                syncRetryUi(prefs)
            }
            .show()
        editText.post {
            editText.requestFocus()
            val imm = ctx.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun applyMode(mode: String, prefs: android.content.SharedPreferences) {
        val customUri = prefs.getString(PREF_DOWNLOAD_CUSTOM_URI, null)?.let { Uri.parse(it) }
        if (mode == MODE_CUSTOM) {
            folderPathText.text = if (customUri != null) {
                uriToDisplayPath(customUri)
            } else {
                getString(R.string.download_location_tap_to_choose)
            }
            folderRow.isEnabled = true
            folderRow.isClickable = true
            folderIcon.alpha = 1f
            folderPathText.alpha = 1f
        } else {
            folderPathText.text = DEFAULT_PATH
            folderRow.isEnabled = false
            folderRow.isClickable = false
            folderIcon.alpha = 0.4f
            folderPathText.alpha = 0.4f
        }
        updateStorageInfo(requireContext(), tvStorageInfo, mode, customUri)
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        folderPickerLauncher.launch(intent)
    }

    private fun saveCustomUri(uri: Uri) {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putString(PREF_DOWNLOAD_CUSTOM_URI, uri.toString())
            .apply()
    }

    private fun uriToDisplayPath(uri: Uri): String {
        val path = uri.lastPathSegment ?: return uri.toString()
        return when {
            path.startsWith("primary:") -> {
                val relative = path.removePrefix("primary:")
                "/storage/emulated/0/$relative/"
            }
            path.contains(":") -> {
                val parts = path.split(":", limit = 2)
                "/storage/${parts[0]}/${parts[1]}/"
            }
            else -> uri.toString()
        }
    }
}

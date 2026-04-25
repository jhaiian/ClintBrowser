package com.jhaiian.clint.settings

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.DocumentViewer
import com.jhaiian.clint.update.UpdateChecker

class UpdateSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.update_preferences, rootKey)
        applyIconTints()

        val checkOnLaunch = findPreference<SwitchPreferenceCompat>("check_update_on_launch")
        val skipOnMetered = findPreference<SwitchPreferenceCompat>("skip_update_on_metered")

        skipOnMetered?.isEnabled = checkOnLaunch?.isChecked == true

        checkOnLaunch?.setOnPreferenceChangeListener { _, newValue ->
            skipOnMetered?.isEnabled = newValue as Boolean
            true
        }

        findPreference<Preference>("check_for_updates")?.setOnPreferenceClickListener {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val isBeta = prefs.getBoolean("beta_channel", false)
            UpdateChecker.check(requireActivity(), isBeta, silent = false)
            true
        }

        findPreference<Preference>("view_changelog")?.setOnPreferenceClickListener {
            DocumentViewer.show(
                requireContext(),
                getString(R.string.document_viewer_changelog_title),
                DocumentViewer.CHANGELOG_URL
            )
            true
        }

        findPreference<SwitchPreferenceCompat>("beta_channel")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                val activity = requireActivity() as com.jhaiian.clint.base.ClintActivity
                MaterialAlertDialogBuilder(requireContext(), activity.getDialogTheme())
                    .setTitle(getString(R.string.beta_enrol_title))
                    .setMessage(getString(R.string.beta_enrol_message))
                    .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
                        findPreference<SwitchPreferenceCompat>("beta_channel")?.isChecked = false
                    }
                    .setPositiveButton(getString(R.string.beta_enrol_confirm)) { _, _ ->
                        findPreference<SwitchPreferenceCompat>("beta_channel")?.isChecked = true
                    }
                    .create().also { activity.applyStatusBarFlagToDialog(it) }.show()
                false
            } else {
                true
            }
        }
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        listOf("check_update_on_launch", "skip_update_on_metered", "check_for_updates", "view_changelog", "beta_channel").forEach { key ->
            findPreference<Preference>(key)?.let { pref ->
                pref.icon?.mutate()?.let { icon ->
                    DrawableCompat.setTintList(DrawableCompat.wrap(icon), tint)
                    pref.icon = icon
                }
            }
        }
    }
}

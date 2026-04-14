package com.jhaiian.clint.settings

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity

class LookAndFeelFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.look_and_feel_preferences, rootKey)

        findPreference<Preference>("app_theme")
            ?.setOnPreferenceClickListener {
                showThemeDialog()
                true
            }

        findPreference<SwitchPreferenceCompat>("hide_status_bar")
            ?.setOnPreferenceChangeListener { _, newValue ->
                showRestartDialog(newValue as Boolean)
                true
            }
    }

    override fun onResume() {
        super.onResume()
        updateDarkWebSwitchState()
    }

    private fun updateDarkWebSwitchState() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val theme = prefs.getString("app_theme", "default") ?: "default"
        val pref = findPreference<SwitchPreferenceCompat>("force_dark_web") ?: return
        val savedValue = prefs.getBoolean("force_dark_web", false)
        when (theme) {
            "dark" -> {
                pref.isEnabled = false
                pref.isChecked = true
                prefs.edit().putBoolean("force_dark_web", savedValue).apply()
            }
            "light" -> {
                pref.isEnabled = false
                pref.isChecked = false
                prefs.edit().putBoolean("force_dark_web", savedValue).apply()
            }
            else -> {
                pref.isEnabled = true
                pref.isChecked = savedValue
            }
        }
    }

    private fun applyTheme(newTheme: String) {
        val activity = requireActivity() as? ClintActivity ?: return
        activity.captureAndRecreate(newTheme)
    }

    private fun showThemeDialog() {
        val activity = requireActivity() as? ClintActivity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("app_theme", "default") ?: "default"

        val dialogView = layoutInflater.inflate(R.layout.dialog_theme_selector, null)
        val optionDefault = dialogView.findViewById<View>(R.id.option_default)
        val optionDark = dialogView.findViewById<View>(R.id.option_dark)
        val optionLight = dialogView.findViewById<View>(R.id.option_light)
        val checkDefault = dialogView.findViewById<ImageView>(R.id.check_default)
        val checkDark = dialogView.findViewById<ImageView>(R.id.check_dark)
        val checkLight = dialogView.findViewById<ImageView>(R.id.check_light)

        checkDefault.visibility = if (current == "default") View.VISIBLE else View.INVISIBLE
        checkDark.visibility = if (current == "dark") View.VISIBLE else View.INVISIBLE
        checkLight.visibility = if (current == "light") View.VISIBLE else View.INVISIBLE

        val dialog = MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.pref_app_theme_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()

        optionDefault.setOnClickListener {
            dialog.dismiss()
            applyTheme("default")
        }

        optionDark.setOnClickListener {
            dialog.dismiss()
            applyTheme("dark")
        }

        optionLight.setOnClickListener {
            dialog.dismiss()
            applyTheme("light")
        }

        dialog.show()
    }

    private fun showRestartDialog(newValue: Boolean) {
        val activity = requireActivity() as? SettingsActivity ?: return
        val pref = findPreference<SwitchPreferenceCompat>("hide_status_bar") ?: return
        MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.restart_required_title))
            .setMessage(getString(R.string.restart_required_message))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
                pref.isChecked = !newValue
                activity.pendingRestart = false
            }
            .setNeutralButton(getString(R.string.restart_required_confirm)) { _, _ ->
                activity.restartApp()
            }
            .setPositiveButton(getString(R.string.action_later)) { _, _ ->
                activity.scheduleRestartIfChanged()
            }
            .show()
    }
}

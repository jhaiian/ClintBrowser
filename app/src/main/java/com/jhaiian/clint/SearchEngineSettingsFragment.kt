package com.jhaiian.clint

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SearchEngineSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.search_engine_preferences, rootKey)
        findPreference<ListPreference>("search_engine")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == "google") {
                showGoogleWarning()
                false
            } else {
                true
            }
        }
    }

    private fun showGoogleWarning() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.google_warning_title))
            .setMessage(getString(R.string.google_warning_message))
            .setNegativeButton(getString(R.string.choose_another), null)
            .setPositiveButton(getString(R.string.use_google_anyway)) { _, _ ->
                val pref = findPreference<ListPreference>("search_engine")
                pref?.value = "google"
            }
            .show()
    }
}

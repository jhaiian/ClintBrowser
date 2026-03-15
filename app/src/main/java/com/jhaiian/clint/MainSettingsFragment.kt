package com.jhaiian.clint

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class MainSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        updateSearchEngineSummary()
    }

    private fun updateSearchEngineSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val engine = prefs.getString("search_engine", "duckduckgo")
        val label = when (engine) {
            "brave" -> getString(R.string.engine_brave)
            "google" -> getString(R.string.engine_google)
            else -> getString(R.string.engine_duckduckgo)
        }
        findPreference<Preference>("pref_search_engine")?.summary = label
    }
}

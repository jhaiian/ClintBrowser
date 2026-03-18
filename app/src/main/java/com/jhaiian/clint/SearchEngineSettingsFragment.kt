package com.jhaiian.clint

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SearchEngineSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.search_engine_preferences, rootKey)
        findPreference<Preference>("search_engine")?.setOnPreferenceClickListener {
            showEngineDialog()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateSummary()
    }

    private fun updateSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val label = when (prefs.getString("search_engine", "duckduckgo")) {
            "brave"  -> getString(R.string.engine_brave)
            "google" -> getString(R.string.engine_google)
            else     -> getString(R.string.engine_duckduckgo)
        }
        findPreference<Preference>("search_engine")?.summary = label
    }

    private fun showEngineDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo"

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search_engine, null)

        val radios = mapOf(
            "duckduckgo" to dialogView.findViewById<RadioButton>(R.id.radioDuckDialog),
            "brave"      to dialogView.findViewById<RadioButton>(R.id.radioBraveDialog),
            "google"     to dialogView.findViewById<RadioButton>(R.id.radioGoogleDialog)
        )

        var selected = current

        fun selectEngine(key: String) {
            selected = key
            radios.forEach { (k, radio) -> radio.isChecked = k == key }
        }

        selectEngine(current)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val key = when (checkedId) {
                R.id.radioBraveDialog  -> "brave"
                R.id.radioGoogleDialog -> "google"
                else                   -> "duckduckgo"
            }
            selectEngine(key)
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ClintBrowser_Dialog)
            .setTitle(getString(R.string.choose_search_engine))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selected == "google" && current != "google") {
                    showGoogleWarning { confirmEngine("google") }
                } else {
                    confirmEngine(selected)
                }
            }
            .show()
    }

    private fun confirmEngine(engine: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit().putString("search_engine", engine).apply()
        updateSummary()
    }

    private fun showGoogleWarning(onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ClintBrowser_Dialog)
            .setTitle(getString(R.string.google_warning_title))
            .setMessage(getString(R.string.google_warning_message))
            .setNegativeButton(getString(R.string.choose_another), null)
            .setPositiveButton(getString(R.string.use_google_anyway)) { _, _ -> onConfirm() }
            .show()
    }
}

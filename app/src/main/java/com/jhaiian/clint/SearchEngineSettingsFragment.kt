package com.jhaiian.clint

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
        val engine = prefs.getString("search_engine", "duckduckgo")
        val label = when (engine) {
            "brave"  -> getString(R.string.engine_brave)
            "google" -> getString(R.string.engine_google)
            else     -> getString(R.string.engine_duckduckgo)
        }
        findPreference<Preference>("search_engine")?.summary = label
    }

    private fun showEngineDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo"

        val engines = listOf(
            Triple("duckduckgo", getString(R.string.engine_duckduckgo), getString(R.string.engine_duckduckgo_desc)),
            Triple("brave",      getString(R.string.engine_brave),      getString(R.string.engine_brave_desc)),
            Triple("google",     getString(R.string.engine_google),     getString(R.string.engine_google_desc))
        )

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search_engine, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)

        val radioIds = mutableMapOf<String, Int>()
        engines.forEach { (key, _, _) ->
            val btn = dialogView.findViewById<RadioButton>(
                when (key) {
                    "brave"  -> R.id.radioBraveDialog
                    "google" -> R.id.radioGoogleDialog
                    else     -> R.id.radioDuckDialog
                }
            )
            radioIds[key] = btn.id
            if (key == current) btn.isChecked = true
        }

        var selectedEngine = current

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedEngine = radioIds.entries.firstOrNull { it.value == checkedId }?.key ?: current
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ClintBrowser_Dialog)
            .setTitle(getString(R.string.choose_search_engine))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selectedEngine == "google" && current != "google") {
                    showGoogleWarning { confirmEngine("google") }
                } else {
                    confirmEngine(selectedEngine)
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

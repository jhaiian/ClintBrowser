package com.jhaiian.clint.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity

class BrowserSettingsFragment : Fragment() {

    private lateinit var rowSearchEngine: LinearLayout
    private lateinit var textSearchEngineSummary: TextView
    private lateinit var rowJavascriptEnabled: LinearLayout
    private lateinit var switchJavascriptEnabled: Switch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_browser_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rowSearchEngine = view.findViewById(R.id.row_search_engine)
        textSearchEngineSummary = view.findViewById(R.id.text_search_engine_summary)
        rowJavascriptEnabled = view.findViewById(R.id.row_javascript_enabled)
        switchJavascriptEnabled = view.findViewById(R.id.switch_javascript_enabled)

        rowSearchEngine.setOnClickListener { showEngineDialog() }
        rowJavascriptEnabled.setOnClickListener {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val current = prefs.getBoolean("javascript_enabled", true)
            if (current) {
                showJavaScriptWarning()
            } else {
                switchJavascriptEnabled.isChecked = true
                prefs.edit().putBoolean("javascript_enabled", true).apply()
            }
        }

        updateAll()
    }

    override fun onResume() {
        super.onResume()
        updateAll()
    }

    private fun updateAll() {
        updateEngineSummary()
        updateJavascriptSwitch()
    }

    private fun updateEngineSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        textSearchEngineSummary.text = when (prefs.getString("search_engine", "duckduckgo")) {
            "brave" -> getString(R.string.engine_brave)
            "google" -> getString(R.string.engine_google)
            else -> getString(R.string.engine_duckduckgo)
        }
    }

    private fun updateJavascriptSwitch() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        switchJavascriptEnabled.isChecked = prefs.getBoolean("javascript_enabled", true)
    }

    private fun showJavaScriptWarning() {
        MaterialAlertDialogBuilder(requireContext(), (requireActivity() as ClintActivity).getDialogTheme())
            .setTitle(getString(R.string.js_warning_title))
            .setMessage(getString(R.string.js_warning_message))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_turn_off_anyway)) { _, _ ->
                switchJavascriptEnabled.isChecked = false
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putBoolean("javascript_enabled", false).apply()
            }
            .create().also { (requireActivity() as ClintActivity).applyStatusBarFlagToDialog(it) }.show()
    }

    private fun showEngineDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo"

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search_engine, null)

        val cardDuck = dialogView.findViewById<MaterialCardView>(R.id.cardDialogDuck)
        val cardBrave = dialogView.findViewById<MaterialCardView>(R.id.cardDialogBrave)
        val cardGoogle = dialogView.findViewById<MaterialCardView>(R.id.cardDialogGoogle)

        val radioDuck = dialogView.findViewById<RadioButton>(R.id.radioDuckDialog)
        val radioBrave = dialogView.findViewById<RadioButton>(R.id.radioBraveDialog)
        val radioGoogle = dialogView.findViewById<RadioButton>(R.id.radioGoogleDialog)

        val cardMap = mapOf(
            "duckduckgo" to cardDuck,
            "brave" to cardBrave,
            "google" to cardGoogle
        )
        val radioMap = mapOf(
            "duckduckgo" to radioDuck,
            "brave" to radioBrave,
            "google" to radioGoogle
        )

        var selected = current
        val strokePx = (3 * resources.displayMetrics.density).toInt()

        fun selectEngine(key: String) {
            selected = key
            cardMap.forEach { (k, card) ->
                val active = k == key
                card.strokeWidth = if (active) strokePx else 0
                card.alpha = if (active) 1f else 0.45f
                radioMap[k]?.isChecked = active
            }
        }

        selectEngine(current)

        cardDuck.setOnClickListener { selectEngine("duckduckgo") }
        cardBrave.setOnClickListener { selectEngine("brave") }
        cardGoogle.setOnClickListener { selectEngine("google") }

        MaterialAlertDialogBuilder(requireContext(), (requireActivity() as ClintActivity).getDialogTheme())
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
            .create().also { (requireActivity() as ClintActivity).applyStatusBarFlagToDialog(it) }.show()
    }

    private fun confirmEngine(engine: String) {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit().putString("search_engine", engine).apply()
        updateEngineSummary()
    }

    private fun showGoogleWarning(onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext(), (requireActivity() as ClintActivity).getDialogTheme())
            .setTitle(getString(R.string.google_warning_title))
            .setMessage(getString(R.string.google_warning_message))
            .setNegativeButton(getString(R.string.choose_another), null)
            .setPositiveButton(getString(R.string.use_google_anyway)) { _, _ -> onConfirm() }
            .create().also { (requireActivity() as ClintActivity).applyStatusBarFlagToDialog(it) }.show()
    }
}

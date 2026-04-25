package com.jhaiian.clint.settings

import android.app.role.RoleManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.RadioButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity

class GeneralSettingsFragment : PreferenceFragmentCompat() {

    private val browserRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateDefaultBrowserSummary()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.general_preferences, rootKey)
        applyIconTints()

        findPreference<Preference>("default_browser")?.setOnPreferenceClickListener {
            openDefaultBrowserPicker()
            true
        }

        findPreference<Preference>("search_engine")?.setOnPreferenceClickListener {
            showEngineDialog()
            true
        }

        findPreference<Preference>("exit_confirmation")?.setOnPreferenceClickListener {
            showExitConfirmationDialog()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateSummary()
        updateDefaultBrowserSummary()
        updateExitConfirmationSummary()
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        listOf("search_engine", "default_browser", "exit_confirmation").forEach { key ->
            findPreference<Preference>(key)?.let { pref ->
                pref.icon?.mutate()?.let { icon ->
                    DrawableCompat.setTintList(DrawableCompat.wrap(icon), tint)
                    pref.icon = icon
                }
            }
        }
    }

    private fun updateDefaultBrowserSummary() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
        val resolveInfo = requireContext().packageManager.resolveActivity(
            intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        val label = resolveInfo?.loadLabel(requireContext().packageManager)?.toString()
            ?: getString(R.string.default_browser_none)
        findPreference<Preference>("default_browser")?.summary = label
    }

    private fun openDefaultBrowserPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = requireContext().getSystemService(RoleManager::class.java)
                if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    browserRoleLauncher.launch(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
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

    private fun updateExitConfirmationSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val value = prefs.getString("exit_confirmation", "toast") ?: "toast"
        findPreference<Preference>("exit_confirmation")?.summary = when (value) {
            "off"    -> getString(R.string.exit_confirmation_off)
            "dialog" -> getString(R.string.exit_confirmation_dialog)
            else     -> getString(R.string.exit_confirmation_toast)
        }
    }

    private fun showExitConfirmationDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("exit_confirmation", "toast") ?: "toast"

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_exit_confirmation, null)

        val cardOff    = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardExitOff)
        val cardToast  = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardExitToast)
        val cardDialog = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardExitDialog)

        val radioOff    = dialogView.findViewById<RadioButton>(R.id.radioExitOff)
        val radioToast  = dialogView.findViewById<RadioButton>(R.id.radioExitToast)
        val radioDialog = dialogView.findViewById<RadioButton>(R.id.radioExitDialog)

        val cardMap = mapOf(
            "off"    to cardOff,
            "toast"  to cardToast,
            "dialog" to cardDialog
        )
        val radioMap = mapOf(
            "off"    to radioOff,
            "toast"  to radioToast,
            "dialog" to radioDialog
        )

        var selected = current

        val strokePx = (3 * resources.displayMetrics.density).toInt()

        fun selectOption(key: String) {
            selected = key
            cardMap.forEach { (k, card) ->
                val active = k == key
                card.strokeWidth = if (active) strokePx else 0
                card.alpha = if (active) 1f else 0.45f
                radioMap[k]?.isChecked = active
            }
        }

        selectOption(current)

        cardOff.setOnClickListener    { selectOption("off") }
        cardToast.setOnClickListener  { selectOption("toast") }
        cardDialog.setOnClickListener { selectOption("dialog") }

        MaterialAlertDialogBuilder(requireContext(), (requireActivity() as ClintActivity).getDialogTheme())
            .setTitle(getString(R.string.exit_confirmation_title))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putString("exit_confirmation", selected).apply()
                updateExitConfirmationSummary()
            }
            .create().also { (requireActivity() as ClintActivity).applyStatusBarFlagToDialog(it) }.show()
    }

    private fun showEngineDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo"

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search_engine, null)

        val cardDuck   = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardDialogDuck)
        val cardBrave  = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardDialogBrave)
        val cardGoogle = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardDialogGoogle)

        val radioDuck   = dialogView.findViewById<RadioButton>(R.id.radioDuckDialog)
        val radioBrave  = dialogView.findViewById<RadioButton>(R.id.radioBraveDialog)
        val radioGoogle = dialogView.findViewById<RadioButton>(R.id.radioGoogleDialog)

        val cardMap = mapOf(
            "duckduckgo" to cardDuck,
            "brave"      to cardBrave,
            "google"     to cardGoogle
        )
        val radioMap = mapOf(
            "duckduckgo" to radioDuck,
            "brave"      to radioBrave,
            "google"     to radioGoogle
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

        cardDuck.setOnClickListener   { selectEngine("duckduckgo") }
        cardBrave.setOnClickListener  { selectEngine("brave") }
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
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit().putString("search_engine", engine).apply()
        updateSummary()
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


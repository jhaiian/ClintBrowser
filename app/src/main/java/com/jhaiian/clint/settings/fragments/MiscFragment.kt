package com.jhaiian.clint.settings.fragments

import android.app.role.RoleManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.setup.SetupActivity

class MiscFragment : PreferenceFragmentCompat() {

    private val browserRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateDefaultBrowserSummary()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.misc_preferences, rootKey)
        applyIconTints()

        findPreference<Preference>("default_browser")?.setOnPreferenceClickListener {
            openDefaultBrowserPicker()
            true
        }

        findPreference<Preference>("rerun_setup")
            ?.setOnPreferenceClickListener {
                showRerunSetupDialog()
                true
            }
    }

    override fun onResume() {
        super.onResume()
        updateDefaultBrowserSummary()
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        listOf("default_browser", "rerun_setup").forEach { key ->
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

    private fun showRerunSetupDialog() {
        val activity = requireActivity() as? ClintActivity ?: return
        MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.rerun_setup_confirm_title))
            .setMessage(getString(R.string.rerun_setup_confirm_message))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_rerun)) { _, _ ->
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .remove("setup_complete")
                    .apply()
                val intent = Intent(activity, SetupActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            .create().also { activity.applyStatusBarFlagToDialog(it) }.show()
    }
}

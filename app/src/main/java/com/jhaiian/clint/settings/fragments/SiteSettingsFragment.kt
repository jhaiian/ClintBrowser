package com.jhaiian.clint.settings.fragments
import com.jhaiian.clint.settings.sitepermissions.SitePermissionActivity
import com.jhaiian.clint.settings.desktopmode.DesktopModeActivity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase

class SiteSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.site_settings_preferences, rootKey)
        applyIconTints()

        findPreference<Preference>("pref_site_camera")?.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), SitePermissionActivity::class.java)
                    .putExtra(SitePermissionActivity.EXTRA_TYPE, SitePermissionDatabase.TYPE_CAMERA)
            )
            true
        }

        findPreference<Preference>("pref_site_mic")?.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), SitePermissionActivity::class.java)
                    .putExtra(SitePermissionActivity.EXTRA_TYPE, SitePermissionDatabase.TYPE_MIC)
            )
            true
        }

        findPreference<Preference>("pref_site_location")?.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), SitePermissionActivity::class.java)
                    .putExtra(SitePermissionActivity.EXTRA_TYPE, SitePermissionDatabase.TYPE_LOCATION)
            )
            true
        }

        findPreference<Preference>("pref_site_notifications")?.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), SitePermissionActivity::class.java)
                    .putExtra(SitePermissionActivity.EXTRA_TYPE, SitePermissionDatabase.TYPE_NOTIFICATION)
            )
            true
        }

        findPreference<Preference>("pref_site_desktop_mode")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), DesktopModeActivity::class.java))
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateSummaries()
    }

    private fun updateSummaries() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val typeToKey = mapOf(
            "pref_site_camera" to "site_perm_default_${SitePermissionDatabase.TYPE_CAMERA}",
            "pref_site_mic" to "site_perm_default_${SitePermissionDatabase.TYPE_MIC}",
            "pref_site_location" to "site_perm_default_${SitePermissionDatabase.TYPE_LOCATION}",
            "pref_site_notifications" to "site_perm_default_${SitePermissionDatabase.TYPE_NOTIFICATION}"
        )

        typeToKey.forEach { (prefKey, storedKey) ->
            val behavior = prefs.getString(storedKey, SitePermissionActivity.PREF_VALUE_ASK)
            val label = when (behavior) {
                SitePermissionActivity.PREF_VALUE_DENY -> getString(R.string.site_permission_always_deny)
                SitePermissionActivity.PREF_VALUE_ALLOW -> getString(R.string.site_permission_always_allow)
                else -> getString(R.string.site_permission_ask_first)
            }
            findPreference<Preference>(prefKey)?.summary = label
        }

        val saveState = prefs.getString(DesktopModeActivity.PREF_DESKTOP_MODE_SAVE_STATE, DesktopModeActivity.VALUE_SAVE_STATE)
        findPreference<Preference>("pref_site_desktop_mode")?.summary = when (saveState) {
            DesktopModeActivity.VALUE_DO_NOT_SAVE -> getString(R.string.desktop_mode_do_not_save_state)
            else -> getString(R.string.desktop_mode_save_state)
        }
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        listOf("pref_site_camera", "pref_site_mic", "pref_site_location", "pref_site_notifications", "pref_site_desktop_mode").forEach { key ->
            findPreference<Preference>(key)?.let { pref ->
                pref.icon?.mutate()?.let { icon ->
                    DrawableCompat.setTintList(DrawableCompat.wrap(icon), tint)
                    pref.icon = icon
                }
            }
        }
    }
}

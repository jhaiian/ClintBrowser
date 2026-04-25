package com.jhaiian.clint.settings

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R

class MainSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preferences, rootKey)
        applyIconTints()
    }

    override fun onResume() {
        super.onResume()
        updateVersionSummary()
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        listOf("pref_general", "pref_look_and_feel", "pref_privacy", "pref_doh", "pref_debug", "pref_updates", "pref_misc", "pref_about").forEach { key ->
            findPreference<Preference>(key)?.let { pref ->
                pref.icon?.mutate()?.let { icon ->
                    DrawableCompat.setTintList(DrawableCompat.wrap(icon), tint)
                    pref.icon = icon
                }
            }
        }
    }

    private fun updateVersionSummary() {
        val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        findPreference<Preference>("pref_about")?.summary =
            getString(R.string.about_summary, pInfo.versionName)
    }
}

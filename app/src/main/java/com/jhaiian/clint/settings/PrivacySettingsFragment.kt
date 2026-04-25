package com.jhaiian.clint.settings

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R

class PrivacySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.privacy_preferences, rootKey)
        applyIconTints()
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        listOf("block_trackers", "block_third_party_cookies", "javascript_enabled", "custom_user_agent").forEach { key ->
            findPreference<Preference>(key)?.let { pref ->
                pref.icon?.mutate()?.let { icon ->
                    DrawableCompat.setTintList(DrawableCompat.wrap(icon), tint)
                    pref.icon = icon
                }
            }
        }
    }
}

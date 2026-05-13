package com.jhaiian.clint.settings.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.history.HistoryActivity

class PrivacySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.privacy_preferences, rootKey)
        applyIconTints()

        findPreference<Preference>("history")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
            true
        }
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        listOf("block_trackers", "block_third_party_cookies", "custom_user_agent", "https_only", "history").forEach { key ->
            findPreference<Preference>(key)?.let { pref ->
                pref.icon?.mutate()?.let { icon ->
                    DrawableCompat.setTintList(DrawableCompat.wrap(icon), tint)
                    pref.icon = icon
                }
            }
        }
    }
}

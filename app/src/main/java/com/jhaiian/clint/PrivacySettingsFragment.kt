package com.jhaiian.clint

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class PrivacySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.privacy_preferences, rootKey)
    }
}

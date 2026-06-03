package com.jhaiian.clint.settings.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.history.HistoryActivity

class PrivacySettingsFragment : Fragment() {

    companion object {
        const val PREF_BLOCK_TRACKERS = "block_trackers"
        const val PREF_BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
        const val PREF_CUSTOM_USER_AGENT = "custom_user_agent"
        const val PREF_HTTPS_ONLY = "https_only"
        const val DEFAULT_BLOCK_TRACKERS = true
        const val DEFAULT_BLOCK_THIRD_PARTY_COOKIES = true
        const val DEFAULT_CUSTOM_USER_AGENT = true
        const val DEFAULT_HTTPS_ONLY = true
    }

    private lateinit var rowBlockTrackers: LinearLayout
    private lateinit var switchBlockTrackers: Switch
    private lateinit var rowBlockThirdPartyCookies: LinearLayout
    private lateinit var switchBlockThirdPartyCookies: Switch
    private lateinit var rowCustomUserAgent: LinearLayout
    private lateinit var switchCustomUserAgent: Switch
    private lateinit var rowHttpsOnly: LinearLayout
    private lateinit var switchHttpsOnly: Switch
    private lateinit var rowHistory: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_privacy_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rowBlockTrackers = view.findViewById(R.id.row_block_trackers)
        switchBlockTrackers = view.findViewById(R.id.switch_block_trackers)
        rowBlockThirdPartyCookies = view.findViewById(R.id.row_block_third_party_cookies)
        switchBlockThirdPartyCookies = view.findViewById(R.id.switch_block_third_party_cookies)
        rowCustomUserAgent = view.findViewById(R.id.row_custom_user_agent)
        switchCustomUserAgent = view.findViewById(R.id.switch_custom_user_agent)
        rowHttpsOnly = view.findViewById(R.id.row_https_only)
        switchHttpsOnly = view.findViewById(R.id.switch_https_only)
        rowHistory = view.findViewById(R.id.row_history)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        switchBlockTrackers.isChecked = prefs.getBoolean(PREF_BLOCK_TRACKERS, DEFAULT_BLOCK_TRACKERS)
        switchBlockThirdPartyCookies.isChecked = prefs.getBoolean(PREF_BLOCK_THIRD_PARTY_COOKIES, DEFAULT_BLOCK_THIRD_PARTY_COOKIES)
        switchCustomUserAgent.isChecked = prefs.getBoolean(PREF_CUSTOM_USER_AGENT, DEFAULT_CUSTOM_USER_AGENT)
        switchHttpsOnly.isChecked = prefs.getBoolean(PREF_HTTPS_ONLY, DEFAULT_HTTPS_ONLY)

        rowBlockTrackers.setOnClickListener {
            val newVal = !switchBlockTrackers.isChecked
            prefs.edit().putBoolean(PREF_BLOCK_TRACKERS, newVal).apply()
            switchBlockTrackers.isChecked = newVal
        }

        rowBlockThirdPartyCookies.setOnClickListener {
            val newVal = !switchBlockThirdPartyCookies.isChecked
            prefs.edit().putBoolean(PREF_BLOCK_THIRD_PARTY_COOKIES, newVal).apply()
            switchBlockThirdPartyCookies.isChecked = newVal
        }

        rowCustomUserAgent.setOnClickListener {
            val newVal = !switchCustomUserAgent.isChecked
            prefs.edit().putBoolean(PREF_CUSTOM_USER_AGENT, newVal).apply()
            switchCustomUserAgent.isChecked = newVal
        }

        rowHttpsOnly.setOnClickListener {
            val newVal = !switchHttpsOnly.isChecked
            prefs.edit().putBoolean(PREF_HTTPS_ONLY, newVal).apply()
            switchHttpsOnly.isChecked = newVal
        }

        rowHistory.setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
    }
}

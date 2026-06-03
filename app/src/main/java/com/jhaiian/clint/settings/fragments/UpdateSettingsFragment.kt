package com.jhaiian.clint.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.DocumentViewer
import com.jhaiian.clint.update.UpdateChecker

class UpdateSettingsFragment : Fragment() {

    companion object {
        const val PREF_CHECK_UPDATE_ON_LAUNCH = "check_update_on_launch"
        const val PREF_SKIP_UPDATE_ON_METERED = "skip_update_on_metered"
        const val PREF_BETA_CHANNEL = "beta_channel"
        const val DEFAULT_CHECK_UPDATE_ON_LAUNCH = true
        const val DEFAULT_SKIP_UPDATE_ON_METERED = true
        const val DEFAULT_BETA_CHANNEL = false
    }

    private lateinit var rowCheckUpdateOnLaunch: LinearLayout
    private lateinit var switchCheckUpdateOnLaunch: Switch
    private lateinit var rowSkipUpdateOnMetered: LinearLayout
    private lateinit var switchSkipUpdateOnMetered: Switch
    private lateinit var rowCheckForUpdates: LinearLayout
    private lateinit var rowViewChangelog: LinearLayout
    private lateinit var rowBetaChannel: LinearLayout
    private lateinit var switchBetaChannel: Switch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_update_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rowCheckUpdateOnLaunch = view.findViewById(R.id.row_check_update_on_launch)
        switchCheckUpdateOnLaunch = view.findViewById(R.id.switch_check_update_on_launch)
        rowSkipUpdateOnMetered = view.findViewById(R.id.row_skip_update_on_metered)
        switchSkipUpdateOnMetered = view.findViewById(R.id.switch_skip_update_on_metered)
        rowCheckForUpdates = view.findViewById(R.id.row_check_for_updates)
        rowViewChangelog = view.findViewById(R.id.row_view_changelog)
        rowBetaChannel = view.findViewById(R.id.row_beta_channel)
        switchBetaChannel = view.findViewById(R.id.switch_beta_channel)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        switchCheckUpdateOnLaunch.isChecked = prefs.getBoolean(PREF_CHECK_UPDATE_ON_LAUNCH, DEFAULT_CHECK_UPDATE_ON_LAUNCH)
        switchSkipUpdateOnMetered.isChecked = prefs.getBoolean(PREF_SKIP_UPDATE_ON_METERED, DEFAULT_SKIP_UPDATE_ON_METERED)
        switchBetaChannel.isChecked = prefs.getBoolean(PREF_BETA_CHANNEL, DEFAULT_BETA_CHANNEL)

        syncMeteredDependentState(switchCheckUpdateOnLaunch.isChecked)

        rowCheckUpdateOnLaunch.setOnClickListener {
            val newVal = !switchCheckUpdateOnLaunch.isChecked
            prefs.edit().putBoolean(PREF_CHECK_UPDATE_ON_LAUNCH, newVal).apply()
            switchCheckUpdateOnLaunch.isChecked = newVal
            syncMeteredDependentState(newVal)
        }

        rowSkipUpdateOnMetered.setOnClickListener {
            val newVal = !switchSkipUpdateOnMetered.isChecked
            prefs.edit().putBoolean(PREF_SKIP_UPDATE_ON_METERED, newVal).apply()
            switchSkipUpdateOnMetered.isChecked = newVal
        }

        rowCheckForUpdates.setOnClickListener {
            val isBeta = prefs.getBoolean(PREF_BETA_CHANNEL, DEFAULT_BETA_CHANNEL)
            UpdateChecker.check(requireActivity(), isBeta, silent = false)
        }

        rowViewChangelog.setOnClickListener {
            DocumentViewer.show(
                requireContext(),
                getString(R.string.document_viewer_changelog_title),
                DocumentViewer.CHANGELOG_URL
            )
        }

        rowBetaChannel.setOnClickListener {
            if (!switchBetaChannel.isChecked) {
                val activity = requireActivity() as ClintActivity
                MaterialAlertDialogBuilder(requireContext(), activity.getDialogTheme())
                    .setTitle(getString(R.string.beta_enrol_title))
                    .setMessage(getString(R.string.beta_enrol_message))
                    .setNegativeButton(getString(R.string.action_cancel), null)
                    .setPositiveButton(getString(R.string.beta_enrol_confirm)) { _, _ ->
                        prefs.edit().putBoolean(PREF_BETA_CHANNEL, true).apply()
                        switchBetaChannel.isChecked = true
                    }
                    .create().also { activity.applyStatusBarFlagToDialog(it) }.show()
            } else {
                prefs.edit().putBoolean(PREF_BETA_CHANNEL, false).apply()
                switchBetaChannel.isChecked = false
            }
        }
    }

    private fun syncMeteredDependentState(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.4f
        rowSkipUpdateOnMetered.isEnabled = enabled
        rowSkipUpdateOnMetered.isClickable = enabled
        rowSkipUpdateOnMetered.alpha = alpha
    }
}

package com.jhaiian.clint.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.misc_preferences, rootKey)
        applyIconTints()

        findPreference<Preference>("rerun_setup")
            ?.setOnPreferenceClickListener {
                showRerunSetupDialog()
                true
            }
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        findPreference<Preference>("rerun_setup")?.let { pref ->
            pref.icon?.mutate()?.let { icon ->
                DrawableCompat.setTintList(DrawableCompat.wrap(icon), tint)
                pref.icon = icon
            }
        }
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

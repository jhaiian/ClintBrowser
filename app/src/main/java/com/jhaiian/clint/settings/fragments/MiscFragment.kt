package com.jhaiian.clint.settings.fragments

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.setup.SetupActivity

class MiscFragment : Fragment() {

    private lateinit var rowDefaultBrowser: LinearLayout
    private lateinit var textDefaultBrowserSummary: TextView
    private lateinit var rowRerunSetup: LinearLayout

    private val browserRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateDefaultBrowserSummary()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_misc, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rowDefaultBrowser = view.findViewById(R.id.row_default_browser)
        textDefaultBrowserSummary = view.findViewById(R.id.text_default_browser_summary)
        rowRerunSetup = view.findViewById(R.id.row_rerun_setup)

        updateDefaultBrowserSummary()

        rowDefaultBrowser.setOnClickListener {
            openDefaultBrowserPicker()
        }

        rowRerunSetup.setOnClickListener {
            showRerunSetupDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        updateDefaultBrowserSummary()
    }

    private fun updateDefaultBrowserSummary() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
        val resolveInfo = requireContext().packageManager.resolveActivity(
            intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        textDefaultBrowserSummary.text = resolveInfo?.loadLabel(requireContext().packageManager)?.toString()
            ?: getString(R.string.default_browser_none)
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

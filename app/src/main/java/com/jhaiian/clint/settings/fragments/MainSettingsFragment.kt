package com.jhaiian.clint.settings.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.jhaiian.clint.BuildConfig
import com.jhaiian.clint.R
import com.jhaiian.clint.crash.CrashReportFragment
import com.jhaiian.clint.quiver.QuiverGuardActivity
import com.jhaiian.clint.settings.SettingsActivity
import com.jhaiian.clint.ui.DocumentViewer

class MainSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_main_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        view.findViewById<TextView>(R.id.text_about_version).text =
            getString(R.string.about_summary, pInfo.versionName)

        view.findViewById<LinearLayout>(R.id.row_look_and_feel).setOnClickListener {
            navigate(LookAndFeelFragment(), getString(R.string.look_and_feel))
        }
        view.findViewById<LinearLayout>(R.id.row_browser).setOnClickListener {
            navigate(BrowserSettingsFragment(), getString(R.string.browser_settings))
        }
        view.findViewById<LinearLayout>(R.id.row_privacy).setOnClickListener {
            navigate(PrivacySettingsFragment(), getString(R.string.privacy_settings))
        }
        view.findViewById<LinearLayout>(R.id.row_quiver_guard).setOnClickListener {
            startActivity(Intent(requireContext(), QuiverGuardActivity::class.java))
        }
        view.findViewById<LinearLayout>(R.id.row_site_settings).setOnClickListener {
            navigate(SiteSettingsFragment(), getString(R.string.site_settings))
        }
        view.findViewById<LinearLayout>(R.id.row_data_saver).setOnClickListener {
            navigate(DataSaverFragment(), getString(R.string.data_saver_title))
        }
        view.findViewById<LinearLayout>(R.id.row_downloads).setOnClickListener {
            navigate(DownloadSettingsFragment(), getString(R.string.download_settings_title))
        }
        if (BuildConfig.IS_FDROID) {
            view.findViewById<ImageView>(R.id.icon_row_updates)
                .setImageResource(R.drawable.ic_history_24)
            view.findViewById<TextView>(R.id.text_row_updates_title)
                .setText(R.string.view_changelog_title)
            view.findViewById<TextView>(R.id.text_row_updates_summary)
                .setText(R.string.view_changelog_summary)
            view.findViewById<LinearLayout>(R.id.row_updates).setOnClickListener {
                DocumentViewer.show(
                    requireContext(),
                    getString(R.string.document_viewer_changelog_title),
                    DocumentViewer.CHANGELOG_URL
                )
            }
        } else {
            view.findViewById<LinearLayout>(R.id.row_updates).setOnClickListener {
                navigate(UpdateSettingsFragment(), getString(R.string.pref_updates_title))
            }
        }
        view.findViewById<LinearLayout>(R.id.row_misc).setOnClickListener {
            navigate(MiscFragment(), getString(R.string.pref_misc_title))
        }
        view.findViewById<LinearLayout>(R.id.row_debug).setOnClickListener {
            navigate(CrashReportFragment(), getString(R.string.debug_title))
        }
        view.findViewById<LinearLayout>(R.id.row_about).setOnClickListener {
            navigate(AboutFragment(), getString(R.string.about))
        }
    }

    private fun navigate(fragment: Fragment, title: String) {
        (activity as SettingsActivity).navigateTo(fragment, title)
    }
}

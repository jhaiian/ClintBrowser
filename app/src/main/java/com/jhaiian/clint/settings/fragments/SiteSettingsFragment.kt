package com.jhaiian.clint.settings.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.desktopmode.DesktopModeActivity
import com.jhaiian.clint.settings.sitepermissions.SitePermissionActivity
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase

class SiteSettingsFragment : Fragment() {

    private lateinit var rowCamera: LinearLayout
    private lateinit var textCameraSummary: TextView
    private lateinit var rowMic: LinearLayout
    private lateinit var textMicSummary: TextView
    private lateinit var rowLocation: LinearLayout
    private lateinit var textLocationSummary: TextView
    private lateinit var rowNotifications: LinearLayout
    private lateinit var textNotificationsSummary: TextView
    private lateinit var rowDesktopMode: LinearLayout
    private lateinit var textDesktopModeSummary: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_site_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rowCamera = view.findViewById(R.id.row_site_camera)
        textCameraSummary = view.findViewById(R.id.text_camera_summary)
        rowMic = view.findViewById(R.id.row_site_mic)
        textMicSummary = view.findViewById(R.id.text_mic_summary)
        rowLocation = view.findViewById(R.id.row_site_location)
        textLocationSummary = view.findViewById(R.id.text_location_summary)
        rowNotifications = view.findViewById(R.id.row_site_notifications)
        textNotificationsSummary = view.findViewById(R.id.text_notifications_summary)
        rowDesktopMode = view.findViewById(R.id.row_site_desktop_mode)
        textDesktopModeSummary = view.findViewById(R.id.text_desktop_mode_summary)

        rowCamera.setOnClickListener {
            startActivity(
                Intent(requireContext(), SitePermissionActivity::class.java)
                    .putExtra(SitePermissionActivity.EXTRA_TYPE, SitePermissionDatabase.TYPE_CAMERA)
            )
        }

        rowMic.setOnClickListener {
            startActivity(
                Intent(requireContext(), SitePermissionActivity::class.java)
                    .putExtra(SitePermissionActivity.EXTRA_TYPE, SitePermissionDatabase.TYPE_MIC)
            )
        }

        rowLocation.setOnClickListener {
            startActivity(
                Intent(requireContext(), SitePermissionActivity::class.java)
                    .putExtra(SitePermissionActivity.EXTRA_TYPE, SitePermissionDatabase.TYPE_LOCATION)
            )
        }

        rowNotifications.setOnClickListener {
            startActivity(
                Intent(requireContext(), SitePermissionActivity::class.java)
                    .putExtra(SitePermissionActivity.EXTRA_TYPE, SitePermissionDatabase.TYPE_NOTIFICATION)
            )
        }

        rowDesktopMode.setOnClickListener {
            startActivity(Intent(requireContext(), DesktopModeActivity::class.java))
        }

        updateSummaries()
    }

    override fun onResume() {
        super.onResume()
        updateSummaries()
    }

    private fun updateSummaries() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val permTypeToTextView = mapOf(
            SitePermissionDatabase.TYPE_CAMERA to textCameraSummary,
            SitePermissionDatabase.TYPE_MIC to textMicSummary,
            SitePermissionDatabase.TYPE_LOCATION to textLocationSummary,
            SitePermissionDatabase.TYPE_NOTIFICATION to textNotificationsSummary
        )

        permTypeToTextView.forEach { (type, textView) ->
            val behavior = prefs.getString("site_perm_default_$type", SitePermissionActivity.PREF_VALUE_ASK)
            textView.text = when (behavior) {
                SitePermissionActivity.PREF_VALUE_DENY -> getString(R.string.site_permission_always_deny)
                SitePermissionActivity.PREF_VALUE_ALLOW -> getString(R.string.site_permission_always_allow)
                else -> getString(R.string.site_permission_ask_first)
            }
        }

        val saveState = prefs.getString(
            DesktopModeActivity.PREF_DESKTOP_MODE_SAVE_STATE,
            DesktopModeActivity.VALUE_SAVE_STATE
        )
        textDesktopModeSummary.text = when (saveState) {
            DesktopModeActivity.VALUE_DO_NOT_SAVE -> getString(R.string.desktop_mode_do_not_save_state)
            else -> getString(R.string.desktop_mode_save_state)
        }
    }
}

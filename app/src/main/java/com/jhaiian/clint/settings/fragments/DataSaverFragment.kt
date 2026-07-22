package com.jhaiian.clint.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R

class DataSaverFragment : Fragment() {

    companion object {
        const val PREF_DATA_SAVER_ENABLED = "data_saver_enabled"
        const val PREF_DISABLE_IMAGES = "data_saver_disable_images"
        const val PREF_DISABLE_AUTOPLAY = "data_saver_disable_autoplay"
        const val DEFAULT_DATA_SAVER_ENABLED = false
        const val DEFAULT_DISABLE_IMAGES = true
        const val DEFAULT_DISABLE_AUTOPLAY = true
    }

    private lateinit var rowEnabled: LinearLayout
    private lateinit var switchEnabled: Switch
    private lateinit var rowDisableImages: LinearLayout
    private lateinit var switchDisableImages: Switch
    private lateinit var rowDisableAutoplay: LinearLayout
    private lateinit var switchDisableAutoplay: Switch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_data_saver, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rowEnabled = view.findViewById(R.id.row_data_saver_enabled)
        switchEnabled = view.findViewById(R.id.switch_data_saver_enabled)
        rowDisableImages = view.findViewById(R.id.row_data_saver_disable_images)
        switchDisableImages = view.findViewById(R.id.switch_data_saver_disable_images)
        rowDisableAutoplay = view.findViewById(R.id.row_data_saver_disable_autoplay)
        switchDisableAutoplay = view.findViewById(R.id.switch_data_saver_disable_autoplay)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        switchEnabled.isChecked = prefs.getBoolean(PREF_DATA_SAVER_ENABLED, DEFAULT_DATA_SAVER_ENABLED)
        switchDisableImages.isChecked = prefs.getBoolean(PREF_DISABLE_IMAGES, DEFAULT_DISABLE_IMAGES)
        switchDisableAutoplay.isChecked = prefs.getBoolean(PREF_DISABLE_AUTOPLAY, DEFAULT_DISABLE_AUTOPLAY)

        syncDependentsState(switchEnabled.isChecked)

        rowEnabled.setOnClickListener {
            val newVal = !switchEnabled.isChecked
            prefs.edit().putBoolean(PREF_DATA_SAVER_ENABLED, newVal).apply()
            switchEnabled.isChecked = newVal
            syncDependentsState(newVal)
        }

        rowDisableImages.setOnClickListener {
            val newVal = !switchDisableImages.isChecked
            prefs.edit().putBoolean(PREF_DISABLE_IMAGES, newVal).apply()
            switchDisableImages.isChecked = newVal
        }

        rowDisableAutoplay.setOnClickListener {
            val newVal = !switchDisableAutoplay.isChecked
            prefs.edit().putBoolean(PREF_DISABLE_AUTOPLAY, newVal).apply()
            switchDisableAutoplay.isChecked = newVal
        }
    }

    private fun syncDependentsState(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.4f
        listOf(rowDisableImages, rowDisableAutoplay).forEach { row ->
            row.isEnabled = enabled
            row.isClickable = enabled
            row.alpha = alpha
        }
    }
}

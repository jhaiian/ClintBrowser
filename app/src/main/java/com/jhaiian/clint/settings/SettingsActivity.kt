package com.jhaiian.clint.settings
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import com.jhaiian.clint.settings.fragments.MainSettingsFragment
import com.jhaiian.clint.settings.fragments.DataSaverFragment

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.databinding.ActivitySettingsBinding

class SettingsActivity : ClintActivity() {

    var pendingRestart = false
    var pendingHideStatusBar: Boolean? = null
    private var hideStatusBarAtLaunch = false
    private var addressBarPositionAtLaunch = ""

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var toolbarTitle: TextView
    private lateinit var btnBack: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBarAtLaunch = androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .getBoolean("hide_status_bar", false)
        addressBarPositionAtLaunch = androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .getString("address_bar_position", "top") ?: "top"
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbarTitle = binding.toolbarTitle
        btnBack = binding.btnBack

        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsToolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBars.top, 0, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsContainer) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, navBars.bottom)
            insets
        }

        toolbarTitle.text = savedInstanceState?.getCharSequence(KEY_TOOLBAR_TITLE)
            ?: getString(R.string.settings)

        btnBack.setOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        if (savedInstanceState == null) {
            val openFragment = intent.getStringExtra(EXTRA_OPEN_FRAGMENT)
            if (openFragment == "data_saver") {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, DataSaverFragment())
                    .commit()
                toolbarTitle.text = getString(R.string.data_saver_title)
            } else if (openFragment == "download_settings") {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, DownloadSettingsFragment())
                    .commit()
                toolbarTitle.text = getString(R.string.download_settings_title)
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, MainSettingsFragment())
                    .commit()
            }
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                toolbarTitle.text = getString(R.string.settings)
            }
        }
    }

    fun navigateTo(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        toolbarTitle.text = title
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(KEY_TOOLBAR_TITLE, toolbarTitle.text)
    }

    override fun onStop() {
        super.onStop()
        if (pendingRestart) {
            pendingRestart = false
            pendingHideStatusBar?.let { pending ->
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putBoolean("hide_status_bar", pending).apply()
                pendingHideStatusBar = null
            }
            restartApp()
        }
    }

    fun scheduleRestartIfChanged() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val effectiveStatusBar = pendingHideStatusBar ?: prefs.getBoolean("hide_status_bar", false)
        val statusBarChanged = effectiveStatusBar != hideStatusBarAtLaunch
        val positionChanged = (prefs.getString("address_bar_position", "top") ?: "top") != addressBarPositionAtLaunch
        pendingRestart = statusBarChanged || positionChanged
    }

    fun restartApp() {
        pendingHideStatusBar?.let { pending ->
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putBoolean("hide_status_bar", pending).apply()
            pendingHideStatusBar = null
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)!!
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    companion object {
        private const val KEY_TOOLBAR_TITLE = "toolbar_title"
        const val EXTRA_OPEN_FRAGMENT = "extra_open_fragment"
    }
}

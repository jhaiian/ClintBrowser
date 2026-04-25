package com.jhaiian.clint.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.databinding.ActivitySettingsBinding

class SettingsActivity : ClintActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    var pendingRestart = false
    var pendingHideStatusBar: Boolean? = null
    private var hideStatusBarAtLaunch = false
    private var addressBarPositionAtLaunch = ""

    private lateinit var binding: ActivitySettingsBinding

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
        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = savedInstanceState?.getCharSequence(KEY_TOOLBAR_TITLE)
                ?: getString(R.string.settings)
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, MainSettingsFragment())
                .commit()
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                supportActionBar?.title = getString(R.string.settings)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(KEY_TOOLBAR_TITLE, supportActionBar?.title)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val fragment = supportFragmentManager.fragmentFactory
            .instantiate(classLoader, pref.fragment!!)
        fragment.arguments = pref.extras
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        supportActionBar?.title = pref.title
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        onBackPressedDispatcher.onBackPressed()
        return true
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
    }
}

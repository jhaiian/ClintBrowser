package com.jhaiian.clint.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import java.lang.ref.WeakReference

class ClintApplication : Application() {

    private var _currentActivity: WeakReference<Activity>? = null
    val currentActivity: Activity? get() = _currentActivity?.get()

    override fun onCreate() {
        super.onCreate()
        applyNightMode()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                _currentActivity = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (_currentActivity?.get() === activity) _currentActivity = null
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (_currentActivity?.get() === activity) _currentActivity = null
            }
        })
    }

    fun applyNightMode() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "default") ?: "default"
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}

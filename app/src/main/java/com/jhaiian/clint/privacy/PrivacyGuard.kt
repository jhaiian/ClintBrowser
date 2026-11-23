package com.jhaiian.clint.privacy

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.preference.PreferenceManager
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import com.jhaiian.clint.history.SearchHistoryManager
import com.jhaiian.clint.profiles.WebProfiles

object PrivacyGuard {

    private val main = Handler(Looper.getMainLooper())
    private var application: Application? = null
    private var startedCount = 0
    private var aliveCount = 0
    private var launches = 0
    private var inBackground = true
    private var backgroundAt = 0L
    private var locked = false
    private var lockVisible = false
    private var clearRunning = false
    private var clipboardTask: Runnable? = null
    private var clipboardStamp = 0L
    private var ignoreClipUntil = 0L

    fun install(app: Application) {
        if (application != null) return
        application = app
        locked = prefs(app).getBoolean(PrivacyPrefs.APP_LOCK, PrivacyPrefs.DEFAULT_APP_LOCK)
        app.registerActivityLifecycleCallbacks(callbacks)
        if (prefs(app).getBoolean(PrivacyPrefs.EXIT_CLEAR_PENDING, false)) runExitClear(app)
        clipboard(app).addPrimaryClipChangedListener { onClipChanged() }
    }

    fun unlock() {
        locked = false
    }

    fun refreshScreenGuard(activity: Activity) {
        applyScreenGuard(activity)
    }

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    private fun clipboard(context: Context) = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            aliveCount++
            if (activity is AppLockActivity) lockVisible = true
            applyScreenGuard(activity)
        }

        override fun onActivityStarted(activity: Activity) {
            startedCount++
            if (startedCount == 1) onForeground(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            applyScreenGuard(activity)
        }

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            startedCount--
            if (startedCount <= 0) {
                startedCount = 0
                if (!activity.isChangingConfigurations) onBackground(activity)
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            aliveCount--
            if (activity is AppLockActivity) lockVisible = false
            if (aliveCount <= 0) {
                aliveCount = 0
                if (activity.isFinishing) onExit(activity)
            }
        }
    }

    private fun onForeground(activity: Activity) {
        if (!inBackground) return
        inBackground = false
        launches++
        val prefs = prefs(activity)
        val lockEnabled = prefs.getBoolean(PrivacyPrefs.APP_LOCK, PrivacyPrefs.DEFAULT_APP_LOCK)
        if (launches == 1) {
            locked = lockEnabled
        } else {
            if (!clearRunning) prefs.edit().putBoolean(PrivacyPrefs.EXIT_CLEAR_PENDING, false).apply()
            if (lockEnabled && !locked) {
                val timeout = prefs.getInt(PrivacyPrefs.APP_LOCK_TIMEOUT, PrivacyPrefs.DEFAULT_APP_LOCK_TIMEOUT)
                if (SystemClock.elapsedRealtime() - backgroundAt >= timeout * 1000L) locked = true
            }
        }
        if (!lockEnabled) locked = false
        if (locked) showLock(activity)
    }

    private fun onBackground(activity: Activity) {
        inBackground = true
        backgroundAt = SystemClock.elapsedRealtime()
        val prefs = prefs(activity)
        if (prefs.getBoolean(PrivacyPrefs.CLEAR_ON_EXIT, PrivacyPrefs.DEFAULT_CLEAR_ON_EXIT)) {
            prefs.edit().putBoolean(PrivacyPrefs.EXIT_CLEAR_PENDING, true).apply()
        }
    }

    private fun onExit(activity: Activity) {
        val prefs = prefs(activity)
        if (!prefs.getBoolean(PrivacyPrefs.CLEAR_ON_EXIT, PrivacyPrefs.DEFAULT_CLEAR_ON_EXIT)) return
        prefs.edit().putBoolean(PrivacyPrefs.EXIT_CLEAR_PENDING, true).commit()
        runExitClear(activity.applicationContext)
    }

    private fun showLock(activity: Activity) {
        if (lockVisible || activity is AppLockActivity) return
        lockVisible = true
        activity.startActivity(Intent(activity, AppLockActivity::class.java))
    }

    private fun applyScreenGuard(activity: Activity) {
        val block = prefs(activity).getBoolean(PrivacyPrefs.SCREENSHOT_BLOCKING, PrivacyPrefs.DEFAULT_SCREENSHOT_BLOCKING)
        if (block) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun runExitClear(context: Context) {
        val prefs = prefs(context)
        if (clearRunning) return
        if (!prefs.getBoolean(PrivacyPrefs.CLEAR_ON_EXIT, PrivacyPrefs.DEFAULT_CLEAR_ON_EXIT)) return
        if (!prefs.getBoolean(PrivacyPrefs.EXIT_CLEAR_PENDING, false)) return
        val history = prefs.getBoolean(PrivacyPrefs.CLEAR_ON_EXIT_HISTORY, PrivacyPrefs.DEFAULT_CLEAR_ON_EXIT_HISTORY)
        val cookies = prefs.getBoolean(PrivacyPrefs.CLEAR_ON_EXIT_COOKIES, PrivacyPrefs.DEFAULT_CLEAR_ON_EXIT_COOKIES)
        val cache = prefs.getBoolean(PrivacyPrefs.CLEAR_ON_EXIT_CACHE, PrivacyPrefs.DEFAULT_CLEAR_ON_EXIT_CACHE)
        clearRunning = true
        if (cookies) clearCookiesAndSiteData()
        if (cache) clearCache(context)
        Thread {
            if (history) runCatching { SearchHistoryManager.clearAllProfiles(context) }
            main.post {
                prefs.edit().putBoolean(PrivacyPrefs.EXIT_CLEAR_PENDING, false).apply()
                clearRunning = false
            }
        }.start()
    }

    @SuppressLint("RequiresFeature")
    internal fun clearCookiesAndSiteData() {
        runCatching {
            val cookies = CookieManager.getInstance()
            cookies.removeAllCookies(null)
            cookies.flush()
            WebStorage.getInstance().deleteAllData()
        }
        if (!WebProfiles.isSupported()) return
        runCatching {
            ProfileStore.getInstance().allProfileNames
                .filter { !it.equals(Profile.DEFAULT_PROFILE_NAME, ignoreCase = true) && !it.equals(WebProfiles.DEFAULT_ID, ignoreCase = true) }
                .forEach { WebProfiles.clearData(it) }
        }
    }

    internal fun clearCache(context: Context) {
        runCatching {
            val webView = WebView(context)
            webView.clearCache(true)
            webView.destroy()
        }
    }

    private fun onClipChanged() {
        val app = application ?: return
        clipboardTask?.let { main.removeCallbacks(it) }
        clipboardTask = null
        val seconds = prefs(app).getInt(PrivacyPrefs.CLIPBOARD_CLEAR_SECONDS, PrivacyPrefs.DEFAULT_CLIPBOARD_CLEAR_SECONDS)
        if (seconds <= 0 || startedCount <= 0) return
        if (SystemClock.elapsedRealtime() < ignoreClipUntil) return
        val stamp = runCatching { clipboard(app).primaryClipDescription?.timestamp }.getOrNull() ?: return
        clipboardStamp = stamp
        val task = Runnable { clearClipboardIfUnchanged(app) }
        clipboardTask = task
        main.postDelayed(task, seconds * 1000L)
    }

    private fun clearClipboardIfUnchanged(app: Application) {
        clipboardTask = null
        runCatching {
            val manager = clipboard(app)
            if (manager.primaryClipDescription?.timestamp == clipboardStamp) {
                ignoreClipUntil = SystemClock.elapsedRealtime() + 2000L
                if (Build.VERSION.SDK_INT >= 28) manager.clearPrimaryClip()
                else manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}

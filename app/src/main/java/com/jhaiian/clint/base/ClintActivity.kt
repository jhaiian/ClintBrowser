package com.jhaiian.clint.base

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.ThemeRevealHolder
import com.jhaiian.clint.ui.ThemeRevealOverlay
import kotlin.math.hypot
import kotlin.math.max

abstract class ClintActivity : AppCompatActivity() {

    private var appliedTheme: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        appliedTheme = prefs.getString("app_theme", "default") ?: "default"
        applyAppTheme()
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentTheme = prefs.getString("app_theme", "default") ?: "default"
        if (currentTheme != appliedTheme) {
            window.setWindowAnimations(0)
            recreate()
            return
        }
        applyStatusBarVisibility()
        applySystemBarAppearance()
        window.decorView.post { startRevealIfNeeded() }
    }

    private fun applyAppTheme() {
        when (appliedTheme) {
            "dark" -> setTheme(R.style.Theme_ClintBrowser_Dark)
            "light" -> setTheme(R.style.Theme_ClintBrowser_Light)
            else -> setTheme(R.style.Theme_ClintBrowser)
        }
    }

    private fun applySystemBarAppearance() {
        val isLight = appliedTheme == "light"
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
    }

    fun getDialogTheme(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return when (prefs.getString("app_theme", "default") ?: "default") {
            "dark" -> R.style.ThemeOverlay_ClintBrowser_Dialog_Dark
            "light" -> R.style.ThemeOverlay_ClintBrowser_Dialog_Light
            else -> R.style.ThemeOverlay_ClintBrowser_Dialog
        }
    }

    fun captureAndRecreate(newTheme: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("app_theme", "default") ?: "default"
        if (current == newTheme) return

        val decor = window.decorView
        try {
            val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bmp))
            ThemeRevealHolder.bitmap = bmp
            ThemeRevealHolder.cx = decor.width / 2
            ThemeRevealHolder.cy = decor.height / 2
        } catch (_: Exception) {
        }

        prefs.edit().putString("app_theme", newTheme).commit()
        window.setWindowAnimations(0)
        recreate()
    }

    private fun startRevealIfNeeded() {
        val (bmp, cx, cy) = ThemeRevealHolder.consume() ?: return
        if (isFinishing || isDestroyed) {
            bmp.recycle()
            return
        }

        val decor = window.decorView as? android.view.ViewGroup ?: run {
            bmp.recycle()
            return
        }

        val maxRadius = hypot(
            max(cx, decor.width - cx).toDouble(),
            max(cy, decor.height - cy).toDouble()
        ).toFloat()

        val overlay = ThemeRevealOverlay(this, bmp, cx, cy).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        decor.addView(overlay)

        ValueAnimator.ofFloat(0f, maxRadius).apply {
            duration = 450
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                overlay.revealRadius = it.animatedValue as Float
                overlay.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    decor.removeView(overlay)
                    bmp.recycle()
                }
            })
            start()
        }
    }

    private fun applyStatusBarVisibility() {
        val hide = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("hide_status_bar", false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

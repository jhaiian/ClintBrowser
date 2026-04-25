package com.jhaiian.clint.base

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.TypedValue
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.ThemeRevealHolder
import com.jhaiian.clint.ui.ThemeRevealOverlay
import kotlin.math.hypot
import kotlin.math.max

abstract class ClintActivity : AppCompatActivity() {

    private var appliedTheme: String? = null
    private var appliedAccent: String? = null
    private var appliedIntensity: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        appliedTheme = prefs.getString("app_theme", "default") ?: "default"
        appliedAccent = prefs.getString("accent_color", "default") ?: "default"
        appliedIntensity = prefs.getString("surface_intensity", "soft_tint") ?: "soft_tint"
        applyThemeResource()
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentTheme = prefs.getString("app_theme", "default") ?: "default"
        val currentAccent = prefs.getString("accent_color", "default") ?: "default"
        val currentIntensity = prefs.getString("surface_intensity", "soft_tint") ?: "soft_tint"
        if (currentTheme != appliedTheme || currentAccent != appliedAccent || currentIntensity != appliedIntensity) {
            window.setWindowAnimations(0)
            recreate()
            return
        }
        applyStatusBarVisibility()
        applySystemBarAppearance()
        window.decorView.post { startRevealIfNeeded() }
    }

    private fun isMaterialYouActive(): Boolean {
        return appliedAccent == "material_you" &&
            (appliedTheme == "dark" || appliedTheme == "light")
    }

    private fun isDefaultMaterialYou(): Boolean {
        return appliedAccent == "material_you" && appliedTheme == "default"
    }

    private fun isPurpleActive(): Boolean {
        return appliedAccent == "purple"
    }

    private fun isBlueActive(): Boolean {
        return appliedAccent == "blue"
    }

    private fun isYellowActive(): Boolean {
        return appliedAccent == "yellow"
    }

    private fun isRedActive(): Boolean {
        return appliedAccent == "red"
    }

    private fun isGreenActive(): Boolean {
        return appliedAccent == "green"
    }

    private fun isOrangeActive(): Boolean {
        return appliedAccent == "orange"
    }

    private fun isSurfaceIntensityActive(): Boolean {
        if (appliedTheme == "default") return false
        return appliedAccent == "material_you" || appliedAccent == "purple" || appliedAccent == "blue" || appliedAccent == "yellow" || appliedAccent == "red" || appliedAccent == "green" || appliedAccent == "orange" || appliedAccent == "default"
    }

    private fun applyThemeResource() {
        when {
            appliedTheme == "dark" && isPurpleActive() -> setTheme(R.style.Theme_ClintBrowser_Dark_Purple)
            appliedTheme == "dark" && isBlueActive() -> setTheme(R.style.Theme_ClintBrowser_Dark_Blue)
            appliedTheme == "dark" && isYellowActive() -> setTheme(R.style.Theme_ClintBrowser_Dark_Yellow)
            appliedTheme == "dark" && isRedActive() -> setTheme(R.style.Theme_ClintBrowser_Dark_Red)
            appliedTheme == "dark" && isGreenActive() -> setTheme(R.style.Theme_ClintBrowser_Dark_Green)
            appliedTheme == "dark" && isOrangeActive() -> setTheme(R.style.Theme_ClintBrowser_Dark_Orange)
            appliedTheme == "dark" && isMaterialYouActive() -> setTheme(R.style.Theme_ClintBrowser_Dark_MaterialYou)
            appliedTheme == "dark" -> setTheme(R.style.Theme_ClintBrowser_Dark)
            appliedTheme == "light" && isPurpleActive() -> setTheme(R.style.Theme_ClintBrowser_Light_Purple)
            appliedTheme == "light" && isBlueActive() -> setTheme(R.style.Theme_ClintBrowser_Light_Blue)
            appliedTheme == "light" && isYellowActive() -> setTheme(R.style.Theme_ClintBrowser_Light_Yellow)
            appliedTheme == "light" && isRedActive() -> setTheme(R.style.Theme_ClintBrowser_Light_Red)
            appliedTheme == "light" && isGreenActive() -> setTheme(R.style.Theme_ClintBrowser_Light_Green)
            appliedTheme == "light" && isOrangeActive() -> setTheme(R.style.Theme_ClintBrowser_Light_Orange)
            appliedTheme == "light" && isMaterialYouActive() -> setTheme(R.style.Theme_ClintBrowser_Light_MaterialYou)
            appliedTheme == "light" -> setTheme(R.style.Theme_ClintBrowser_Light)
            isPurpleActive() -> setTheme(R.style.Theme_ClintBrowser_Purple)
            isBlueActive() -> setTheme(R.style.Theme_ClintBrowser_Blue)
            isYellowActive() -> setTheme(R.style.Theme_ClintBrowser_Yellow)
            isRedActive() -> setTheme(R.style.Theme_ClintBrowser_Red)
            isGreenActive() -> setTheme(R.style.Theme_ClintBrowser_Green)
            isOrangeActive() -> setTheme(R.style.Theme_ClintBrowser_Orange)
            isDefaultMaterialYou() -> {
                setTheme(R.style.Theme_ClintBrowser_MaterialYou)
                if (DynamicColors.isDynamicColorAvailable()) {
                    DynamicColors.applyToActivityIfAvailable(this)
                    theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_PreserveDefaultBackground, true)
                }
                return
            }
            else -> setTheme(R.style.Theme_ClintBrowser)
        }
        if (isMaterialYouActive()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        applyIntensityOverlay()
    }

    private fun applyIntensityOverlay() {
        if (!isSurfaceIntensityActive()) return
        val intensity = appliedIntensity ?: "soft_tint"
        val isLight = appliedTheme == "light"
        val isPurple = isPurpleActive()
        val isBlue = isBlueActive()
        val isYellow = isYellowActive()
        val isRed = isRedActive()
        val isGreen = isGreenActive()
        val isOrange = isOrangeActive()
        val isMaterialYou = isMaterialYouActive()
        when (intensity) {
            "soft_tint" -> {
                if (isPurple && !isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Purple_Dark, true)
                else if (isPurple && isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Purple_Light, true)
                else if (isBlue && !isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Blue_Dark, true)
                else if (isBlue && isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Blue_Light, true)
                else if (isYellow && !isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Yellow_Dark, true)
                else if (isYellow && isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Yellow_Light, true)
                else if (isRed && !isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Red_Dark, true)
                else if (isRed && isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Red_Light, true)
                else if (isGreen && !isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Green_Dark, true)
                else if (isGreen && isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Green_Light, true)
                else if (isOrange && !isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Orange_Dark, true)
                else if (isOrange && isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_Orange_Light, true)
                else if (isMaterialYou && !isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_MaterialYou_Dark, true)
                else if (isMaterialYou && isLight) theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_SoftTint_MaterialYou_Light, true)
            }
            "pure_mode" -> {
                when {
                    isMaterialYou && !isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_MaterialYou_Dark, true)
                    isMaterialYou && isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_MaterialYou_Light, true)
                    isPurple && !isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Purple_Dark, true)
                    isPurple && isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Purple_Light, true)
                    isBlue && !isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Blue_Dark, true)
                    isBlue && isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Blue_Light, true)
                    isYellow && !isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Yellow_Dark, true)
                    isYellow && isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Yellow_Light, true)
                    isRed && !isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Red_Dark, true)
                    isRed && isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Red_Light, true)
                    isGreen && !isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Green_Dark, true)
                    isGreen && isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Green_Light, true)
                    isOrange && !isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Orange_Dark, true)
                    isOrange && isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Orange_Light, true)
                    !isLight -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Dark, true)
                    else -> theme.applyStyle(R.style.ThemeOverlay_ClintBrowser_SurfaceIntensity_PureMode_Light, true)
                }
            }
        }
    }

    private fun applySystemBarAppearance() {
        val isLight = appliedTheme == "light"
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
        if (isMaterialYouActive()) {
            val tv = TypedValue()
            val colorAttr = if (appliedIntensity == "soft_tint") {
                com.google.android.material.R.attr.colorSurfaceContainer
            } else {
                com.google.android.material.R.attr.colorSurface
            }
            if (theme.resolveAttribute(colorAttr, tv, true)) {
                window.statusBarColor = tv.data
                window.navigationBarColor = tv.data
            }
        }
    }

    fun getDialogTheme(): Int {
        val theme = appliedTheme ?: "default"
        val accent = appliedAccent ?: "default"
        val intensity = appliedIntensity ?: "soft_tint"
        val useMaterialYou = accent == "material_you" && (theme == "dark" || theme == "light")
        val isDefaultMY = accent == "material_you" && theme == "default"
        val usePurple = accent == "purple"
        val useBlue = accent == "blue"
        val useYellow = accent == "yellow"
        val useRed = accent == "red"
        val useGreen = accent == "green"
        val useOrange = accent == "orange"
        val isLight = theme == "light"
        val isDark = theme == "dark"

        if (intensity == "pure_mode" && (isDark || isLight)) {
            return when {
                useMaterialYou && isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_MaterialYou_Dark
                useMaterialYou && isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_MaterialYou_Light
                usePurple && isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Purple_Dark
                usePurple && isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Purple_Light
                useBlue && isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Blue_Dark
                useBlue && isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Blue_Light
                useYellow && isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Yellow_Dark
                useYellow && isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Yellow_Light
                useRed && isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Red_Dark
                useRed && isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Red_Light
                useGreen && isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Green_Dark
                useGreen && isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Green_Light
                useOrange && isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Orange_Dark
                useOrange && isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Orange_Light
                isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Dark
                else -> R.style.ThemeOverlay_ClintBrowser_Dialog_PureMode_Light
            }
        }

        if (intensity == "soft_tint" && usePurple) {
            return when {
                isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Purple_Dark
                isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Purple_Light
                else -> R.style.ThemeOverlay_ClintBrowser_Dialog_Purple
            }
        }

        if (intensity == "soft_tint" && useBlue) {
            return when {
                isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Blue_Dark
                isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Blue_Light
                else -> R.style.ThemeOverlay_ClintBrowser_Dialog_Blue
            }
        }

        if (intensity == "soft_tint" && useYellow) {
            return when {
                isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Yellow_Dark
                isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Yellow_Light
                else -> R.style.ThemeOverlay_ClintBrowser_Dialog_Yellow
            }
        }

        if (intensity == "soft_tint" && useRed) {
            return when {
                isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Red_Dark
                isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Red_Light
                else -> R.style.ThemeOverlay_ClintBrowser_Dialog_Red
            }
        }

        if (intensity == "soft_tint" && useGreen) {
            return when {
                isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Green_Dark
                isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Green_Light
                else -> R.style.ThemeOverlay_ClintBrowser_Dialog_Green
            }
        }

        if (intensity == "soft_tint" && useOrange) {
            return when {
                isDark -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Orange_Dark
                isLight -> R.style.ThemeOverlay_ClintBrowser_Dialog_SoftTint_Orange_Light
                else -> R.style.ThemeOverlay_ClintBrowser_Dialog_Orange
            }
        }

        return when {
            theme == "dark" && usePurple -> R.style.ThemeOverlay_ClintBrowser_Dialog_Dark_Purple
            theme == "dark" && useBlue -> R.style.ThemeOverlay_ClintBrowser_Dialog_Dark_Blue
            theme == "dark" && useYellow -> R.style.ThemeOverlay_ClintBrowser_Dialog_Dark_Yellow
            theme == "dark" && useRed -> R.style.ThemeOverlay_ClintBrowser_Dialog_Dark_Red
            theme == "dark" && useGreen -> R.style.ThemeOverlay_ClintBrowser_Dialog_Dark_Green
            theme == "dark" && useOrange -> R.style.ThemeOverlay_ClintBrowser_Dialog_Dark_Orange
            theme == "dark" && useMaterialYou -> R.style.ThemeOverlay_ClintBrowser_Dialog_Dark_MaterialYou
            theme == "dark" -> R.style.ThemeOverlay_ClintBrowser_Dialog_Dark
            theme == "light" && usePurple -> R.style.ThemeOverlay_ClintBrowser_Dialog_Light_Purple
            theme == "light" && useBlue -> R.style.ThemeOverlay_ClintBrowser_Dialog_Light_Blue
            theme == "light" && useYellow -> R.style.ThemeOverlay_ClintBrowser_Dialog_Light_Yellow
            theme == "light" && useRed -> R.style.ThemeOverlay_ClintBrowser_Dialog_Light_Red
            theme == "light" && useGreen -> R.style.ThemeOverlay_ClintBrowser_Dialog_Light_Green
            theme == "light" && useOrange -> R.style.ThemeOverlay_ClintBrowser_Dialog_Light_Orange
            theme == "light" && useMaterialYou -> R.style.ThemeOverlay_ClintBrowser_Dialog_Light_MaterialYou
            theme == "light" -> R.style.ThemeOverlay_ClintBrowser_Dialog_Light
            usePurple -> R.style.ThemeOverlay_ClintBrowser_Dialog_Purple
            useBlue -> R.style.ThemeOverlay_ClintBrowser_Dialog_Blue
            useYellow -> R.style.ThemeOverlay_ClintBrowser_Dialog_Yellow
            useRed -> R.style.ThemeOverlay_ClintBrowser_Dialog_Red
            useGreen -> R.style.ThemeOverlay_ClintBrowser_Dialog_Green
            useOrange -> R.style.ThemeOverlay_ClintBrowser_Dialog_Orange
            isDefaultMY -> R.style.ThemeOverlay_ClintBrowser_Dialog_Default_MaterialYou
            else -> R.style.ThemeOverlay_ClintBrowser_Dialog
        }
    }

    fun captureAndRecreate(newTheme: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("app_theme", "default") ?: "default"
        if (current == newTheme) return
        if (newTheme == "default") {
            prefs.edit().putString("surface_intensity", "soft_tint").apply()
        }
        captureScreenBitmap()
        prefs.edit().putString("app_theme", newTheme).commit()
        window.setWindowAnimations(0)
        recreate()
    }

    fun captureAndApplyAccentColor(newAccent: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("accent_color", "default") ?: "default"
        if (current == newAccent) return
        val currentIntensity = prefs.getString("surface_intensity", "soft_tint") ?: "soft_tint"
        if (currentIntensity == "strong_tint" && newAccent != "purple" && newAccent != "blue" && newAccent != "yellow" && newAccent != "red" && newAccent != "green" && newAccent != "orange") {
            prefs.edit().putString("surface_intensity", "soft_tint").apply()
        }
        captureScreenBitmap()
        prefs.edit().putString("accent_color", newAccent).commit()
        window.setWindowAnimations(0)
        recreate()
    }

    fun captureAndApplySurfaceIntensity(newIntensity: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("surface_intensity", "soft_tint") ?: "soft_tint"
        if (current == newIntensity) return
        captureScreenBitmap()
        prefs.edit().putString("surface_intensity", newIntensity).commit()
        window.setWindowAnimations(0)
        recreate()
    }

    private fun captureScreenBitmap() {
        val decor = window.decorView
        try {
            val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bmp))
            ThemeRevealHolder.bitmap = bmp
            ThemeRevealHolder.cx = decor.width / 2
            ThemeRevealHolder.cy = decor.height / 2
        } catch (_: Exception) {
        }
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBarVisibility()
    }

    fun applyStatusBarFlagToDialog(dialog: android.app.Dialog) {
        val hide = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("hide_status_bar", false)
        if (hide) {
            dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun applyStatusBarVisibility() {
        val hide = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("hide_status_bar", false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

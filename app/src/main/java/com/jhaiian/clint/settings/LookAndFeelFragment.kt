package com.jhaiian.clint.settings

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import android.content.res.ColorStateList
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.ThemeSwatchUtils

class LookAndFeelFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.look_and_feel_preferences, rootKey)
        applyIconTints()

        findPreference<Preference>("app_theme")
            ?.setOnPreferenceClickListener {
                showThemeDialog()
                true
            }

        findPreference<Preference>("accent_color")
            ?.setOnPreferenceClickListener {
                showAccentColorDialog()
                true
            }

        findPreference<Preference>("surface_intensity")
            ?.setOnPreferenceClickListener {
                showSurfaceIntensityDialog()
                true
            }

        findPreference<Preference>("scroll_hide_mode")
            ?.setOnPreferenceClickListener {
                showScrollHideModeDialog()
                true
            }

        findPreference<Preference>("address_bar_position")
            ?.setOnPreferenceClickListener {
                showAddressBarPositionDialog()
                true
            }

        findPreference<Preference>("menu_style")
            ?.setOnPreferenceClickListener {
                showMenuStyleDialog()
                true
            }

        findPreference<SwitchPreferenceCompat>("hide_status_bar")
            ?.setOnPreferenceChangeListener { _, newValue ->
                showRestartDialog(newValue as Boolean)
                false
            }
    }

    override fun onResume() {
        super.onResume()
        updateDarkWebSwitchState()
        updateAccentColorPrefState()
        updateSurfaceIntensityState()
        updateScrollHideModeSummary()
        updateAddressBarPositionSummary()
        updateMenuStyleSummary()
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        listOf("app_theme", "accent_color", "surface_intensity", "force_dark_web", "scroll_hide_mode", "hide_status_bar", "address_bar_position", "menu_style").forEach { key ->
            findPreference<Preference>(key)?.let { pref ->
                pref.icon?.mutate()?.let { icon ->
                    DrawableCompat.setTintList(DrawableCompat.wrap(icon), tint)
                    pref.icon = icon
                }
            }
        }
    }

    private fun updateDarkWebSwitchState() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val theme = prefs.getString("app_theme", "default") ?: "default"
        val pref = findPreference<SwitchPreferenceCompat>("force_dark_web") ?: return
        val savedValue = prefs.getBoolean("force_dark_web", false)
        when (theme) {
            "dark" -> {
                pref.isEnabled = false
                pref.isChecked = true
                prefs.edit().putBoolean("force_dark_web", savedValue).apply()
            }
            "light" -> {
                pref.isEnabled = false
                pref.isChecked = false
                prefs.edit().putBoolean("force_dark_web", savedValue).apply()
            }
            else -> {
                pref.isEnabled = true
                pref.isChecked = savedValue
            }
        }
    }

    private fun updateAccentColorPrefState() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val accent = prefs.getString("accent_color", "default") ?: "default"
        val pref = findPreference<Preference>("accent_color") ?: return
        pref.isEnabled = true
        pref.summary = when (accent) {
            "material_you" -> getString(R.string.accent_material_you)
            "purple" -> getString(R.string.accent_purple)
            "blue" -> getString(R.string.accent_blue)
            "yellow" -> getString(R.string.accent_yellow)
            "red" -> getString(R.string.accent_red)
            "green" -> getString(R.string.accent_green)
            "orange" -> getString(R.string.accent_orange)
            else -> getString(R.string.accent_default)
        }
    }

    private fun updateSurfaceIntensityState() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val theme = prefs.getString("app_theme", "default") ?: "default"
        val accent = prefs.getString("accent_color", "default") ?: "default"
        val intensity = prefs.getString("surface_intensity", "soft_tint") ?: "soft_tint"
        val pref = findPreference<Preference>("surface_intensity") ?: return
        val enabled = isSurfaceIntensityEnabled(theme, accent)
        pref.isEnabled = enabled
        if (!enabled) {
            pref.summary = getString(R.string.pref_surface_intensity_disabled_summary)
        } else {
            pref.summary = when (intensity) {
                "strong_tint" -> getString(R.string.surface_intensity_strong)
                "pure_mode" -> getString(R.string.surface_intensity_pure)
                else -> getString(R.string.surface_intensity_soft)
            }
        }
    }

    private fun isSurfaceIntensityEnabled(theme: String, accent: String): Boolean =
        ThemeSwatchUtils.isSurfaceIntensityEnabled(theme, accent)

    private fun updateAddressBarPositionSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val position = prefs.getString("address_bar_position", "top") ?: "top"
        val pref = findPreference<Preference>("address_bar_position") ?: return
        pref.summary = when (position) {
            "top" -> getString(R.string.address_bar_position_top)
            "bottom" -> getString(R.string.address_bar_position_bottom)
            else -> getString(R.string.address_bar_position_split)
        }
    }

    private fun updateScrollHideModeSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val mode = prefs.getString("scroll_hide_mode", "off") ?: "off"
        val pref = findPreference<Preference>("scroll_hide_mode") ?: return
        pref.summary = when (mode) {
            "off" -> getString(R.string.nested_scroll_off)
            "navigation_bar" -> getString(R.string.nested_scroll_nav_bar)
            "both" -> getString(R.string.nested_scroll_both)
            else -> getString(R.string.nested_scroll_search_bar)
        }
    }

    private fun applyTheme(newTheme: String) {
        val activity = requireActivity() as? ClintActivity ?: return
        activity.captureAndRecreate(newTheme)
    }

    private fun applyAccentColor(newAccent: String) {
        val activity = requireActivity() as? ClintActivity ?: return
        activity.captureAndApplyAccentColor(newAccent)
    }

    private fun applySurfaceIntensity(newIntensity: String) {
        val activity = requireActivity() as? ClintActivity ?: return
        activity.captureAndApplySurfaceIntensity(newIntensity)
    }

    private fun showThemeDialog() {
        val activity = requireActivity() as? ClintActivity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("app_theme", "default") ?: "default"

        val dialogView = layoutInflater.inflate(R.layout.dialog_theme_selector, null)
        val optionDefault = dialogView.findViewById<View>(R.id.option_default)
        val optionDark = dialogView.findViewById<View>(R.id.option_dark)
        val optionLight = dialogView.findViewById<View>(R.id.option_light)
        val checkDefault = dialogView.findViewById<ImageView>(R.id.check_default)
        val checkDark = dialogView.findViewById<ImageView>(R.id.check_dark)
        val checkLight = dialogView.findViewById<ImageView>(R.id.check_light)

        checkDefault.visibility = if (current == "default") View.VISIBLE else View.INVISIBLE
        checkDark.visibility = if (current == "dark") View.VISIBLE else View.INVISIBLE
        checkLight.visibility = if (current == "light") View.VISIBLE else View.INVISIBLE

        val dialog = MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.pref_app_theme_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()

        optionDefault.setOnClickListener { dialog.dismiss(); applyTheme("default") }
        optionDark.setOnClickListener { dialog.dismiss(); applyTheme("dark") }
        optionLight.setOnClickListener { dialog.dismiss(); applyTheme("light") }

        activity.applyStatusBarFlagToDialog(dialog)
        dialog.show()
    }

    private fun buildSwatchDrawable(bgColor: Int, surfaceColor: Int, accentColor: Int) =
        ThemeSwatchUtils.buildSwatchDrawable(requireContext(), bgColor, surfaceColor, accentColor)

    private fun showAccentColorDialog() {
        val activity = requireActivity() as? ClintActivity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("accent_color", "default") ?: "default"
        val theme = prefs.getString("app_theme", "default") ?: "default"

        val (defaultBg, defaultSurface, defaultAccent) = ThemeSwatchUtils.resolveDefaultSwatchColors(requireContext(), theme)

        val (matYouBg, matYouSurface, matYouAccent) = ThemeSwatchUtils.resolveMaterialYouSwatchColors(requireContext(), theme)

        val (purpleBg, purpleSurface, purpleAccent) = ThemeSwatchUtils.resolvePurpleSwatchColors(requireContext(), theme)

        val (blueBg, blueSurface, blueAccent) = ThemeSwatchUtils.resolveBlueSwatchColors(requireContext(), theme)

        val (yellowBg, yellowSurface, yellowAccent) = ThemeSwatchUtils.resolveYellowSwatchColors(requireContext(), theme)

        val (redBg, redSurface, redAccent) = ThemeSwatchUtils.resolveRedSwatchColors(requireContext(), theme)

        val (greenBg, greenSurface, greenAccent) = ThemeSwatchUtils.resolveGreenSwatchColors(requireContext(), theme)

        val (orangeBg, orangeSurface, orangeAccent) = ThemeSwatchUtils.resolveOrangeSwatchColors(requireContext(), theme)

        val dialogView = layoutInflater.inflate(R.layout.dialog_accent_color_selector, null)
        val optionDefault = dialogView.findViewById<View>(R.id.option_accent_default)
        val optionMaterialYou = dialogView.findViewById<View>(R.id.option_material_you)
        val optionPurple = dialogView.findViewById<View>(R.id.option_accent_purple)
        val optionBlue = dialogView.findViewById<View>(R.id.option_accent_blue)
        val optionYellow = dialogView.findViewById<View>(R.id.option_accent_yellow)
        val optionRed = dialogView.findViewById<View>(R.id.option_accent_red)
        val optionGreen = dialogView.findViewById<View>(R.id.option_accent_green)
        val optionOrange = dialogView.findViewById<View>(R.id.option_accent_orange)
        val checkDefault = dialogView.findViewById<ImageView>(R.id.check_accent_default)
        val checkMaterialYou = dialogView.findViewById<ImageView>(R.id.check_material_you)
        val checkPurple = dialogView.findViewById<ImageView>(R.id.check_accent_purple)
        val checkBlue = dialogView.findViewById<ImageView>(R.id.check_accent_blue)
        val checkYellow = dialogView.findViewById<ImageView>(R.id.check_accent_yellow)
        val checkRed = dialogView.findViewById<ImageView>(R.id.check_accent_red)
        val checkGreen = dialogView.findViewById<ImageView>(R.id.check_accent_green)
        val checkOrange = dialogView.findViewById<ImageView>(R.id.check_accent_orange)
        val swatchDefault = dialogView.findViewById<View>(R.id.swatch_default)
        val swatchMaterialYou = dialogView.findViewById<View>(R.id.swatch_material_you)
        val swatchPurple = dialogView.findViewById<View>(R.id.swatch_purple)
        val swatchBlue = dialogView.findViewById<View>(R.id.swatch_blue)
        val swatchYellow = dialogView.findViewById<View>(R.id.swatch_yellow)
        val swatchRed = dialogView.findViewById<View>(R.id.swatch_red)
        val swatchGreen = dialogView.findViewById<View>(R.id.swatch_green)
        val swatchOrange = dialogView.findViewById<View>(R.id.swatch_orange)

        swatchDefault.background = buildSwatchDrawable(defaultBg, defaultSurface, defaultAccent)
        swatchMaterialYou.background = buildSwatchDrawable(matYouBg, matYouSurface, matYouAccent)
        swatchPurple.background = buildSwatchDrawable(purpleBg, purpleSurface, purpleAccent)
        swatchBlue.background = buildSwatchDrawable(blueBg, blueSurface, blueAccent)
        swatchYellow.background = buildSwatchDrawable(yellowBg, yellowSurface, yellowAccent)
        swatchRed.background = buildSwatchDrawable(redBg, redSurface, redAccent)
        swatchGreen.background = buildSwatchDrawable(greenBg, greenSurface, greenAccent)
        swatchOrange.background = buildSwatchDrawable(orangeBg, orangeSurface, orangeAccent)

        checkDefault.visibility = if (current == "default") View.VISIBLE else View.INVISIBLE
        checkMaterialYou.visibility = if (current == "material_you") View.VISIBLE else View.INVISIBLE
        checkPurple.visibility = if (current == "purple") View.VISIBLE else View.INVISIBLE
        checkBlue.visibility = if (current == "blue") View.VISIBLE else View.INVISIBLE
        checkYellow.visibility = if (current == "yellow") View.VISIBLE else View.INVISIBLE
        checkRed.visibility = if (current == "red") View.VISIBLE else View.INVISIBLE
        checkGreen.visibility = if (current == "green") View.VISIBLE else View.INVISIBLE
        checkOrange.visibility = if (current == "orange") View.VISIBLE else View.INVISIBLE

        val dialog = MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.pref_accent_color_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()

        optionDefault.setOnClickListener { dialog.dismiss(); applyAccentColor("default") }
        optionMaterialYou.setOnClickListener { dialog.dismiss(); applyAccentColor("material_you") }
        optionPurple.setOnClickListener { dialog.dismiss(); applyAccentColor("purple") }
        optionBlue.setOnClickListener { dialog.dismiss(); applyAccentColor("blue") }
        optionYellow.setOnClickListener { dialog.dismiss(); applyAccentColor("yellow") }
        optionRed.setOnClickListener { dialog.dismiss(); applyAccentColor("red") }
        optionGreen.setOnClickListener { dialog.dismiss(); applyAccentColor("green") }
        optionOrange.setOnClickListener { dialog.dismiss(); applyAccentColor("orange") }

        activity.applyStatusBarFlagToDialog(dialog)
        dialog.show()
    }

    private fun showSurfaceIntensityDialog() {
        val activity = requireActivity() as? ClintActivity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("surface_intensity", "soft_tint") ?: "soft_tint"
        val theme = prefs.getString("app_theme", "default") ?: "default"
        val accent = prefs.getString("accent_color", "default") ?: "default"

        val isPurple = accent == "purple"
        val isBlue = accent == "blue"
        val isYellow = accent == "yellow"
        val isRed = accent == "red"
        val isGreen = accent == "green"
        val isOrange = accent == "orange"
        val isLight = theme == "light"

        val ctx = requireContext()
        val accentColor = when {
            isPurple -> ThemeSwatchUtils.resolvePurpleSwatchColors(ctx, theme).accent
            isBlue -> ThemeSwatchUtils.resolveBlueSwatchColors(ctx, theme).accent
            isYellow -> ThemeSwatchUtils.resolveYellowSwatchColors(ctx, theme).accent
            isRed -> ThemeSwatchUtils.resolveRedSwatchColors(ctx, theme).accent
            isGreen -> ThemeSwatchUtils.resolveGreenSwatchColors(ctx, theme).accent
            isOrange -> ThemeSwatchUtils.resolveOrangeSwatchColors(ctx, theme).accent
            else -> MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFFBB86FC.toInt())
        }

        val (softBg, softSurface) = ThemeSwatchUtils.resolveSoftTintSwatchBgSurface(ctx, theme, accent)
        val strongBg: Int
        val strongSurface: Int
        when {
            isBlue -> {
                strongBg = ContextCompat.getColor(ctx, if (isLight) R.color.blue_accent_light_bg else R.color.blue_accent_dark_bg)
                strongSurface = ContextCompat.getColor(ctx, if (isLight) R.color.blue_accent_light_surface else R.color.blue_accent_dark_surface)
            }
            isYellow -> {
                strongBg = ContextCompat.getColor(ctx, if (isLight) R.color.yellow_accent_light_bg else R.color.yellow_accent_dark_bg)
                strongSurface = ContextCompat.getColor(ctx, if (isLight) R.color.yellow_accent_light_surface else R.color.yellow_accent_dark_surface)
            }
            isRed -> {
                strongBg = ContextCompat.getColor(ctx, if (isLight) R.color.red_accent_light_bg else R.color.red_accent_dark_bg)
                strongSurface = ContextCompat.getColor(ctx, if (isLight) R.color.red_accent_light_surface else R.color.red_accent_dark_surface)
            }
            isGreen -> {
                strongBg = ContextCompat.getColor(ctx, if (isLight) R.color.green_accent_light_bg else R.color.green_accent_dark_bg)
                strongSurface = ContextCompat.getColor(ctx, if (isLight) R.color.green_accent_light_surface else R.color.green_accent_dark_surface)
            }
            isOrange -> {
                strongBg = ContextCompat.getColor(ctx, if (isLight) R.color.orange_accent_light_bg else R.color.orange_accent_dark_bg)
                strongSurface = ContextCompat.getColor(ctx, if (isLight) R.color.orange_accent_light_surface else R.color.orange_accent_dark_surface)
            }
            else -> {
                strongBg = ContextCompat.getColor(ctx, if (isLight) R.color.purple_accent_light_bg else R.color.purple_accent_dark_bg)
                strongSurface = ContextCompat.getColor(ctx, if (isLight) R.color.purple_accent_light_surface else R.color.purple_accent_dark_surface)
            }
        }
        val pureBg = if (isLight) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val pureSurface = if (isLight) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

        val dialogView = layoutInflater.inflate(R.layout.dialog_surface_intensity, null)
        val optionSoft = dialogView.findViewById<View>(R.id.option_soft_tint)
        val optionStrong = dialogView.findViewById<View>(R.id.option_strong_tint)
        val optionPure = dialogView.findViewById<View>(R.id.option_pure_mode)
        val dividerStrong = dialogView.findViewById<View>(R.id.divider_strong_tint)
        val checkSoft = dialogView.findViewById<ImageView>(R.id.check_soft_tint)
        val checkStrong = dialogView.findViewById<ImageView>(R.id.check_strong_tint)
        val checkPure = dialogView.findViewById<ImageView>(R.id.check_pure_mode)
        val swatchSoft = dialogView.findViewById<View>(R.id.swatch_soft_tint)
        val swatchStrong = dialogView.findViewById<View>(R.id.swatch_strong_tint)
        val swatchPure = dialogView.findViewById<View>(R.id.swatch_pure_mode)
        val descPure = dialogView.findViewById<TextView>(R.id.desc_pure_mode)

        swatchSoft.background = buildSwatchDrawable(softBg, softSurface, accentColor)
        swatchStrong.background = buildSwatchDrawable(strongBg, strongSurface, accentColor)
        swatchPure.background = buildSwatchDrawable(pureBg, pureSurface, accentColor)

        descPure.text = getString(if (isLight) R.string.surface_intensity_pure_light_desc else R.string.surface_intensity_pure_dark_desc)

        if (isPurple || isBlue || isYellow || isRed || isGreen || isOrange) {
            optionStrong.visibility = View.VISIBLE
            dividerStrong.visibility = View.VISIBLE
        }

        checkSoft.visibility = if (current == "soft_tint") View.VISIBLE else View.INVISIBLE
        checkStrong.visibility = if (current == "strong_tint") View.VISIBLE else View.INVISIBLE
        checkPure.visibility = if (current == "pure_mode") View.VISIBLE else View.INVISIBLE

        val dialog = MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.pref_surface_intensity_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()

        optionSoft.setOnClickListener { dialog.dismiss(); applySurfaceIntensity("soft_tint") }
        optionStrong.setOnClickListener { dialog.dismiss(); applySurfaceIntensity("strong_tint") }
        optionPure.setOnClickListener { dialog.dismiss(); applySurfaceIntensity("pure_mode") }

        activity.applyStatusBarFlagToDialog(dialog)
        dialog.show()
    }

    private fun showAddressBarPositionDialog() {
        val activity = requireActivity() as? ClintActivity ?: return
        val settingsActivity = requireActivity() as? SettingsActivity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("address_bar_position", "top") ?: "top"
        val theme = prefs.getString("app_theme", "default") ?: "default"
        val accent = prefs.getString("accent_color", "default") ?: "default"

        val ctx = requireContext()
        val (bgColor, surfaceColor, _) = when (accent) {
            "material_you" -> ThemeSwatchUtils.resolveMaterialYouSwatchColors(ctx, theme)
            "purple" -> ThemeSwatchUtils.resolvePurpleSwatchColors(ctx, theme)
            "blue" -> ThemeSwatchUtils.resolveBlueSwatchColors(ctx, theme)
            "yellow" -> ThemeSwatchUtils.resolveYellowSwatchColors(ctx, theme)
            "red" -> ThemeSwatchUtils.resolveRedSwatchColors(ctx, theme)
            "green" -> ThemeSwatchUtils.resolveGreenSwatchColors(ctx, theme)
            "orange" -> ThemeSwatchUtils.resolveOrangeSwatchColors(ctx, theme)
            else -> ThemeSwatchUtils.resolveDefaultSwatchColors(ctx, theme)
        }
        val colorOnSurface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0)

        val dialogView = layoutInflater.inflate(R.layout.dialog_address_bar_position, null)

        val optionTop = dialogView.findViewById<View>(R.id.option_position_top)
        val optionBottom = dialogView.findViewById<View>(R.id.option_position_bottom)
        val optionSplit = dialogView.findViewById<View>(R.id.option_position_split)
        val checkTop = dialogView.findViewById<ImageView>(R.id.check_position_top)
        val checkBottom = dialogView.findViewById<ImageView>(R.id.check_position_bottom)
        val checkSplit = dialogView.findViewById<ImageView>(R.id.check_position_split)
        val swatchTop = dialogView.findViewById<View>(R.id.swatch_position_top)
        val swatchBottom = dialogView.findViewById<View>(R.id.swatch_position_bottom)
        val swatchSplit = dialogView.findViewById<View>(R.id.swatch_position_split)

        val density = ctx.resources.displayMetrics.density
        val cornerRadius = 8f * density

        fun makeFrameBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(color)
        }

        swatchTop.background = makeFrameBg(bgColor)
        swatchBottom.background = makeFrameBg(bgColor)
        swatchSplit.background = makeFrameBg(bgColor)

        dialogView.findViewById<View>(R.id.swatch_top_topbar).setBackgroundColor(surfaceColor)
        dialogView.findViewById<View>(R.id.swatch_top_pill).setBackgroundColor(colorOnSurface)
        dialogView.findViewById<View>(R.id.swatch_bottom_bottombar).setBackgroundColor(surfaceColor)
        dialogView.findViewById<View>(R.id.swatch_bottom_pill).setBackgroundColor(colorOnSurface)
        dialogView.findViewById<View>(R.id.swatch_split_topbar).setBackgroundColor(surfaceColor)
        dialogView.findViewById<View>(R.id.swatch_split_pill).setBackgroundColor(colorOnSurface)
        dialogView.findViewById<View>(R.id.swatch_split_bottombar).setBackgroundColor(surfaceColor)

        checkTop.visibility = if (current == "top") View.VISIBLE else View.INVISIBLE
        checkBottom.visibility = if (current == "bottom") View.VISIBLE else View.INVISIBLE
        checkSplit.visibility = if (current == "split") View.VISIBLE else View.INVISIBLE

        val selectionDialog = MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.pref_address_bar_position_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()

        fun confirmRestart(newPosition: String) {
            if (newPosition == current) {
                selectionDialog.dismiss()
                return
            }
            val currentMode = prefs.getString("scroll_hide_mode", "off") ?: "off"
            val savedModeForNew = prefs.getString("scroll_hide_mode_$newPosition", "off") ?: "off"
            val validModeForNew = when (newPosition) {
                "top" -> if (savedModeForNew in listOf("off", "search_bar")) savedModeForNew else "off"
                "bottom" -> if (savedModeForNew in listOf("off", "search_bar")) savedModeForNew else "off"
                else -> savedModeForNew
            }
            prefs.edit()
                .putString("scroll_hide_mode_$current", currentMode)
                .putString("address_bar_position", newPosition)
                .putString("scroll_hide_mode", validModeForNew)
                .apply()
            updateAddressBarPositionSummary()
            updateScrollHideModeSummary()
            selectionDialog.dismiss()
            MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
                .setTitle(getString(R.string.restart_required_title))
                .setMessage(getString(R.string.restart_required_message))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
                    prefs.edit()
                        .putString("address_bar_position", current)
                        .putString("scroll_hide_mode", currentMode)
                        .apply()
                    updateAddressBarPositionSummary()
                    updateScrollHideModeSummary()
                    settingsActivity.pendingRestart = false
                }
                .setNeutralButton(getString(R.string.restart_required_confirm)) { _, _ ->
                    settingsActivity.restartApp()
                }
                .setPositiveButton(getString(R.string.action_later)) { _, _ ->
                    settingsActivity.scheduleRestartIfChanged()
                }
                .create().also { settingsActivity.applyStatusBarFlagToDialog(it) }.show()
        }

        optionTop.setOnClickListener { confirmRestart("top") }
        optionBottom.setOnClickListener { confirmRestart("bottom") }
        optionSplit.setOnClickListener { confirmRestart("split") }

        activity.applyStatusBarFlagToDialog(selectionDialog)
        selectionDialog.show()
    }

    private fun showScrollHideModeDialog() {
        val activity = requireActivity() as? ClintActivity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("scroll_hide_mode", "off") ?: "off"
        val theme = prefs.getString("app_theme", "default") ?: "default"
        val accent = prefs.getString("accent_color", "default") ?: "default"

        val ctx = requireContext()
        val (bgColor, surfaceColor, accentColor) = when (accent) {
            "material_you" -> ThemeSwatchUtils.resolveMaterialYouSwatchColors(ctx, theme)
            "purple" -> ThemeSwatchUtils.resolvePurpleSwatchColors(ctx, theme)
            "blue" -> ThemeSwatchUtils.resolveBlueSwatchColors(ctx, theme)
            "yellow" -> ThemeSwatchUtils.resolveYellowSwatchColors(ctx, theme)
            "red" -> ThemeSwatchUtils.resolveRedSwatchColors(ctx, theme)
            "green" -> ThemeSwatchUtils.resolveGreenSwatchColors(ctx, theme)
            "orange" -> ThemeSwatchUtils.resolveOrangeSwatchColors(ctx, theme)
            else -> ThemeSwatchUtils.resolveDefaultSwatchColors(ctx, theme)
        }
        val colorOnSurface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0)

        val dialogView = layoutInflater.inflate(R.layout.dialog_scroll_hide_mode, null)

        val optionOff = dialogView.findViewById<View>(R.id.option_scroll_off)
        val optionTop = dialogView.findViewById<View>(R.id.option_scroll_top)
        val optionBottom = dialogView.findViewById<View>(R.id.option_scroll_bottom)
        val optionBoth = dialogView.findViewById<View>(R.id.option_scroll_both)

        val checkOff = dialogView.findViewById<ImageView>(R.id.check_scroll_off)
        val checkTop = dialogView.findViewById<ImageView>(R.id.check_scroll_top)
        val checkBottom = dialogView.findViewById<ImageView>(R.id.check_scroll_bottom)
        val checkBoth = dialogView.findViewById<ImageView>(R.id.check_scroll_both)

        val swatchOffFrame = dialogView.findViewById<View>(R.id.swatch_scroll_off)
        val swatchTopFrame = dialogView.findViewById<View>(R.id.swatch_scroll_top)
        val swatchBottomFrame = dialogView.findViewById<View>(R.id.swatch_scroll_bottom)
        val swatchBothFrame = dialogView.findViewById<View>(R.id.swatch_scroll_both)

        val swatchOffTopBar = dialogView.findViewById<View>(R.id.swatch_off_top)
        val swatchOffPill = dialogView.findViewById<View>(R.id.swatch_off_pill)
        val swatchOffBottomBar = dialogView.findViewById<View>(R.id.swatch_off_bottom)

        val swatchTopTopBar = dialogView.findViewById<View>(R.id.swatch_top_top)
        val swatchTopPill = dialogView.findViewById<View>(R.id.swatch_top_pill)
        val swatchTopBottomBar = dialogView.findViewById<View>(R.id.swatch_top_bottom)

        val swatchBottomTopBar = dialogView.findViewById<View>(R.id.swatch_bottom_top)
        val swatchBottomPill = dialogView.findViewById<View>(R.id.swatch_bottom_pill)
        val swatchBottomBottomBar = dialogView.findViewById<View>(R.id.swatch_bottom_bottom)

        val swatchBothTopBar = dialogView.findViewById<View>(R.id.swatch_both_top)
        val swatchBothPill = dialogView.findViewById<View>(R.id.swatch_both_pill)
        val swatchBothBottomBar = dialogView.findViewById<View>(R.id.swatch_both_bottom)

        val swatchOffNavDots = dialogView.findViewById<View>(R.id.swatch_off_nav_dots)
        val swatchTopNavDots = dialogView.findViewById<View>(R.id.swatch_top_nav_dots)
        val swatchBottomNavDots = dialogView.findViewById<View>(R.id.swatch_bottom_nav_dots)
        val swatchBothNavDots = dialogView.findViewById<View>(R.id.swatch_both_nav_dots)

        val density = ctx.resources.displayMetrics.density
        val cornerRadius = 8f * density
        val barHeightPx = 14f * density

        fun makeBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(color)
        }

        fun makePillBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 3f * density
            setColor(color)
        }

        listOf(swatchOffFrame, swatchTopFrame, swatchBottomFrame, swatchBothFrame).forEach {
            it.background = makeBg(bgColor)
        }
        listOf(swatchOffPill, swatchTopPill, swatchBottomPill, swatchBothPill).forEach {
            it.background = makePillBg(colorOnSurface)
        }

        val position = prefs.getString("address_bar_position", "top") ?: "top"

        val topBarColor = if (position != "bottom") surfaceColor else bgColor
        val bottomBarColor = if (position != "top") surfaceColor else bgColor

        listOf(swatchOffTopBar, swatchTopTopBar, swatchBottomTopBar, swatchBothTopBar).forEach {
            it.setBackgroundColor(topBarColor)
        }
        listOf(swatchOffBottomBar, swatchTopBottomBar, swatchBottomBottomBar, swatchBothBottomBar).forEach {
            it.setBackgroundColor(bottomBarColor)
        }

        val dividerAfterOff = dialogView.findViewById<View>(R.id.divider_after_off)
        val dividerAfterTop = dialogView.findViewById<View>(R.id.divider_after_top)
        val dividerAfterBottom = dialogView.findViewById<View>(R.id.divider_after_bottom)

        when (position) {
            "top" -> {
                optionBottom.visibility = View.GONE
                dividerAfterTop.visibility = View.GONE
                optionBoth.visibility = View.GONE
                dividerAfterBottom.visibility = View.GONE
            }
            "bottom" -> {
                optionTop.visibility = View.GONE
                dividerAfterTop.visibility = View.GONE
                optionBoth.visibility = View.GONE
                dividerAfterBottom.visibility = View.GONE
                dialogView.findViewById<TextView>(R.id.title_scroll_bottom).text =
                    getString(R.string.nested_scroll_search_bar)
                dialogView.findViewById<TextView>(R.id.desc_scroll_bottom).text =
                    getString(R.string.nested_scroll_search_bar_desc)
                listOf(swatchOffPill, swatchBottomPill).forEach { pill ->
                    val lp = pill.layoutParams as FrameLayout.LayoutParams
                    lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = 0
                    lp.bottomMargin = (4 * density).toInt()
                    pill.layoutParams = lp
                    pill.bringToFront()
                }
            }
            "split" -> {
                listOf(swatchOffNavDots, swatchTopNavDots, swatchBottomNavDots, swatchBothNavDots).forEach {
                    it.visibility = View.VISIBLE
                }
            }
        }

        checkOff.visibility = if (current == "off") View.VISIBLE else View.INVISIBLE
        checkTop.visibility = if (current == "search_bar" && position != "bottom") View.VISIBLE else View.INVISIBLE
        checkBottom.visibility = if ((current == "search_bar" && position == "bottom") || (current == "navigation_bar" && position == "split")) View.VISIBLE else View.INVISIBLE
        checkBoth.visibility = if (current == "both") View.VISIBLE else View.INVISIBLE

        val animatorTop = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val t = -(anim.animatedValue as Float) * barHeightPx
                swatchTopTopBar.translationY = t
                swatchTopPill.translationY = t
            }
        }

        val animatorBottom = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val t = (anim.animatedValue as Float) * barHeightPx
                swatchBottomBottomBar.translationY = t
                if (position == "bottom") swatchBottomPill.translationY = t
                if (position == "split") swatchBottomNavDots.translationY = t
            }
        }

        val animatorBoth = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                val topT = -f * barHeightPx
                val botT = f * barHeightPx
                swatchBothTopBar.translationY = topT
                swatchBothPill.translationY = topT
                swatchBothBottomBar.translationY = botT
                if (position == "split") swatchBothNavDots.translationY = botT
            }
        }

        if (optionTop.visibility == View.VISIBLE) animatorTop.start()
        if (optionBottom.visibility == View.VISIBLE) animatorBottom.start()
        if (optionBoth.visibility == View.VISIBLE) animatorBoth.start()

        fun applyMode(mode: String) {
            prefs.edit()
                .putString("scroll_hide_mode", mode)
                .putString("scroll_hide_mode_$position", mode)
                .apply()
            updateScrollHideModeSummary()
        }

        val dialog = MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.pref_nested_scroll_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()

        dialog.setOnDismissListener {
            animatorTop.cancel()
            animatorBottom.cancel()
            animatorBoth.cancel()
        }

        fun confirmWarning(titleRes: Int, messageRes: Int, mode: String) {
            MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
                .setTitle(getString(titleRes))
                .setMessage(getString(messageRes))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_enable_anyway)) { _, _ ->
                    dialog.dismiss()
                    applyMode(mode)
                }
                .create().also { activity.applyStatusBarFlagToDialog(it) }.show()
        }

        optionOff.setOnClickListener { dialog.dismiss(); applyMode("off") }
        optionTop.setOnClickListener { confirmWarning(R.string.nested_scroll_warning_title, R.string.nested_scroll_warning_message, "search_bar") }
        optionBottom.setOnClickListener {
            val mode = if (position == "bottom") "search_bar" else "navigation_bar"
            confirmWarning(R.string.nested_scroll_warning_title, R.string.nested_scroll_warning_message, mode)
        }
        optionBoth.setOnClickListener { confirmWarning(R.string.nested_scroll_warning_title, R.string.nested_scroll_warning_message, "both") }

        activity.applyStatusBarFlagToDialog(dialog)
        dialog.show()
    }

    private fun updateMenuStyleSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val style = prefs.getString("menu_style", "popup") ?: "popup"
        val pref = findPreference<Preference>("menu_style") ?: return
        pref.summary = when (style) {
            "bottom_sheet" -> getString(R.string.menu_style_bottom_sheet)
            else -> getString(R.string.menu_style_popup)
        }
    }

    private fun showMenuStyleDialog() {
        val activity = requireActivity() as? ClintActivity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("menu_style", "popup") ?: "popup"
        val theme = prefs.getString("app_theme", "default") ?: "default"
        val accent = prefs.getString("accent_color", "default") ?: "default"
        val position = prefs.getString("address_bar_position", "top") ?: "top"

        val ctx = requireContext()
        val (bgColor, surfaceColor, _) = when (accent) {
            "material_you" -> ThemeSwatchUtils.resolveMaterialYouSwatchColors(ctx, theme)
            "purple" -> ThemeSwatchUtils.resolvePurpleSwatchColors(ctx, theme)
            "blue" -> ThemeSwatchUtils.resolveBlueSwatchColors(ctx, theme)
            "yellow" -> ThemeSwatchUtils.resolveYellowSwatchColors(ctx, theme)
            "red" -> ThemeSwatchUtils.resolveRedSwatchColors(ctx, theme)
            "green" -> ThemeSwatchUtils.resolveGreenSwatchColors(ctx, theme)
            "orange" -> ThemeSwatchUtils.resolveOrangeSwatchColors(ctx, theme)
            else -> ThemeSwatchUtils.resolveDefaultSwatchColors(ctx, theme)
        }
        val popupBgColor = MaterialColors.getColor(ctx, R.attr.clintPopupBackground, bgColor)
        val colorOnSurface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0)

        val dialogView = layoutInflater.inflate(R.layout.dialog_menu_style, null)
        val optionPopup = dialogView.findViewById<View>(R.id.option_menu_popup)
        val optionSheet = dialogView.findViewById<View>(R.id.option_menu_bottom_sheet)
        val checkPopup = dialogView.findViewById<ImageView>(R.id.check_menu_popup)
        val checkSheet = dialogView.findViewById<ImageView>(R.id.check_menu_bottom_sheet)

        val swatchPopupFrame = dialogView.findViewById<View>(R.id.swatch_menu_popup)
        val swatchPopupTopBar = dialogView.findViewById<View>(R.id.swatch_popup_topbar)
        val swatchPopupPill = dialogView.findViewById<View>(R.id.swatch_popup_pill)
        val swatchPopupBottomBar = dialogView.findViewById<View>(R.id.swatch_popup_bottombar)
        val swatchPopupCard = dialogView.findViewById<View>(R.id.swatch_popup_card)
        val swatchPopupCardBg = dialogView.findViewById<View>(R.id.swatch_popup_card_bg)
        val swatchPopupNavDots = dialogView.findViewById<View>(R.id.swatch_popup_nav_dots)

        val swatchSheetFrame = dialogView.findViewById<View>(R.id.swatch_menu_bottom_sheet)
        val swatchSheetTopBar = dialogView.findViewById<View>(R.id.swatch_sheet_topbar)
        val swatchSheetPill = dialogView.findViewById<View>(R.id.swatch_sheet_pill)
        val swatchSheetBottomBar = dialogView.findViewById<View>(R.id.swatch_sheet_bottombar)
        val swatchSheetPanelBg = dialogView.findViewById<View>(R.id.swatch_sheet_panel_bg)
        val swatchSheetNavDots = dialogView.findViewById<View>(R.id.swatch_sheet_nav_dots)

        val density = ctx.resources.displayMetrics.density
        val cornerRadius = 8f * density

        fun makeFrameBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(color)
        }

        fun makeSheetPanelBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f)
            setColor(color)
        }

        fun makeCardBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 4f * density
            setColor(color)
        }

        swatchPopupFrame.background = makeFrameBg(bgColor)
        swatchSheetFrame.background = makeFrameBg(bgColor)

        val topBarColor = if (position != "bottom") surfaceColor else bgColor
        val bottomBarColor = if (position != "top") surfaceColor else bgColor

        swatchPopupTopBar.setBackgroundColor(topBarColor)
        swatchPopupCardBg.background = makeCardBg(popupBgColor)

        swatchSheetTopBar.setBackgroundColor(topBarColor)
        swatchSheetPanelBg.background = makeSheetPanelBg(popupBgColor)

        when (position) {
            "bottom" -> {
                swatchPopupBottomBar.visibility = View.VISIBLE
                swatchPopupBottomBar.setBackgroundColor(bottomBarColor)
                swatchSheetBottomBar.visibility = View.VISIBLE
                swatchSheetBottomBar.setBackgroundColor(bottomBarColor)

                listOf(swatchPopupPill, swatchSheetPill).forEach { pill ->
                    val lp = pill.layoutParams as FrameLayout.LayoutParams
                    lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = 0
                    lp.bottomMargin = (4 * density).toInt()
                    pill.layoutParams = lp
                    pill.bringToFront()
                }

                swatchPopupPill.setBackgroundColor(colorOnSurface)
                swatchSheetPill.setBackgroundColor(colorOnSurface)
            }
            "split" -> {
                swatchPopupBottomBar.visibility = View.VISIBLE
                swatchPopupBottomBar.setBackgroundColor(bottomBarColor)
                swatchSheetBottomBar.visibility = View.VISIBLE
                swatchSheetBottomBar.setBackgroundColor(bottomBarColor)
                swatchPopupNavDots.visibility = View.VISIBLE
                swatchSheetNavDots.visibility = View.VISIBLE

                swatchPopupPill.setBackgroundColor(colorOnSurface)
                swatchSheetPill.setBackgroundColor(colorOnSurface)

                val popupCardLp = swatchPopupCard.layoutParams as FrameLayout.LayoutParams
                popupCardLp.gravity = Gravity.TOP or Gravity.END
                swatchPopupCard.layoutParams = popupCardLp
            }
            else -> {
                swatchPopupPill.setBackgroundColor(colorOnSurface)
                swatchSheetPill.setBackgroundColor(colorOnSurface)

                val popupCardLp = swatchPopupCard.layoutParams as FrameLayout.LayoutParams
                popupCardLp.gravity = Gravity.TOP or Gravity.END
                swatchPopupCard.layoutParams = popupCardLp
            }
        }

        checkPopup.visibility = if (current == "popup") View.VISIBLE else View.INVISIBLE
        checkSheet.visibility = if (current == "bottom_sheet") View.VISIBLE else View.INVISIBLE

        val dialog = MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.pref_menu_style_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()

        optionPopup.setOnClickListener {
            prefs.edit().putString("menu_style", "popup").apply()
            updateMenuStyleSummary()
            dialog.dismiss()
        }
        optionSheet.setOnClickListener {
            prefs.edit().putString("menu_style", "bottom_sheet").apply()
            updateMenuStyleSummary()
            dialog.dismiss()
        }

        activity.applyStatusBarFlagToDialog(dialog)
        dialog.show()
    }

    private fun showRestartDialog(newValue: Boolean) {
        val activity = requireActivity() as? SettingsActivity ?: return
        val pref = findPreference<SwitchPreferenceCompat>("hide_status_bar") ?: return
        MaterialAlertDialogBuilder(activity, activity.getDialogTheme())
            .setTitle(getString(R.string.restart_required_title))
            .setMessage(getString(R.string.restart_required_message))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
                activity.pendingRestart = false
            }
            .setNeutralButton(getString(R.string.restart_required_confirm)) { _, _ ->
                pref.isPersistent = false
                pref.isChecked = newValue
                pref.isPersistent = true
                activity.pendingHideStatusBar = newValue
                activity.restartApp()
            }
            .setPositiveButton(getString(R.string.action_later)) { _, _ ->
                pref.isPersistent = false
                pref.isChecked = newValue
                pref.isPersistent = true
                activity.pendingHideStatusBar = newValue
                activity.scheduleRestartIfChanged()
            }
            .create().also { activity.applyStatusBarFlagToDialog(it) }.show()
    }
}
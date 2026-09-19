package com.jhaiian.clint.settings.lookandfeel
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WebAsset

import androidx.compose.foundation.layout.padding
import com.jhaiian.clint.ui.ClintSwitch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.common.RowDivider
import com.jhaiian.clint.settings.common.SettingsRow
import com.jhaiian.clint.settings.common.SettingsScreenScaffold
import com.jhaiian.clint.settings.common.SettingsSection
import com.jhaiian.clint.setup.SectionLabel
import com.jhaiian.clint.ui.ThemeSwatchUtils
import com.jhaiian.clint.ui.theme.LocalClintColors

@Composable
fun LookAndFeelScreen(
    state: LookAndFeelUiState,
    onThemeSelected: (String) -> Unit,
    onAccentSelected: (String) -> Unit,
    onIntensitySelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onAddressBarPositionSelected: (String) -> Unit,
    onMenuStyleSelected: (String) -> Unit,
    onTabMenuStyleSelected: (String) -> Unit,
    onScrollHideModeSelected: (String) -> Unit,
    onHideStatusBarRowClicked: () -> Unit,
    onHideSystemNavigationRowClicked: () -> Unit,
    onCustomizeMenuRowClicked: () -> Unit,
    onExitConfirmationConfirmed: (String) -> Unit
) {
    val colors = LocalClintColors.current
    val intensityEnabled = ThemeSwatchUtils.isSurfaceIntensityEnabled(state.theme, state.accent)

    SettingsScreenScaffold(
        overlay = {
            when (state.openDialog) {
                LookAndFeelDialog.THEME -> ThemeSelectorDialog(
                    current = state.theme, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onThemeSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.ACCENT -> AccentColorDialog(
                    current = state.accent, theme = state.theme, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onAccentSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.SURFACE_INTENSITY -> SurfaceIntensityDialog(
                    current = state.intensity, theme = state.theme, accent = state.accent, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onIntensitySelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.ADDRESS_BAR_POSITION -> AddressBarPositionDialog(
                    current = state.addressBarPosition, theme = state.theme, accent = state.accent, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onAddressBarPositionSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.MENU_STYLE -> MenuStyleDialog(
                    current = state.menuStyle, addressBarPosition = state.addressBarPosition, theme = state.theme, accent = state.accent,
                    hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation, onSelect = onMenuStyleSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.TAB_MENU_STYLE -> TabMenuStyleDialog(
                    current = state.tabMenuStyle, theme = state.theme, accent = state.accent,
                    hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation, onSelect = onTabMenuStyleSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.SCROLL_HIDE_MODE -> ScrollHideModeDialog(
                    current = state.scrollHideMode, addressBarPosition = state.addressBarPosition, theme = state.theme, accent = state.accent,
                    hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation, onSelect = onScrollHideModeSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.EXIT_CONFIRMATION -> ExitConfirmationDialog(
                    current = state.exitConfirmation, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onConfirm = onExitConfirmationConfirmed, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.LANGUAGE -> LanguageSelectorDialog(
                    current = state.language, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onLanguageSelected, onDismiss = { state.openDialog = null }
                )
                null -> {}
            }
        }
    ) {
        SectionLabel(stringResource(R.string.pref_category_language).uppercase(), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Translate,
                title = stringResource(R.string.pref_language_title),
                summary = languageSummaryText(state.language),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.LANGUAGE }
            )
        }

        SectionLabel(stringResource(R.string.pref_category_appearance).uppercase(), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Palette,
                title = stringResource(R.string.pref_app_theme_title),
                summary = stringResource(R.string.pref_app_theme_summary),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.THEME }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Brush,
                title = stringResource(R.string.pref_accent_color_title),
                summary = stringResource(accentSummaryRes(state.accent)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.ACCENT }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Contrast,
                title = stringResource(R.string.pref_surface_intensity_title),
                summary = if (intensityEnabled) stringResource(intensitySummaryRes(state.intensity)) else stringResource(R.string.pref_surface_intensity_disabled_summary),
                colors = colors,
                enabled = intensityEnabled,
                onClick = { if (intensityEnabled) state.openDialog = LookAndFeelDialog.SURFACE_INTENSITY }
            )
        }

        SectionLabel(stringResource(R.string.pref_category_layout).uppercase(), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.SwapVert,
                title = stringResource(R.string.pref_nested_scroll_title),
                summary = stringResource(nestedScrollSummaryRes(state.scrollHideMode)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.SCROLL_HIDE_MODE }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.WebAsset,
                title = stringResource(R.string.pref_address_bar_position_title),
                summary = stringResource(addressBarPositionSummaryRes(state.addressBarPosition)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.ADDRESS_BAR_POSITION }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.MoreVert,
                title = stringResource(R.string.pref_menu_style_title),
                summary = stringResource(menuStyleSummaryRes(state.menuStyle)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.MENU_STYLE }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.GridView,
                title = stringResource(R.string.pref_tab_menu_style_title),
                summary = stringResource(tabMenuStyleSummaryRes(state.tabMenuStyle)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.TAB_MENU_STYLE }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Reorder,
                title = stringResource(R.string.menu_customization_title),
                summary = stringResource(R.string.menu_customization_settings_summary),
                colors = colors,
                onClick = onCustomizeMenuRowClicked
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Fullscreen,
                title = stringResource(R.string.hide_status_bar),
                summary = stringResource(R.string.hide_status_bar_summary),
                colors = colors,
                onClick = onHideStatusBarRowClicked,
                trailing = {
                    ClintSwitch(checked = state.hideStatusBar)
                }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Fullscreen,
                title = stringResource(R.string.hide_system_navigation),
                summary = stringResource(R.string.hide_system_navigation_summary),
                colors = colors,
                onClick = onHideSystemNavigationRowClicked,
                trailing = {
                    ClintSwitch(checked = state.hideSystemNavigation)
                }
            )
        }

        SectionLabel(stringResource(R.string.pref_category_navigation).uppercase(), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Close,
                title = stringResource(R.string.exit_confirmation_title),
                summary = stringResource(exitConfirmationSummaryRes(state.exitConfirmation)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.EXIT_CONFIRMATION }
            )
        }
    }
}

private fun accentSummaryRes(accent: String): Int = when (accent) {
    "material_you" -> R.string.accent_material_you
    "purple" -> R.string.accent_purple
    "deep_purple" -> R.string.accent_deep_purple
    "royal_purple" -> R.string.accent_royal_purple
    "amethyst" -> R.string.accent_amethyst
    "lavender" -> R.string.accent_lavender
    "teal" -> R.string.accent_teal
    "azure" -> R.string.accent_azure
    "pink" -> R.string.accent_pink
    "indigo" -> R.string.accent_indigo
    "cyan" -> R.string.accent_cyan
    "amber" -> R.string.accent_amber
    "mint" -> R.string.accent_mint
    "crimson" -> R.string.accent_crimson
    "slate" -> R.string.accent_slate
    "graphite" -> R.string.accent_graphite
    "obsidian" -> R.string.accent_obsidian
    "onyx" -> R.string.accent_onyx
    "titanium" -> R.string.accent_titanium
    "coral" -> R.string.accent_coral
    "burgundy" -> R.string.accent_burgundy
    "scarlet" -> R.string.accent_scarlet
    "midnight" -> R.string.accent_midnight
    "sepia" -> R.string.accent_sepia
    "mustard" -> R.string.accent_mustard
    "forest" -> R.string.accent_forest
    "sage" -> R.string.accent_sage
    "lime" -> R.string.accent_lime
    "olive" -> R.string.accent_olive
    "plum" -> R.string.accent_plum
    "violet" -> R.string.accent_violet
    "sand" -> R.string.accent_sand
    "ruby" -> R.string.accent_ruby
    "sky" -> R.string.accent_sky
    "charcoal" -> R.string.accent_charcoal
    "peach" -> R.string.accent_peach
    "terracotta" -> R.string.accent_terracotta
    "emerald" -> R.string.accent_emerald
    "blue" -> R.string.accent_blue
    "yellow" -> R.string.accent_yellow
    "lemon" -> R.string.accent_lemon
    "gold" -> R.string.accent_gold
    "red" -> R.string.accent_red
    "green" -> R.string.accent_green
    "orange" -> R.string.accent_orange
    "deep_orange" -> R.string.accent_deep_orange
    "tangerine" -> R.string.accent_tangerine
    "apricot" -> R.string.accent_apricot
    "copper" -> R.string.accent_copper
    else -> R.string.accent_default
}

private fun intensitySummaryRes(intensity: String): Int = when (intensity) {
    "amoled_no_tint" -> R.string.surface_intensity_amoled_no_tint
    "no_tint" -> R.string.surface_intensity_no_tint
    "strong_tint" -> R.string.surface_intensity_strong
    "pure_mode" -> R.string.surface_intensity_pure
    else -> R.string.surface_intensity_soft
}

private fun nestedScrollSummaryRes(mode: String): Int = when (mode) {
    "off" -> R.string.nested_scroll_off
    "navigation_bar" -> R.string.nested_scroll_nav_bar
    "both" -> R.string.nested_scroll_both
    else -> R.string.nested_scroll_search_bar
}

private fun addressBarPositionSummaryRes(position: String): Int = when (position) {
    "top" -> R.string.address_bar_position_top
    "bottom" -> R.string.address_bar_position_bottom
    else -> R.string.address_bar_position_split
}

private fun menuStyleSummaryRes(style: String): Int =
    if (style == "bottom_sheet") R.string.menu_style_bottom_sheet else R.string.menu_style_popup

private fun tabMenuStyleSummaryRes(style: String): Int =
    if (style == "grid") R.string.tab_menu_style_grid else R.string.tab_menu_style_sheet

private fun exitConfirmationSummaryRes(value: String): Int = when (value) {
    "off" -> R.string.exit_confirmation_off
    "dialog" -> R.string.exit_confirmation_dialog
    else -> R.string.exit_confirmation_toast
}

@Composable
fun languageSummaryText(language: String): String {
    val option = languageOptions.firstOrNull { it.tag == language }
    return stringResource(option?.nameRes ?: R.string.language_system)
}

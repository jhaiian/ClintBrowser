package com.jhaiian.clint.settings.lookandfeel
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WebAsset

import com.jhaiian.clint.ui.ClintSwitch
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Web
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.common.RowDivider
import com.jhaiian.clint.settings.common.SettingsRow
import com.jhaiian.clint.settings.common.SettingsScreenScaffold
import com.jhaiian.clint.settings.common.SettingsSection
import com.jhaiian.clint.settings.common.SettingsSectionLabel
import com.jhaiian.clint.ui.ThemeSwatchUtils
import com.jhaiian.clint.ui.theme.LocalClintColors

@Composable
fun LookAndFeelScreen(
    state: LookAndFeelUiState,
    onThemeSelected: (String) -> Unit,
    onAccentSelected: (String) -> Unit,
    onCustomThemesClicked: () -> Unit,
    onIntensitySelected: (String) -> Unit,
    onWebFollowAppThemeRowClicked: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onAddressBarPositionSelected: (String) -> Unit,
    onMenuStyleSelected: (String) -> Unit,
    onScrollHideModeSelected: (String) -> Unit,
    onHideStatusBarRowClicked: () -> Unit,
    onHideSystemNavigationRowClicked: () -> Unit,
    onShowHomeButtonRowClicked: () -> Unit,
    onStripUrlSchemeRowClicked: () -> Unit,
    onShowTabStripRowClicked: () -> Unit,
    onTabStripPositionSelected: (String) -> Unit,
    onTabGroupsRowClicked: () -> Unit,
    onTabGroupBarPositionSelected: (String) -> Unit,
    onCustomizeMenuRowClicked: () -> Unit,
    onCustomizeToolbarRowClicked: () -> Unit,
    onExitConfirmationConfirmed: (String) -> Unit,
    onBackCloseTabSelected: (String) -> Unit,
    onPopupAlertStyleSelected: (String) -> Unit
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
                LookAndFeelDialog.SCROLL_HIDE_MODE -> ScrollHideModeDialog(
                    current = state.scrollHideMode, addressBarPosition = state.addressBarPosition, theme = state.theme, accent = state.accent,
                    hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation, onSelect = onScrollHideModeSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.EXIT_CONFIRMATION -> ExitConfirmationDialog(
                    current = state.exitConfirmation, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onConfirm = onExitConfirmationConfirmed, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.BACK_CLOSE_TAB -> BackCloseTabDialog(
                    current = state.backCloseTab, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onBackCloseTabSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.TAB_STRIP_POSITION -> TabBarPositionDialog(
                    title = stringResource(R.string.pref_tab_strip_position_title),
                    current = state.tabStripPosition, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onTabStripPositionSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.TAB_GROUP_BAR_POSITION -> TabBarPositionDialog(
                    title = stringResource(R.string.pref_tab_group_bar_position_title),
                    current = state.tabGroupBarPosition, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onTabGroupBarPositionSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.POPUP_ALERT_STYLE -> PopupAlertStyleDialog(
                    current = state.popupAlertStyle, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onPopupAlertStyleSelected, onDismiss = { state.openDialog = null }
                )
                LookAndFeelDialog.LANGUAGE -> LanguageSelectorDialog(
                    current = state.language, hideStatusBar = state.hideStatusBar, hideSystemNavigation = state.hideSystemNavigation,
                    onSelect = onLanguageSelected, onDismiss = { state.openDialog = null }
                )
                null -> {}
            }
        }
    ) {
        SettingsSectionLabel(stringResource(R.string.pref_category_language))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Translate,
                title = stringResource(R.string.pref_language_title),
                summary = languageSummaryText(state.language),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.LANGUAGE }
            )
        }

        SettingsSectionLabel(stringResource(R.string.pref_category_appearance))
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
                summary = if (com.jhaiian.clint.customtheme.isCustomAccent(state.accent)) {
                    com.jhaiian.clint.customtheme.customAccentLabel(androidx.compose.ui.platform.LocalContext.current, state.accent)
                } else {
                    stringResource(accentSummaryRes(state.accent))
                },
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.ACCENT }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.ColorLens,
                title = stringResource(R.string.custom_themes_title),
                summary = stringResource(R.string.custom_themes_row_summary),
                colors = colors,
                onClick = onCustomThemesClicked
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
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Web,
                title = stringResource(R.string.pref_web_follow_app_theme_title),
                summary = stringResource(R.string.pref_web_follow_app_theme_summary),
                colors = colors,
                onClick = onWebFollowAppThemeRowClicked,
                trailing = {
                    ClintSwitch(checked = state.webFollowAppTheme)
                }
            )
        }

        SettingsSectionLabel(stringResource(R.string.pref_category_address_bar))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.WebAsset,
                title = stringResource(R.string.pref_address_bar_position_title),
                summary = stringResource(addressBarPositionSummaryRes(state.addressBarPosition)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.ADDRESS_BAR_POSITION }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.SwapVert,
                title = stringResource(R.string.pref_nested_scroll_title),
                summary = stringResource(nestedScrollSummaryRes(state.scrollHideMode)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.SCROLL_HIDE_MODE }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Home,
                title = stringResource(R.string.pref_show_home_button_title),
                summary = stringResource(R.string.pref_show_home_button_summary),
                colors = colors,
                onClick = onShowHomeButtonRowClicked,
                trailing = {
                    ClintSwitch(checked = state.showHomeButton)
                }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Link,
                title = stringResource(R.string.pref_strip_url_scheme_title),
                summary = stringResource(R.string.pref_strip_url_scheme_summary),
                colors = colors,
                onClick = onStripUrlSchemeRowClicked,
                trailing = {
                    ClintSwitch(checked = state.stripUrlScheme)
                }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Reorder,
                title = stringResource(R.string.toolbar_customization_title),
                summary = stringResource(R.string.toolbar_customization_settings_summary),
                colors = colors,
                onClick = onCustomizeToolbarRowClicked
            )
        }

        SettingsSectionLabel(stringResource(R.string.pref_category_tabs))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Tab,
                title = stringResource(R.string.pref_tab_strip_title),
                summary = stringResource(R.string.pref_tab_strip_summary),
                colors = colors,
                onClick = onShowTabStripRowClicked,
                trailing = {
                    ClintSwitch(checked = state.showTabStrip)
                }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.VerticalAlignTop,
                title = stringResource(R.string.pref_tab_strip_position_title),
                summary = stringResource(tabBarPositionSummaryRes(state.tabStripPosition)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.TAB_STRIP_POSITION }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Workspaces,
                title = stringResource(R.string.pref_tab_groups_title),
                summary = stringResource(R.string.pref_tab_groups_summary),
                colors = colors,
                onClick = onTabGroupsRowClicked,
                trailing = {
                    ClintSwitch(checked = state.tabGroupsEnabled)
                }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Layers,
                title = stringResource(R.string.pref_tab_group_bar_position_title),
                summary = stringResource(tabBarPositionSummaryRes(state.tabGroupBarPosition)),
                colors = colors,
                enabled = state.tabGroupsEnabled,
                onClick = { if (state.tabGroupsEnabled) state.openDialog = LookAndFeelDialog.TAB_GROUP_BAR_POSITION }
            )
        }

        SettingsSectionLabel(stringResource(R.string.menu))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.MoreVert,
                title = stringResource(R.string.pref_menu_style_title),
                summary = stringResource(menuStyleSummaryRes(state.menuStyle)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.MENU_STYLE }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Reorder,
                title = stringResource(R.string.menu_customization_title),
                summary = stringResource(R.string.menu_customization_settings_summary),
                colors = colors,
                onClick = onCustomizeMenuRowClicked
            )
        }

        SettingsSectionLabel(stringResource(R.string.pref_category_fullscreen))
        SettingsSection(colors.cardBackground) {
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

        SettingsSectionLabel(stringResource(R.string.pref_category_navigation))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Close,
                title = stringResource(R.string.exit_confirmation_title),
                summary = stringResource(exitConfirmationSummaryRes(state.exitConfirmation)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.EXIT_CONFIRMATION }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Tab,
                title = stringResource(R.string.back_close_tab_title),
                summary = stringResource(backCloseTabSummaryRes(state.backCloseTab)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.BACK_CLOSE_TAB }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Layers,
                title = stringResource(R.string.pref_popup_alert_style_title),
                summary = stringResource(popupAlertStyleSummaryRes(state.popupAlertStyle)),
                colors = colors,
                onClick = { state.openDialog = LookAndFeelDialog.POPUP_ALERT_STYLE }
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

private fun tabBarPositionSummaryRes(position: String): Int = when (position) {
    com.jhaiian.clint.tabs.TabBarPlacement.TOP -> R.string.address_bar_position_top
    com.jhaiian.clint.tabs.TabBarPlacement.BOTTOM -> R.string.address_bar_position_bottom
    else -> R.string.tab_bar_position_follow
}

private fun addressBarPositionSummaryRes(position: String): Int = when (position) {
    "top" -> R.string.address_bar_position_top
    "bottom" -> R.string.address_bar_position_bottom
    else -> R.string.address_bar_position_split
}

private fun menuStyleSummaryRes(style: String): Int =
    if (style == "bottom_sheet") R.string.menu_style_bottom_sheet else R.string.menu_style_popup

private fun exitConfirmationSummaryRes(value: String): Int = when (value) {
    "off" -> R.string.exit_confirmation_off
    "dialog" -> R.string.exit_confirmation_dialog
    else -> R.string.exit_confirmation_toast
}

private fun backCloseTabSummaryRes(value: String): Int = when (value) {
    "any" -> R.string.back_close_tab_any
    "never" -> R.string.back_close_tab_never
    else -> R.string.back_close_tab_opener
}

private fun popupAlertStyleSummaryRes(value: String): Int =
    if (value == "snackbar") R.string.popup_alert_style_snackbar else R.string.popup_alert_style_dialog

@Composable
fun languageSummaryText(language: String): String {
    val option = languageOptions.firstOrNull { it.tag == language }
    return stringResource(option?.nameRes ?: R.string.language_system)
}

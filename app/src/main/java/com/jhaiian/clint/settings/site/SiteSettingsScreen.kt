package com.jhaiian.clint.settings.site
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.common.RowDivider
import com.jhaiian.clint.settings.common.SettingsRow
import com.jhaiian.clint.settings.common.SettingsScreenScaffold
import com.jhaiian.clint.settings.common.SettingsSection
import com.jhaiian.clint.settings.desktopmode.DesktopModeActivity
import com.jhaiian.clint.settings.sitepermissions.SitePermissionActivity
import com.jhaiian.clint.setup.SectionLabel
import com.jhaiian.clint.ui.theme.LocalClintColors

@Composable
fun SiteSettingsScreen(
    state: SiteSettingsUiState,
    onCameraClick: () -> Unit,
    onMicClick: () -> Unit,
    onLocationClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onClipboardClick: () -> Unit,
    onDesktopModeClick: () -> Unit,
    onQuiverGuardClick: () -> Unit
) {
    val colors = LocalClintColors.current

    SettingsScreenScaffold {
        SectionLabel(stringResource(R.string.site_section_permissions), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Videocam,
                title = stringResource(R.string.site_settings_camera),
                summary = stringResource(permissionSummaryRes(state.cameraBehavior)),
                colors = colors,
                onClick = onCameraClick
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Mic,
                title = stringResource(R.string.site_settings_mic),
                summary = stringResource(permissionSummaryRes(state.micBehavior)),
                colors = colors,
                onClick = onMicClick
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.LocationOn,
                title = stringResource(R.string.site_settings_location),
                summary = stringResource(permissionSummaryRes(state.locationBehavior)),
                colors = colors,
                onClick = onLocationClick
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Notifications,
                title = stringResource(R.string.site_settings_notifications),
                summary = stringResource(permissionSummaryRes(state.notificationsBehavior)),
                colors = colors,
                onClick = onNotificationsClick
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.ContentPaste,
                title = stringResource(R.string.site_settings_clipboard),
                summary = stringResource(permissionSummaryRes(state.clipboardBehavior)),
                colors = colors,
                onClick = onClipboardClick
            )
        }

        SectionLabel(stringResource(R.string.site_section_desktop_mode), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.DesktopWindows,
                title = stringResource(R.string.site_settings_desktop_mode),
                summary = stringResource(
                    if (state.desktopModeSaveState == DesktopModeActivity.VALUE_DO_NOT_SAVE)
                        R.string.desktop_mode_do_not_save_state
                    else
                        R.string.desktop_mode_save_state
                ),
                colors = colors,
                onClick = onDesktopModeClick
            )
        }

        SectionLabel(stringResource(R.string.site_section_quiver_guard), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Shield,
                title = stringResource(R.string.site_settings_quiver_guard),
                summary = stringResource(R.string.site_settings_quiver_guard_summary),
                colors = colors,
                onClick = onQuiverGuardClick
            )
        }
    }
}

private fun permissionSummaryRes(behavior: String): Int = when (behavior) {
    SitePermissionActivity.PREF_VALUE_DENY -> R.string.site_permission_always_deny
    SitePermissionActivity.PREF_VALUE_ALLOW -> R.string.site_permission_always_allow
    else -> R.string.site_permission_ask_first
}

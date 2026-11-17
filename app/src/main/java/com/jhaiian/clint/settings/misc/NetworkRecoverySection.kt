package com.jhaiian.clint.settings.misc

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.network.BrowserEngineRestart
import com.jhaiian.clint.settings.common.RowDivider
import com.jhaiian.clint.settings.common.SettingsRow
import com.jhaiian.clint.settings.common.SettingsSection
import com.jhaiian.clint.settings.common.SettingsSectionLabel
import com.jhaiian.clint.ui.ClintSwitch
import com.jhaiian.clint.ui.theme.LocalClintColors

@Composable
fun NetworkRecoverySection() {
    val colors = LocalClintColors.current
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var recovery by remember {
        mutableStateOf(
            prefs.getBoolean(BrowserEngineRestart.PREF_NETWORK_SWITCH_RECOVERY, BrowserEngineRestart.DEFAULT_NETWORK_SWITCH_RECOVERY)
        )
    }
    SettingsSectionLabel(stringResource(R.string.network_switch_section))
    SettingsSection(colors.cardBackground) {
        SettingsRow(
            icon = Icons.Filled.SyncProblem,
            title = stringResource(R.string.network_switch_recovery_title),
            summary = stringResource(R.string.network_switch_recovery_summary),
            colors = colors,
            onClick = {
                recovery = !recovery
                prefs.edit().putBoolean(BrowserEngineRestart.PREF_NETWORK_SWITCH_RECOVERY, recovery).apply()
            },
            trailing = { ClintSwitch(checked = recovery) }
        )
        RowDivider(colors.divider)
        SettingsRow(
            icon = Icons.Filled.RestartAlt,
            title = stringResource(R.string.network_switch_restart_title),
            summary = stringResource(R.string.network_switch_restart_summary),
            colors = colors,
            onClick = { (context as? Activity)?.let { BrowserEngineRestart.relaunch(it) } }
        )
    }
}

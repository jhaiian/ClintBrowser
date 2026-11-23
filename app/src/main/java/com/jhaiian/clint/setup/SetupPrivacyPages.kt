package com.jhaiian.clint.setup

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jhaiian.clint.R

@Composable
internal fun SetupPrivacyPage(state: SetupUiState, metrics: PageMetrics, scrollState: ScrollState) {
    val haptics = rememberSetupHaptics()
    SetupPage(metrics, scrollState, hero = { PrivacyHero(it) }) {
        PageHeader(stringResource(R.string.setup_privacy_title), stringResource(R.string.setup_privacy_body), metrics)
        Spacer(Modifier.height(24.dp))
        SetupToggleCard(
            icon = Icons.Outlined.Cookie,
            title = stringResource(R.string.block_third_party_cookies),
            body = stringResource(R.string.block_third_party_cookies_summary),
            checked = state.blockThirdPartyCookies,
            onCheckedChange = {
                haptics.toggle(it)
                state.blockThirdPartyCookies = it
            },
            modifier = Modifier.rise(2)
        )
        Spacer(Modifier.height(22.dp))
        SectionTitle(stringResource(R.string.setup_section_anti_tracking).uppercase(), metrics, Modifier.rise(3))
        Spacer(Modifier.height(10.dp))
        SetupToggleCard(
            icon = Icons.Outlined.LinkOff,
            title = stringResource(R.string.strip_tracking_params_title),
            body = stringResource(R.string.strip_tracking_params_summary),
            checked = state.stripTrackingParams,
            onCheckedChange = {
                haptics.toggle(it)
                state.stripTrackingParams = it
            },
            modifier = Modifier.rise(4)
        )
        Spacer(Modifier.height(10.dp))
        SetupToggleCard(
            icon = Icons.Outlined.PrivacyTip,
            title = stringResource(R.string.global_privacy_control_title),
            body = stringResource(R.string.global_privacy_control_summary),
            checked = state.globalPrivacyControl,
            onCheckedChange = {
                haptics.toggle(it)
                state.globalPrivacyControl = it
            },
            modifier = Modifier.rise(5)
        )
        Spacer(Modifier.height(10.dp))
        SetupToggleCard(
            icon = Icons.Outlined.VisibilityOff,
            title = stringResource(R.string.do_not_track_title),
            body = stringResource(R.string.do_not_track_summary),
            checked = state.doNotTrack,
            onCheckedChange = {
                haptics.toggle(it)
                state.doNotTrack = it
            },
            modifier = Modifier.rise(6)
        )
    }
}

@Composable
internal fun SetupSecurityPage(state: SetupUiState, metrics: PageMetrics, scrollState: ScrollState) {
    val haptics = rememberSetupHaptics()
    SetupPage(metrics, scrollState, hero = { SecurityHero(it) }) {
        PageHeader(stringResource(R.string.setup_security_title), stringResource(R.string.setup_security_body), metrics)
        Spacer(Modifier.height(24.dp))
        SetupToggleCard(
            icon = Icons.Outlined.Lock,
            title = stringResource(R.string.https_only),
            body = stringResource(R.string.https_only_summary),
            checked = state.httpsOnly,
            onCheckedChange = {
                haptics.toggle(it)
                state.httpsOnly = it
            },
            modifier = Modifier.rise(2)
        )
        Spacer(Modifier.height(22.dp))
        SectionTitle(stringResource(R.string.setup_section_more_protection).uppercase(), metrics, Modifier.rise(3))
        Spacer(Modifier.height(10.dp))
        SetupToggleCard(
            icon = Icons.Outlined.Router,
            title = stringResource(R.string.webrtc_protection_title),
            body = stringResource(R.string.webrtc_protection_summary),
            checked = state.webRtcProtection,
            onCheckedChange = {
                haptics.toggle(it)
                state.webRtcProtection = it
            },
            modifier = Modifier.rise(4)
        )
        Spacer(Modifier.height(10.dp))
        SetupToggleCard(
            icon = Icons.Outlined.DeleteSweep,
            title = stringResource(R.string.clear_on_exit_title),
            body = stringResource(R.string.clear_on_exit_summary),
            checked = state.clearOnExit,
            onCheckedChange = {
                haptics.toggle(it)
                state.clearOnExit = it
            },
            modifier = Modifier.rise(5)
        )
        Spacer(Modifier.height(10.dp))
        SetupToggleCard(
            icon = Icons.Outlined.ScreenLockPortrait,
            title = stringResource(R.string.screenshot_blocking_title),
            body = stringResource(R.string.screenshot_blocking_summary),
            checked = state.screenshotBlocking,
            onCheckedChange = {
                haptics.toggle(it)
                state.screenshotBlocking = it
            },
            modifier = Modifier.rise(6)
        )
    }
}

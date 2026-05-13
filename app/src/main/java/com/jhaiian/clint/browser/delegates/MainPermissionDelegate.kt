package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.MainActivity

import android.Manifest
import android.view.LayoutInflater
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
import com.jhaiian.clint.settings.sitepermissions.SitePermissionActivity

private fun originFromRequest(request: PermissionRequest): String =
    request.origin?.host?.takeIf { it.isNotEmpty() }
        ?: request.origin?.toString()
        ?: ""

private fun originFromString(origin: String): String =
    android.net.Uri.parse(origin).host?.takeIf { it.isNotEmpty() } ?: origin

internal fun MainActivity.isSystemPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun MainActivity.showWebPermissionDialog(
    title: String,
    message: String,
    isIncognito: Boolean,
    onAllow: (remember: Boolean) -> Unit,
    onDeny: (remember: Boolean) -> Unit
) {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_web_permission, null)
    view.findViewById<TextView>(R.id.tvWebPermissionMessage).text = message
    val checkRemember = view.findViewById<CheckBox>(R.id.checkWebPermissionRemember)
    if (isIncognito) checkRemember.visibility = View.GONE
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(title)
        .setView(view)
        .setNegativeButton(getString(R.string.action_deny)) { _, _ ->
            onDeny(checkRemember.isChecked)
        }
        .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
            onAllow(checkRemember.isChecked)
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

internal fun MainActivity.onWebPermissionRequest(request: PermissionRequest) {
    val resources = request.resources
    val wantsCamera = PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources
    val wantsMic = PermissionRequest.RESOURCE_AUDIO_CAPTURE in resources

    if (!wantsCamera && !wantsMic) {
        request.deny()
        return
    }

    val isIncognito = tabManager.activeTab?.isIncognito == true
    val origin = originFromRequest(request)

    if (wantsCamera && !wantsMic) {
        if (!isIncognito) {
            val stored = SitePermissionManager.getState(this, origin, SitePermissionDatabase.TYPE_CAMERA)
            if (stored == SitePermissionDatabase.STATE_ALLOW) {
                if (isSystemPermissionGranted(Manifest.permission.CAMERA)) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                } else {
                    pendingWebPermissionRequest = request
                    webCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                return
            }
            if (stored == SitePermissionDatabase.STATE_DENY) {
                request.deny()
                return
            }
        }
        if (isSystemPermissionGranted(Manifest.permission.CAMERA)) {
            if (!isIncognito) {
                val globalDefault = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("site_perm_default_${SitePermissionDatabase.TYPE_CAMERA}", SitePermissionActivity.PREF_VALUE_ASK) ?: SitePermissionActivity.PREF_VALUE_ASK
                when (globalDefault) {
                    SitePermissionActivity.PREF_VALUE_ALLOW -> { request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)); return }
                    SitePermissionActivity.PREF_VALUE_DENY -> { request.deny(); return }
                    else -> {}
                }
            }
            showWebCameraDialog(request)
        } else {
            pendingWebPermissionRequest = request
            val needsRationale = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            if (needsRationale) {
                MaterialAlertDialogBuilder(this, getDialogTheme())
                    .setTitle(getString(R.string.camera_permission_title))
                    .setMessage(getString(R.string.camera_permission_message))
                    .setNegativeButton(getString(R.string.action_deny)) { _, _ ->
                        pendingWebPermissionRequest?.deny()
                        pendingWebPermissionRequest = null
                    }
                    .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
                        webCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .create().also { applyStatusBarFlagToDialog(it) }.show()
            } else {
                webCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        return
    }

    if (wantsMic && !wantsCamera) {
        if (!isIncognito) {
            val stored = SitePermissionManager.getState(this, origin, SitePermissionDatabase.TYPE_MIC)
            if (stored == SitePermissionDatabase.STATE_ALLOW) {
                if (isSystemPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    pendingWebMicPermissionRequest = request
                    webMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                return
            }
            if (stored == SitePermissionDatabase.STATE_DENY) {
                request.deny()
                return
            }
        }
        if (isSystemPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            if (!isIncognito) {
                val globalDefault = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("site_perm_default_${SitePermissionDatabase.TYPE_MIC}", SitePermissionActivity.PREF_VALUE_ASK) ?: SitePermissionActivity.PREF_VALUE_ASK
                when (globalDefault) {
                    SitePermissionActivity.PREF_VALUE_ALLOW -> { request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)); return }
                    SitePermissionActivity.PREF_VALUE_DENY -> { request.deny(); return }
                    else -> {}
                }
            }
            showWebMicDialog(request)
        } else {
            pendingWebMicPermissionRequest = request
            val needsRationale = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            if (needsRationale) {
                MaterialAlertDialogBuilder(this, getDialogTheme())
                    .setTitle(getString(R.string.voice_search_permission_title))
                    .setMessage(getString(R.string.voice_search_permission_message))
                    .setNegativeButton(getString(R.string.action_deny)) { _, _ ->
                        pendingWebMicPermissionRequest?.deny()
                        pendingWebMicPermissionRequest = null
                    }
                    .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
                        webMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    .create().also { applyStatusBarFlagToDialog(it) }.show()
            } else {
                webMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        return
    }

    if (!isIncognito) {
        val cameraStored = SitePermissionManager.getState(this, origin, SitePermissionDatabase.TYPE_CAMERA)
        val micStored = SitePermissionManager.getState(this, origin, SitePermissionDatabase.TYPE_MIC)
        if (cameraStored != null && micStored != null) {
            val toGrant = mutableListOf<String>()
            if (cameraStored == SitePermissionDatabase.STATE_ALLOW && isSystemPermissionGranted(Manifest.permission.CAMERA))
                toGrant.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            if (micStored == SitePermissionDatabase.STATE_ALLOW && isSystemPermissionGranted(Manifest.permission.RECORD_AUDIO))
                toGrant.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            if (toGrant.isEmpty()) request.deny() else request.grant(toGrant.toTypedArray())
            return
        }
    }

    val cameraOk = isSystemPermissionGranted(Manifest.permission.CAMERA)
    val micOk = isSystemPermissionGranted(Manifest.permission.RECORD_AUDIO)
    if (cameraOk && micOk) {
        showWebCameraAndMicDialog(request, origin)
    } else {
        request.deny()
    }
}

internal fun MainActivity.showWebCameraDialog(request: PermissionRequest) {
    val isIncognito = tabManager.activeTab?.isIncognito == true
    val rawOrigin = originFromRequest(request)
    val origin = rawOrigin.ifEmpty { getString(R.string.camera_web_request_origin_unknown) }
    showWebPermissionDialog(
        title = getString(R.string.camera_web_request_title),
        message = getString(R.string.camera_web_request_message, origin),
        isIncognito = isIncognito,
        onAllow = { remember ->
            if (remember && !isIncognito) SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_CAMERA, SitePermissionDatabase.STATE_ALLOW)
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        },
        onDeny = { remember ->
            if (remember && !isIncognito) SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_CAMERA, SitePermissionDatabase.STATE_DENY)
            request.deny()
        }
    )
}

internal fun MainActivity.showWebMicDialog(request: PermissionRequest) {
    val isIncognito = tabManager.activeTab?.isIncognito == true
    val rawOrigin = originFromRequest(request)
    val origin = rawOrigin.ifEmpty { getString(R.string.mic_web_request_origin_unknown) }
    showWebPermissionDialog(
        title = getString(R.string.mic_web_request_title),
        message = getString(R.string.mic_web_request_message, origin),
        isIncognito = isIncognito,
        onAllow = { remember ->
            if (remember && !isIncognito) SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_MIC, SitePermissionDatabase.STATE_ALLOW)
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        },
        onDeny = { remember ->
            if (remember && !isIncognito) SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_MIC, SitePermissionDatabase.STATE_DENY)
            request.deny()
        }
    )
}

private fun MainActivity.showWebCameraAndMicDialog(request: PermissionRequest, rawOrigin: String) {
    val isIncognito = tabManager.activeTab?.isIncognito == true
    val origin = rawOrigin.ifEmpty { getString(R.string.camera_web_request_origin_unknown) }
    showWebPermissionDialog(
        title = getString(R.string.camera_web_request_title),
        message = getString(R.string.camera_web_request_message, origin),
        isIncognito = isIncognito,
        onAllow = { remember ->
            if (remember && !isIncognito) {
                SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_CAMERA, SitePermissionDatabase.STATE_ALLOW)
                SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_MIC, SitePermissionDatabase.STATE_ALLOW)
            }
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        },
        onDeny = { remember ->
            if (remember && !isIncognito) {
                SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_CAMERA, SitePermissionDatabase.STATE_DENY)
                SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_MIC, SitePermissionDatabase.STATE_DENY)
            }
            request.deny()
        }
    )
}

internal fun MainActivity.onWebGeolocationRequest(origin: String, callback: GeolocationPermissions.Callback) {
    val isIncognito = tabManager.activeTab?.isIncognito == true
    val rawOrigin = originFromString(origin)

    if (!isIncognito) {
        val stored = SitePermissionManager.getState(this, rawOrigin, SitePermissionDatabase.TYPE_LOCATION)
        if (stored == SitePermissionDatabase.STATE_ALLOW) {
            if (isSystemPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                isSystemPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                callback.invoke(origin, true, false)
            } else {
                pendingWebGeoOrigin = origin
                pendingWebGeoCallback = callback
                webLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            return
        }
        if (stored == SitePermissionDatabase.STATE_DENY) {
            callback.invoke(origin, false, false)
            return
        }
    }

    val locationGranted = isSystemPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        isSystemPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (locationGranted) {
        if (!isIncognito) {
            val globalDefault = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("site_perm_default_${SitePermissionDatabase.TYPE_LOCATION}", SitePermissionActivity.PREF_VALUE_ASK) ?: SitePermissionActivity.PREF_VALUE_ASK
            when (globalDefault) {
                SitePermissionActivity.PREF_VALUE_ALLOW -> { callback.invoke(origin, true, false); return }
                SitePermissionActivity.PREF_VALUE_DENY -> { callback.invoke(origin, false, false); return }
                else -> {}
            }
        }
        showWebLocationDialog(origin, callback)
    } else {
        pendingWebGeoOrigin = origin
        pendingWebGeoCallback = callback
        val needsRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        if (needsRationale) {
            MaterialAlertDialogBuilder(this, getDialogTheme())
                .setTitle(getString(R.string.location_permission_title))
                .setMessage(getString(R.string.location_permission_message))
                .setNegativeButton(getString(R.string.action_deny)) { _, _ ->
                    pendingWebGeoCallback?.invoke(pendingWebGeoOrigin ?: "", false, false)
                    pendingWebGeoOrigin = null
                    pendingWebGeoCallback = null
                }
                .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
                    webLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                .create().also { applyStatusBarFlagToDialog(it) }.show()
        } else {
            webLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

internal fun MainActivity.showWebLocationDialog(origin: String, callback: GeolocationPermissions.Callback) {
    val isIncognito = tabManager.activeTab?.isIncognito == true
    val rawOrigin = originFromString(origin)
    val displayOrigin = rawOrigin.ifEmpty { getString(R.string.location_web_request_origin_unknown) }
    showWebPermissionDialog(
        title = getString(R.string.location_web_request_title),
        message = getString(R.string.location_web_request_message, displayOrigin),
        isIncognito = isIncognito,
        onAllow = { remember ->
            if (remember && !isIncognito) SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_LOCATION, SitePermissionDatabase.STATE_ALLOW)
            callback.invoke(origin, true, false)
        },
        onDeny = { remember ->
            if (remember && !isIncognito) SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_LOCATION, SitePermissionDatabase.STATE_DENY)
            callback.invoke(origin, false, false)
        }
    )
}

package com.jhaiian.clint.browser

internal fun MainActivity.onShowFileChooser(
    callback: android.webkit.ValueCallback<Array<android.net.Uri>>,
    params: android.webkit.WebChromeClient.FileChooserParams
): Boolean {
    filePathCallback?.onReceiveValue(null)
    filePathCallback = null

    val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        this, android.Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (!hasCameraPermission) {
        pendingFileChooserCallback = callback
        pendingFileChooserParams = params
        if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this, getDialogTheme())
                .setTitle(getString(com.jhaiian.clint.R.string.camera_permission_title))
                .setMessage(getString(com.jhaiian.clint.R.string.camera_permission_message))
                .setCancelable(false)
                .setNegativeButton(getString(com.jhaiian.clint.R.string.action_not_now)) { _, _ ->
                    val cb = pendingFileChooserCallback
                    pendingFileChooserCallback = null
                    pendingFileChooserParams = null
                    cb?.onReceiveValue(null)
                }
                .setPositiveButton(getString(com.jhaiian.clint.R.string.action_allow)) { _, _ ->
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
                .create().also { applyStatusBarFlagToDialog(it) }.show()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
        return true
    }

    return launchFileChooser(callback, params)
}

internal fun MainActivity.launchFileChooser(
    callback: android.webkit.ValueCallback<Array<android.net.Uri>>,
    params: android.webkit.WebChromeClient.FileChooserParams
): Boolean {
    filePathCallback = callback

    val accept = params.acceptTypes?.joinToString(",") ?: "*/*"
    val isImageOnly = accept.contains("image") && !accept.contains("video") && !accept.contains("audio") && !accept.contains("*/*")
    val isVideoOnly = accept.contains("video") && !accept.contains("image") && !accept.contains("*/*")
    val allowMultiple = params.mode == android.webkit.WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

    val extraIntents = mutableListOf<android.content.Intent>()

    if (!isVideoOnly) {
        try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.TITLE, "clint_capture_${System.currentTimeMillis()}")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                cameraImageUri = uri
                extraIntents.add(android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                })
            }
        } catch (_: Exception) {}
    }

    if (!isImageOnly) {
        try { extraIntents.add(android.content.Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION)) } catch (_: Exception) {}
        try { extraIntents.add(android.content.Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)) } catch (_: Exception) {}
    }

    val mimeType = when {
        isImageOnly -> "image/*"
        isVideoOnly -> "video/*"
        accept.contains("audio") && !accept.contains("*/*") -> "audio/*"
        else -> "*/*"
    }

    val contentIntent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
        type = mimeType
        addCategory(android.content.Intent.CATEGORY_OPENABLE)
        if (allowMultiple) putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    val chooser = android.content.Intent.createChooser(contentIntent, null).apply {
        if (extraIntents.isNotEmpty()) putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS, extraIntents.toTypedArray())
    }

    try {
        fileChooserLauncher.launch(chooser)
    } catch (_: android.content.ActivityNotFoundException) {
        filePathCallback = null
        cameraImageUri = null
        cameraVideoUri = null
        return false
    }
    return true
}

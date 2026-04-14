package com.jhaiian.clint.browser

import android.view.View
import android.webkit.WebChromeClient
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams

internal fun MainActivity.onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
    if (fullscreenView != null) { callback.onCustomViewHidden(); return }
    fullscreenCallback = callback
    fullscreenView = view
    binding.fullscreenContainer.addView(view, android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT
    ))
    binding.fullscreenContainer.visibility = View.VISIBLE
    binding.toolbarTop.visibility = View.GONE
    binding.bottomBar.visibility = View.GONE
    val ctrl = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    ctrl.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    tabManager.activeTab?.webView?.evaluateJavascript(loadJsAsset("video_dimensions.js")) { result ->
        val parts = result?.trim('"')?.split(",")
        val vw = parts?.getOrNull(0)?.toIntOrNull() ?: 0
        val vh = parts?.getOrNull(1)?.toIntOrNull() ?: 0
        requestedOrientation = when {
            vw > 0 && vh > 0 && vw >= vh -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            vw > 0 && vh > 0 && vh > vw  -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else                          -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}

internal fun MainActivity.exitFullscreen() {
    fullscreenCallback?.onCustomViewHidden()
    fullscreenCallback = null
    fullscreenView?.let { binding.fullscreenContainer.removeView(it) }
    fullscreenView = null
    binding.fullscreenContainer.visibility = View.GONE
    barAnimator?.cancel()
    barsHidden = false
    nestedScrollActive = false
    if (topBarFullHeight > 0) binding.toolbarTop.updateLayoutParams { height = topBarFullHeight }
    binding.bottomBar.translationY = 0f
    binding.toolbarTop.visibility = View.VISIBLE
    binding.bottomBar.visibility = View.VISIBLE
    binding.swipeRefresh.isEnabled = true
    val ctrl = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    ctrl.show(WindowInsetsCompat.Type.navigationBars())
    applyStatusBarVisibility()
    binding.root.post {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        topBarFullHeight = 0
        bottomBarFullHeight = 0
        ViewCompat.requestApplyInsets(binding.toolbarTop)
        ViewCompat.requestApplyInsets(binding.bottomBar)
    }
}

private fun MainActivity.applyStatusBarVisibility() {
    val hide = prefs.getBoolean("hide_status_bar", false)
    val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    if (hide) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
        statusBarInsetPx = 0
        binding.toolbarTop.setPadding(0, 0, 0, 0)
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
        if (cachedStatusBarInsetPx > 0) {
            statusBarInsetPx = cachedStatusBarInsetPx
            binding.toolbarTop.setPadding(0, cachedStatusBarInsetPx, 0, 0)
        }
    }
}

package com.jhaiian.clint.browser

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.webkit.WebView
import android.animation.ValueAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

internal fun MainActivity.setupSwipeRefresh() {
    binding.swipeRefresh.canChildScrollUpCallback = {
        isYouTubeShorts() || run {
            val wv = tabManager.activeTab?.webView
            val mode = prefs.getString("scroll_hide_mode", "off") ?: "off"
            val barsHiddenByScrolling = !hasWebBottomNav && mode != "off" && topBarFraction >= 1f
            wv != null && (barsHiddenByScrolling || wv.canScrollVertically(-1) || nestedScrollActive)
        }
    }
    binding.swipeRefresh.apply {
        setColorSchemeColors(getThemeColor(com.google.android.material.R.attr.colorPrimary))
        setProgressBackgroundColorSchemeColor(getThemeColor(com.google.android.material.R.attr.colorSurface))
        setOnRefreshListener {
            nestedScrollActive = false
            tabManager.activeTab?.webView?.reload() ?: run { isRefreshing = false }
        }
    }
}

internal fun MainActivity.updateMainContentInsets() {
    val contentBarHeight = topBarFullHeight - statusBarInsetPx
    val visibleTop = statusBarInsetPx + ((1f - topBarFraction) * contentBarHeight).toInt().coerceAtLeast(0)
    val visibleBottom = ((1f - bottomBarFraction) * bottomBarFullHeight).toInt().coerceAtLeast(0)
    binding.mainContent.setPadding(0, visibleTop, 0, visibleBottom)
    val mode = prefs.getString("scroll_hide_mode", "off") ?: "off"
    val position = prefs.getString("address_bar_position", "top") ?: "top"
    val barsHidden = mode != "off" && when (mode) {
        "search_bar" -> if (position == "bottom") bottomBarFraction >= 1f else topBarFraction >= 1f
        "navigation_bar" -> bottomBarFraction >= 1f
        else -> topBarFraction >= 1f
    }
    binding.swipeRefresh.isEnabled = !barsHidden
}

internal fun MainActivity.setTopBarFraction(fraction: Float) {
    topBarFraction = fraction
    val contentBarHeight = (topBarFullHeight - statusBarInsetPx).toFloat()
    if (contentBarHeight > 0) {
        binding.toolbarTop.translationY = -fraction * contentBarHeight
    }
    updateMainContentInsets()
}

internal fun MainActivity.setBottomBarFraction(fraction: Float) {
    bottomBarFraction = fraction
    if (bottomBarFullHeight > 0) {
        binding.bottomBar.translationY = fraction * bottomBarFullHeight.toFloat()
        binding.toolbarBottom.translationY = fraction * bottomBarFullHeight.toFloat()
    }
    updateMainContentInsets()
}

internal fun MainActivity.animateBottomBarTo(targetFraction: Float, animated: Boolean = true) {
    bottomBarAnimator2?.cancel()
    if (!animated || (topBarFullHeight == 0 && bottomBarFullHeight == 0)) {
        setTopBarFraction(targetFraction)
        setBottomBarFraction(targetFraction)
        return
    }
    val startFraction = bottomBarFraction
    if (startFraction == targetFraction) return
    bottomBarAnimator2 = ValueAnimator.ofFloat(startFraction, targetFraction).apply {
        duration = 200L
        interpolator = if (targetFraction > startFraction) AccelerateInterpolator() else DecelerateInterpolator()
        addUpdateListener { anim ->
            val f = anim.animatedValue as Float
            setTopBarFraction(f)
            setBottomBarFraction(f)
        }
        start()
    }
}

internal fun MainActivity.animateTopBarOnlyTo(targetFraction: Float, animated: Boolean = true) {
    bottomBarAnimator2?.cancel()
    if (!animated || topBarFullHeight == 0) {
        setTopBarFraction(targetFraction)
        return
    }
    val startFraction = topBarFraction
    if (startFraction == targetFraction) return
    bottomBarAnimator2 = ValueAnimator.ofFloat(startFraction, targetFraction).apply {
        duration = 200L
        interpolator = if (targetFraction > startFraction) AccelerateInterpolator() else DecelerateInterpolator()
        addUpdateListener { anim ->
            val f = anim.animatedValue as Float
            setTopBarFraction(f)
        }
        start()
    }
}

internal fun MainActivity.animateBottomBarOnlyTo(targetFraction: Float, animated: Boolean = true) {
    bottomBarAnimator2?.cancel()
    if (!animated || bottomBarFullHeight == 0) {
        setBottomBarFraction(targetFraction)
        return
    }
    val startFraction = bottomBarFraction
    if (startFraction == targetFraction) return
    bottomBarAnimator2 = ValueAnimator.ofFloat(startFraction, targetFraction).apply {
        duration = 200L
        interpolator = if (targetFraction > startFraction) AccelerateInterpolator() else DecelerateInterpolator()
        addUpdateListener { anim ->
            val f = anim.animatedValue as Float
            setBottomBarFraction(f)
        }
        start()
    }
}

private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) { action() }
    })
}

internal fun MainActivity.attachScrollListener(webView: WebView) {
    var localVelocityTracker: VelocityTracker? = null

    val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val mode = prefs.getString("scroll_hide_mode", "off") ?: "off"
            if (mode != "off") {
                val position = prefs.getString("address_bar_position", "top") ?: "top"
                val refHeight = when (mode) {
                    "search_bar" -> if (position == "bottom") bottomBarFullHeight.takeIf { it > 0 } ?: topBarFullHeight else topBarFullHeight
                    else -> bottomBarFullHeight.takeIf { it > 0 } ?: topBarFullHeight
                }
                if (!hasWebBottomNav) {
                    if (refHeight > 0) {
                        val delta = distanceY / (refHeight * 1.5f)
                        when (mode) {
                            "search_bar" -> {
                                if (position == "bottom") {
                                    val newFrac = (bottomBarFraction + delta).coerceIn(0f, 1f)
                                    setBottomBarFraction(newFrac)
                                } else {
                                    val newFrac = (topBarFraction + delta).coerceIn(0f, 1f)
                                    setTopBarFraction(newFrac)
                                }
                            }
                            "navigation_bar" -> {
                                val newFrac = (bottomBarFraction + delta).coerceIn(0f, 1f)
                                setBottomBarFraction(newFrac)
                            }
                            "both" -> {
                                val newFrac = (bottomBarFraction + delta).coerceIn(0f, 1f)
                                setTopBarFraction(newFrac)
                                setBottomBarFraction(newFrac)
                            }
                        }
                    }
                } else if (refHeight > 0) {
                    val delta = distanceY / (refHeight * 1.5f)
                    when (mode) {
                        "search_bar" -> {
                            if (position == "bottom") {
                                val newFrac = (bottomBarFraction + delta).coerceIn(0f, 1f)
                                setBottomBarFraction(newFrac)
                            } else {
                                val newFrac = (topBarFraction + delta).coerceIn(0f, 1f)
                                setTopBarFraction(newFrac)
                            }
                        }
                        "navigation_bar" -> {
                            val newFrac = (bottomBarFraction + delta).coerceIn(0f, 1f)
                            setBottomBarFraction(newFrac)
                        }
                        "both" -> {
                            val newFrac = (bottomBarFraction + delta).coerceIn(0f, 1f)
                            setTopBarFraction(newFrac)
                            setBottomBarFraction(newFrac)
                        }
                    }
                }
            }
            return false
        }
    })

    webView.setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                localVelocityTracker?.recycle()
                localVelocityTracker = VelocityTracker.obtain()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val mode = prefs.getString("scroll_hide_mode", "off") ?: "off"
                if (mode != "off") {
                    localVelocityTracker?.computeCurrentVelocity(1000)
                    val vy = localVelocityTracker?.yVelocity ?: 0f
                    val position = prefs.getString("address_bar_position", "top") ?: "top"
                    if (!hasWebBottomNav) {
                        val currentFrac = when (mode) {
                            "search_bar" -> if (position == "bottom") bottomBarFraction else topBarFraction
                            else -> bottomBarFraction
                        }
                        val snapToHidden = when {
                            vy < -900f -> true
                            vy > 900f -> false
                            else -> currentFrac >= 0.5f
                        }
                        val target = if (snapToHidden) 1f else 0f
                        when (mode) {
                            "search_bar" -> {
                                if (position == "bottom") animateBottomBarOnlyTo(target)
                                else animateTopBarOnlyTo(target)
                            }
                            "navigation_bar" -> animateBottomBarOnlyTo(target)
                            "both" -> animateBottomBarTo(target)
                        }
                    } else {
                        val currentFrac = when (mode) {
                            "search_bar" -> if (position == "bottom") bottomBarFraction else topBarFraction
                            else -> bottomBarFraction
                        }
                        val snapToHidden = when {
                            vy < -900f -> true
                            vy > 900f -> false
                            else -> currentFrac >= 0.5f
                        }
                        val target = if (snapToHidden) 1f else 0f
                        when (mode) {
                            "search_bar" -> {
                                if (position == "bottom") animateBottomBarOnlyTo(target)
                                else animateTopBarOnlyTo(target)
                            }
                            "navigation_bar" -> animateBottomBarOnlyTo(target)
                            "both" -> animateBottomBarTo(target)
                        }
                    }
                }
                localVelocityTracker?.recycle()
                localVelocityTracker = null
            }
        }
        localVelocityTracker?.addMovement(event)
        detector.onTouchEvent(event)
        false
    }
}

internal fun MainActivity.injectScrollTracker(webView: WebView) {
    webView.evaluateJavascript(loadJsAsset("scroll_tracker.js"), null)
}

internal fun MainActivity.injectBottomNavDetector(webView: WebView) {
    webView.evaluateJavascript(loadJsAsset("bottom_nav_detector.js"), null)
}

internal fun MainActivity.isYouTubeShorts(): Boolean {
    val url = tabManager.activeTab?.webView?.url ?: return false
    return url.contains("youtube.com/shorts", ignoreCase = true)
}

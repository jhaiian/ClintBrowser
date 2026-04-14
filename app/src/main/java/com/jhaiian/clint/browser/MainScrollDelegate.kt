package com.jhaiian.clint.browser

import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import android.animation.ValueAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.view.updateLayoutParams


internal fun MainActivity.setupSwipeRefresh() {
    binding.swipeRefresh.canChildScrollUpCallback = {
        barsHidden || isYouTubeShorts() || run {
            val wv = tabManager.activeTab?.webView
            wv != null && (wv.canScrollVertically(-1) || nestedScrollActive)
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

internal fun MainActivity.animateBars(hide: Boolean, animated: Boolean) {
    if (hide == barsHidden) return
    if (hide && !prefs.getBoolean("hide_bars_on_scroll", true)) return
    if (topBarFullHeight == 0 || bottomBarFullHeight == 0) return
    barAnimator?.cancel()
    barsHidden = hide
    if (hide) binding.swipeRefresh.isEnabled = false
    val topFrom = if (hide) topBarFullHeight else statusBarInsetPx
    val topTo   = if (hide) statusBarInsetPx   else topBarFullHeight
    val botFrom = if (hide) bottomBarFullHeight else 0
    val botTo   = if (hide) 0                  else bottomBarFullHeight
    if (!animated) {
        binding.toolbarTop.updateLayoutParams { height = topTo }
        binding.bottomBar.updateLayoutParams { height = botTo }
        if (!hide) binding.swipeRefresh.isEnabled = true
        return
    }
    barAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = if (hide) 250L else 200L
        interpolator = if (hide) AccelerateInterpolator() else DecelerateInterpolator()
        addUpdateListener { anim ->
            val f = anim.animatedFraction
            binding.toolbarTop.updateLayoutParams { height = (topFrom + (topTo - topFrom) * f).toInt() }
            binding.bottomBar.updateLayoutParams { height = (botFrom + (botTo - botFrom) * f).toInt() }
        }
        start()
    }
}

internal fun MainActivity.attachScrollListener(webView: WebView) {
    val density = resources.displayMetrics.density
    val threshold = density * 40f
    var accumulated = 0f

    val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            accumulated += distanceY
            when {
                accumulated > threshold -> {
                    animateBars(hide = true, animated = true)
                    accumulated = 0f
                }
                accumulated < -threshold -> {
                    animateBars(hide = false, animated = true)
                    accumulated = 0f
                }
            }
            return false
        }
    })

    webView.setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> accumulated = 0f
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!barsHidden) binding.swipeRefresh.isEnabled = true
            }
        }
        detector.onTouchEvent(event)
        false
    }
}

internal fun MainActivity.injectScrollTracker(webView: WebView) {
    webView.evaluateJavascript(loadJsAsset("scroll_tracker.js"), null)
}

internal fun MainActivity.isYouTubeShorts(): Boolean {
    val url = tabManager.activeTab?.webView?.url ?: return false
    return url.contains("youtube.com/shorts", ignoreCase = true)
}

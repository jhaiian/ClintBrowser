package com.jhaiian.clint

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class ClintSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    var canChildScrollUpCallback: (() -> Boolean)? = null

    private var initialY = 0f
    private var gestureConsumed = false

    override fun canChildScrollUp(): Boolean {
        return canChildScrollUpCallback?.invoke() ?: super.canChildScrollUp()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialY = ev.y
                gestureConsumed = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - initialY
                if (dy > 0 && canChildScrollUp()) {
                    return false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureConsumed = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}

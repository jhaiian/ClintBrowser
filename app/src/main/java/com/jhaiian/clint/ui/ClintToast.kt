package com.jhaiian.clint.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.widget.ImageViewCompat
import com.jhaiian.clint.R
import com.jhaiian.clint.app.ClintApplication

object ClintToast {

    private const val FADE_MS = 180L
    private const val DISPLAY_MS = 2200L

    fun show(context: Context, message: String, @DrawableRes iconRes: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { show(context, message, iconRes) }
            return
        }
        val activity: Activity = when (context) {
            is Activity -> context
            else -> (context.applicationContext as? ClintApplication)?.currentActivity ?: return
        }
        if (activity.isFinishing || activity.isDestroyed) return
        val decor = activity.window.decorView as? ViewGroup ?: return
        val view = LayoutInflater.from(activity).inflate(R.layout.toast_custom, decor, false)
        val icon = view.findViewById<ImageView>(R.id.toast_icon)
        val text = view.findViewById<TextView>(R.id.toast_text)
        val ta = activity.obtainStyledAttributes(intArrayOf(R.attr.clintIconTint))
        val tint = ta.getColor(0, 0xFFFFFFFF.toInt())
        ta.recycle()
        icon.setImageResource(iconRes)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(tint))
        text.text = message
        val density = activity.resources.displayMetrics.density
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (96 * density).toInt()
        }
        view.layoutParams = params
        view.alpha = 0f
        decor.addView(view)
        view.animate().alpha(1f).setDuration(FADE_MS).start()
        Handler(Looper.getMainLooper()).postDelayed({
            if (view.isAttachedToWindow) {
                view.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                    if (view.isAttachedToWindow) decor.removeView(view)
                }.start()
            }
        }, DISPLAY_MS)
    }
}

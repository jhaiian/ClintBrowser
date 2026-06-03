package com.jhaiian.clint.downloads

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.R as AppCompatR
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class DownloadProgressCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var colorResolved = false

    private var progressFraction = 0f
    private var active = false
    private var indeterminate = false
    private var indeterminatePos = 0f

    private val indeterminateAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            indeterminatePos = it.animatedValue as Float
            if (indeterminate) invalidate()
        }
    }

    init {
        setWillNotDraw(false)
    }

    private fun resolveColor() {
        if (colorResolved) return
        val primary = MaterialColors.getColor(this, AppCompatR.attr.colorPrimary, 0)
        fillPaint.color = (primary and 0x00FFFFFF) or (0x40 shl 24)
        colorResolved = true
    }

    fun setDownloadProgress(percent: Int) {
        if (indeterminateAnimator.isRunning) indeterminateAnimator.cancel()
        indeterminate = false
        active = percent > 0
        progressFraction = percent / 100f
        invalidate()
    }

    fun setIndeterminate() {
        indeterminate = true
        active = true
        if (!indeterminateAnimator.isRunning) indeterminateAnimator.start()
        invalidate()
    }

    fun clearProgress() {
        if (indeterminateAnimator.isRunning) indeterminateAnimator.cancel()
        indeterminate = false
        active = false
        progressFraction = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (indeterminateAnimator.isRunning) indeterminateAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return
        resolveColor()

        val r = radius
        val w = width.toFloat()
        val h = height.toFloat()

        val clipPath = Path()
        clipPath.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clipPath)

        if (indeterminate) {
            val bandW = w * 0.45f
            val x = indeterminatePos * (w + bandW) - bandW
            canvas.drawRect(x, 0f, x + bandW, h, fillPaint)
        } else {
            canvas.drawRect(0f, 0f, w * progressFraction, h, fillPaint)
        }

        canvas.restore()
    }
}

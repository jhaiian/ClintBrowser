package com.jhaiian.clint.settings.sitepermissions

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

class SitePermissionFastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface SectionIndexer {
        fun getSectionLetter(position: Int): String
        fun getSectionItemCount(): Int
    }

    private val dp = resources.displayMetrics.density

    private val thumbW = 4f * dp
    private val thumbH = 44f * dp
    private val thumbPaddingEnd = 6f * dp
    private val trackPaddingV = 20f * dp
    private val bubbleRadius = 26f * dp
    private val bubbleGap = 8f * dp

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f * dp
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    var isInteractive = true

    private var isDragging = false
    private var thumbFraction = 0f
    private var currentLetter = ""

    private var attachedRv: RecyclerView? = null
    private var sectionIndexer: SectionIndexer? = null
    private var colorsInitialized = false

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (!isDragging) {
                thumbFraction = computeFraction(rv)
                invalidate()
            }
        }
    }

    private fun ensureColors() {
        if (colorsInitialized) return
        val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF6200EE.toInt())
        val onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF.toInt())
        thumbPaint.color = primary
        bubblePaint.color = primary
        textPaint.color = onPrimary
        colorsInitialized = true
    }

    private fun computeFraction(rv: RecyclerView): Float {
        val offset = rv.computeVerticalScrollOffset().toFloat()
        val range = rv.computeVerticalScrollRange().toFloat()
        val extent = rv.computeVerticalScrollExtent().toFloat()
        val scrollable = (range - extent).coerceAtLeast(1f)
        return (offset / scrollable).coerceIn(0f, 1f)
    }

    fun attach(rv: RecyclerView, indexer: SectionIndexer) {
        attachedRv?.removeOnScrollListener(scrollListener)
        attachedRv = rv
        sectionIndexer = indexer
        thumbFraction = computeFraction(rv)
        rv.addOnScrollListener(scrollListener)
        invalidate()
    }

    fun detach() {
        attachedRv?.removeOnScrollListener(scrollListener)
        attachedRv = null
        sectionIndexer = null
    }

    fun notifyDataChanged() {
        val rv = attachedRv ?: return
        thumbFraction = computeFraction(rv)
        invalidate()
    }

    private fun trackTop() = trackPaddingV + thumbH / 2f
    private fun trackBottom() = height - trackPaddingV - thumbH / 2f
    private fun trackRange() = (trackBottom() - trackTop()).coerceAtLeast(1f)
    private fun thumbCenterY() = trackTop() + thumbFraction * trackRange()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rv = attachedRv ?: return false
        if (!isInteractive) return false

        val cy = thumbCenterY()
        val hitLeft = width - thumbW - thumbPaddingEnd - 24f * dp
        val hitTop = cy - thumbH / 2f - 20f * dp
        val hitBottom = cy + thumbH / 2f + 20f * dp

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x >= hitLeft && event.y in hitTop..hitBottom) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    drag(event.y, rv)
                    true
                } else {
                    false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) { drag(event.y, rv); true } else false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun drag(touchY: Float, rv: RecyclerView) {
        val clamped = touchY.coerceIn(trackTop(), trackBottom())
        thumbFraction = (clamped - trackTop()) / trackRange()
        val idx = sectionIndexer ?: return
        val count = idx.getSectionItemCount()
        if (count == 0) return
        val pos = (thumbFraction * (count - 1)).toInt().coerceIn(0, count - 1)
        currentLetter = idx.getSectionLetter(pos)
        (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, 0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (height == 0) return
        val rv = attachedRv ?: return
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        if (range <= extent) return
        ensureColors()

        val cy = thumbCenterY()
        val thumbLeft = width - thumbW - thumbPaddingEnd
        val thumbRight = width - thumbPaddingEnd

        thumbPaint.alpha = if (isDragging) 255 else 160
        canvas.drawRoundRect(
            RectF(thumbLeft, cy - thumbH / 2f, thumbRight, cy + thumbH / 2f),
            thumbW / 2f, thumbW / 2f,
            thumbPaint
        )

        if (isDragging && isInteractive && currentLetter.isNotEmpty()) {
            val cx = thumbLeft - bubbleGap - bubbleRadius
            bubblePaint.alpha = 255
            canvas.drawCircle(cx, cy, bubbleRadius, bubblePaint)

            val tail = Path().apply {
                val baseX = cx + bubbleRadius - 2f * dp
                val tipX = thumbLeft - bubbleGap + 2f * dp
                moveTo(baseX, cy - 6f * dp)
                lineTo(tipX, cy)
                lineTo(baseX, cy + 6f * dp)
                close()
            }
            canvas.drawPath(tail, bubblePaint)

            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(currentLetter, cx, textY, textPaint)
        }
    }
}

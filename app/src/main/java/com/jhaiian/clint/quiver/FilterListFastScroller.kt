package com.jhaiian.clint.quiver

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

// Custom fast-scroll thumb drawn as an overlay on the right edge of the filter
// list RecyclerView. When the user drags the thumb, a section-letter bubble
// appears to the left of the thumb showing the first letter of the item at the
// current scroll position, matching the behaviour of the history fast scroller.
//
// The thumb colour is taken from the theme's colorPrimary at first draw time
// to respect dynamic colour and dark-mode changes.
class FilterListFastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Implemented by the adapter to provide the section letter for a given
    // adapter position and the total item count, used during drag to map
    // a touch fraction to a list position.
    interface SectionIndexer {
        fun getSectionLetter(position: Int): String
        fun getSectionItemCount(): Int

        // Adapters with content pinned before the indexed section (e.g. a fixed header row)
        // override this so drag() can convert a section-space position into the RecyclerView
        // position that actually holds it.
        fun getAdapterPositionOffset(): Int = 0
    }

    private val dp = resources.displayMetrics.density

    // Thumb dimensions and layout constants expressed in dp so they scale
    // correctly on all display densities.
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

    // When false, touch events are ignored so the thumb is display-only.
    // Set to false during date-sorted mode where letter sections are meaningless.
    var isInteractive = true

    private var isDragging = false
    // Fraction of the track height at which the thumb is positioned (0 = top, 1 = bottom).
    private var thumbFraction = 0f
    private var currentLetter = ""

    private var attachedRv: RecyclerView? = null
    private var sectionIndexer: SectionIndexer? = null
    // Deferred until first draw so the view is attached to the window and
    // can resolve theme attributes correctly.
    private var colorsInitialized = false

    // Passive scroll listener that keeps the thumb fraction in sync with the
    // list position without the user dragging.
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

    // Computes the current scroll fraction from the RecyclerView's scroll metrics.
    // Clamps to [0,1] to guard against edge cases where offset or range values
    // are inconsistent during layout passes.
    private fun computeFraction(rv: RecyclerView): Float {
        val offset = rv.computeVerticalScrollOffset().toFloat()
        val range = rv.computeVerticalScrollRange().toFloat()
        val extent = rv.computeVerticalScrollExtent().toFloat()
        val scrollable = (range - extent).coerceAtLeast(1f)
        return (offset / scrollable).coerceIn(0f, 1f)
    }

    // Registers this scroller against a RecyclerView and its adapter. Removes the
    // listener from any previously attached RecyclerView first to avoid leaks.
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

    // Called by the adapter after items are added, removed, or sorted to
    // recompute the thumb fraction and redraw.
    fun notifyDataChanged() {
        val rv = attachedRv ?: return
        thumbFraction = computeFraction(rv)
        invalidate()
    }

    // Track extents account for the padding at each end so the thumb does not
    // travel all the way to the top and bottom edges of the view.
    private fun trackTop() = trackPaddingV + thumbH / 2f
    private fun trackBottom() = height - trackPaddingV - thumbH / 2f
    private fun trackRange() = (trackBottom() - trackTop()).coerceAtLeast(1f)
    private fun thumbCenterY() = trackTop() + thumbFraction * trackRange()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rv = attachedRv ?: return false
        if (!isInteractive) return false

        val cy = thumbCenterY()
        // Expand the hit target beyond the visible thumb so small touches are
        // still registered correctly on the first ACTION_DOWN.
        val hitLeft = width - thumbW - thumbPaddingEnd - 24f * dp
        val hitTop = cy - thumbH / 2f - 20f * dp
        val hitBottom = cy + thumbH / 2f + 20f * dp

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x >= hitLeft && event.y in hitTop..hitBottom) {
                    isDragging = true
                    // Prevent the parent RecyclerView from intercepting the drag.
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
                    // Redraw to remove the letter bubble on release.
                    invalidate()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    // Maps the touch Y coordinate to a list position and scrolls the RecyclerView
    // to that position. Updates the letter displayed in the bubble from the section
    // indexer so the user can see which section they are scrolling to.
    private fun drag(touchY: Float, rv: RecyclerView) {
        val clamped = touchY.coerceIn(trackTop(), trackBottom())
        thumbFraction = (clamped - trackTop()) / trackRange()
        val idx = sectionIndexer ?: return
        val count = idx.getSectionItemCount()
        if (count == 0) return
        val pos = (thumbFraction * (count - 1)).toInt().coerceIn(0, count - 1)
        currentLetter = idx.getSectionLetter(pos)
        (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos + idx.getAdapterPositionOffset(), 0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (height == 0) return
        val rv = attachedRv ?: return

        // Do not draw the thumb when the content fits on one screen.
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        if (range <= extent) return
        ensureColors()

        val cy = thumbCenterY()
        val thumbLeft = width - thumbW - thumbPaddingEnd
        val thumbRight = width - thumbPaddingEnd

        // Reduce the thumb's alpha when not actively dragging so it is less
        // visually prominent during passive reading.
        thumbPaint.alpha = if (isDragging) 255 else 160
        canvas.drawRoundRect(
            RectF(thumbLeft, cy - thumbH / 2f, thumbRight, cy + thumbH / 2f),
            thumbW / 2f, thumbW / 2f,
            thumbPaint
        )

        // Show the section letter bubble only while actively dragging.
        if (isDragging && isInteractive && currentLetter.isNotEmpty()) {
            val cx = thumbLeft - bubbleGap - bubbleRadius
            bubblePaint.alpha = 255
            canvas.drawCircle(cx, cy, bubbleRadius, bubblePaint)

            // Draw a small triangular tail pointing toward the thumb to make
            // the bubble feel visually connected to it.
            val tail = Path().apply {
                val baseX = cx + bubbleRadius - 2f * dp
                val tipX = thumbLeft - bubbleGap + 2f * dp
                moveTo(baseX, cy - 6f * dp)
                lineTo(tipX, cy)
                lineTo(baseX, cy + 6f * dp)
                close()
            }
            canvas.drawPath(tail, bubblePaint)

            // Centre the letter vertically inside the bubble using the font metrics.
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(currentLetter, cx, textY, textPaint)
        }
    }
}

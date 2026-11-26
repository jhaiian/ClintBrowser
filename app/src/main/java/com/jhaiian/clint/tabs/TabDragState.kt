package com.jhaiian.clint.tabs

import android.os.SystemClock
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.unit.IntSize

internal fun tabItemKey(tabId: String): String = "tab:$tabId"
internal fun groupHeaderKey(groupId: String): String = "group:$groupId"
internal fun tabCardOriginKey(tabId: String): String = "card:$tabId"
internal fun groupCardOriginKey(groupId: String): String = "groupCard:$groupId"
internal fun groupChipKey(groupId: String): String = "chip:$groupId"
internal fun groupFillerKey(groupId: String, slot: Int): String = "filler:$groupId:$slot"
internal fun groupFooterKey(groupId: String): String = "footer:$groupId"
internal fun groupBeforeKey(groupId: String): String = "before:$groupId"
internal fun groupAfterKey(groupId: String): String = "after:$groupId"
internal fun tabBeforeKey(tabId: String): String = "tabBefore:$tabId"
internal fun tabAfterKey(tabId: String): String = "tabAfter:$tabId"
internal fun tabMergeKey(tabId: String): String = "merge:$tabId"
internal fun tabMergeHoldKey(tabId: String): String = "hold:$tabId"
internal const val END_DROP_KEY = "drop:end"
internal const val START_DROP_KEY = "drop:start"
private const val MERGE_DWELL_MS = 220L
// Merge zone: the centre box of a mergeable target (fraction of its width and height).
private const val ZONE_LO = 0.25f
private const val ZONE_HI = 0.75f
// Once a hold started, the finger may wander this much further out before it is lost.
private const val ZONE_KEEP_LO = 0.17f
private const val ZONE_KEEP_HI = 0.83f
// Past this on the far side the finger has clearly gone over the target, so it swaps.
private const val FAR_FRACTION = 0.75f
// Non-mergeable targets swap as soon as the finger is this far inside (small hysteresis).
private const val SWAP_ENTER_FRACTION = 0.1f
private const val SPEED_WINDOW_MS = 90L

internal fun mergeTargetTabId(key: Any?): String? =
    (key as? String)?.takeIf { it.startsWith("merge:") }?.removePrefix("merge:")

internal fun holdTargetTabId(key: Any?): String? =
    (key as? String)?.takeIf { it.startsWith("hold:") }?.removePrefix("hold:")

internal fun dropTargetGroupId(key: Any?): String? {
    val value = key as? String ?: return null
    return when {
        value.startsWith("group:") -> value.removePrefix("group:")
        value.startsWith("chip:") -> value.removePrefix("chip:")
        value.startsWith("filler:") -> value.removePrefix("filler:").substringBefore(':')
        else -> null
    }
}

@Stable
internal class TabDragState(private val gridState: LazyGridState) {
    var draggingId by mutableStateOf<String?>(null)
        private set
    var draggingGroupId by mutableStateOf<String?>(null)
        private set
    val active: Boolean get() = draggingId != null || draggingGroupId != null
    val dragDistance: Float get() = dragOffset.getDistance()
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var originOffset: Offset = Offset.Zero
        private set
    var originSize: IntSize = IntSize.Zero
        private set

    var hoverKey by mutableStateOf<Any?>(null)
        private set

    /** Mergeable tab under the finger, for drawing its merge zone / edge bar; null otherwise. */
    var zoneTabId by mutableStateOf<String?>(null)
        private set
    /** Edge bar on [zoneTabId]: -1 on its leading side, 1 on its trailing side, 0 none. */
    var zoneEdge by mutableStateOf(0)
        private set
    /** True when the edge bar is vertical (same-row neighbour in a grid). */
    var zoneEdgeVertical by mutableStateOf(true)
        private set
    /** uptimeMillis when the current merge hold started; 0 when none. */
    var holdSince by mutableStateOf(0L)
        private set
    val mergeDwellMs: Long get() = MERGE_DWELL_MS

    var gridOriginInRoot: Offset = Offset.Zero
    /**
     * LazyGrid reports item offsets without its content padding, but places items after it.
     * Add this to an item offset to get where the item is actually drawn inside the grid box.
     */
    var contentPaddingOffset: Offset = Offset.Zero
    val chipBounds = mutableMapOf<String, Rect>()
    val handleOrigins = mutableMapOf<String, Offset>()
    var collapsedGroupIds: Set<String> = emptySet()
    var columns: Int = 1
    var mergeEnabled: Boolean = true
    var canMergeWith: (String) -> Boolean = { true }
    var groupOfTab: (String) -> String? = { null }
    var primaryPointerId: PointerId? = null
    private var pointerStartInRoot: Offset = Offset.Zero
    /** Finger speed (px per ms) above which the drag counts as moving, not hovering. */
    var fastSpeedPxPerMs: Float = 0.6f
    private val recentMoves = ArrayDeque<Pair<Long, Offset>>()
    private var mergeCandidate: String? = null
    private var mergeCandidateSince = 0L

    val mergeDwellPending: Boolean get() = (hoverKey as? String)?.startsWith("hold:") == true

    fun start(tabId: String, touchOffset: Offset, originKey: String = tabId) {
        if (!begin(tabItemKey(tabId), originKey, touchOffset)) return
        draggingId = tabId
    }

    fun startGroup(groupId: String, touchOffset: Offset, originKey: String = groupHeaderKey(groupId)) {
        if (!begin(groupHeaderKey(groupId), originKey, touchOffset)) return
        draggingGroupId = groupId
    }

    private fun begin(itemKey: String, handleKey: String, touchOffset: Offset): Boolean {
        val info = gridState.layoutInfo.visibleItemsInfo.find { it.key == itemKey } ?: return false
        dragOffset = Offset.Zero
        // Drawn position of the cell inside the grid box (item offset + content padding), so the
        // lifted overlay starts exactly on top of the card instead of a padding-width off.
        originOffset = Offset(info.offset.x.toFloat(), info.offset.y.toFloat()) + contentPaddingOffset
        originSize = info.size
        pointerStartInRoot = handleOrigins[handleKey]?.plus(touchOffset)
            ?: (gridOriginInRoot + originOffset + Offset(originSize.width / 2f, originSize.height / 2f))
        recentMoves.clear()
        hoverKey = null
        mergeCandidate = null
        return true
    }

    fun drag(delta: Offset) {
        if (!active) return
        dragOffset += delta
        trackSpeed(delta)
        val pointer = pointerStartInRoot + dragOffset
        // Targets follow the finger. Coordinates are in LazyGrid item space (content padding removed).
        val inGrid = pointer - gridOriginInRoot - contentPaddingOffset
        val visible = gridState.layoutInfo.visibleItemsInfo
        var probe = inGrid
        var item = visible.firstOrNull { candidate ->
            inGrid.x >= candidate.offset.x && inGrid.x <= candidate.offset.x + candidate.size.width &&
                inGrid.y >= candidate.offset.y && inGrid.y <= candidate.offset.y + candidate.size.height
        }
        if (item == null) {
            // Finger is in the edge padding or a gap: snap to the nearest card in the same row.
            val row = visible.filter { inGrid.y >= it.offset.y && inGrid.y <= it.offset.y + it.size.height }
            val nearest = row.minByOrNull { candidate ->
                val left = candidate.offset.x.toFloat()
                val right = left + candidate.size.width
                when {
                    inGrid.x < left -> left - inGrid.x
                    inGrid.x > right -> inGrid.x - right
                    else -> 0f
                }
            }
            if (nearest != null && (nearest.key as? String)?.startsWith("tab:") == true) {
                item = nearest
                probe = Offset(
                    inGrid.x.coerceIn(nearest.offset.x + 1f, nearest.offset.x + nearest.size.width - 1f),
                    inGrid.y
                )
            }
        }
        zoneTabId = null
        zoneEdge = 0
        if (draggingGroupId != null) {
            hoverKey = if (item != null) refineGroupDragKey(item, probe) else edgeDropKey(inGrid)
            return
        }
        hoverKey = chipBounds.entries.firstOrNull { it.value.contains(pointer) }?.let { groupChipKey(it.key) }
            ?: if (item != null) refineItemKey(item, probe) else edgeDropKey(inGrid)
        // Keep the dwell timer while the card stays over the same tab, both during the hold and
        // after it turned into a merge; otherwise every small move would restart the dwell and
        // the merge highlight would flicker back to "hold".
        val hovered = hoverKey as? String
        if (hovered == null || !(hovered.startsWith("hold:") || hovered.startsWith("merge:"))) {
            mergeCandidate = null
            holdSince = 0L
        }
    }

    private fun edgeDropKey(inGrid: Offset): Any? = startDropKey(inGrid) ?: endDropKey(inGrid)

    private fun startDropKey(inGrid: Offset): Any? {
        val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return null
        if (first.index != 0) return null
        return if (inGrid.y < first.offset.y) START_DROP_KEY else null
    }

    private fun refineGroupDragKey(item: LazyGridItemInfo, inGrid: Offset): Any? {
        val key = item.key as? String ?: return item.key
        val xFraction = (inGrid.x - item.offset.x) / item.size.width.coerceAtLeast(1)
        val yFraction = (inGrid.y - item.offset.y) / item.size.height.coerceAtLeast(1)
        return when {
            key.startsWith("group:") -> {
                val groupId = key.removePrefix("group:")
                when {
                    groupId == draggingGroupId -> key
                    yFraction < 0.5f -> groupBeforeKey(groupId)
                    else -> groupAfterKey(groupId)
                }
            }
            key.startsWith("tab:") -> {
                val tabId = key.removePrefix("tab:")
                val fraction = if (columns > 1) xFraction else yFraction
                if (fraction < 0.5f) tabBeforeKey(tabId) else tabAfterKey(tabId)
            }
            else -> key
        }
    }

    private fun refineItemKey(item: LazyGridItemInfo, inGrid: Offset): Any? {
        val key = item.key as? String ?: return item.key
        return when {
            key.startsWith("group:") -> {
                val groupId = key.removePrefix("group:")
                val fraction = (inGrid.y - item.offset.y) / item.size.height.coerceAtLeast(1)
                when {
                    fraction < 0.35f -> groupBeforeKey(groupId)
                    groupId in collapsedGroupIds && fraction > 0.65f -> groupAfterKey(groupId)
                    // Dragging a tab of this group onto its own header: put it first in the group
                    // instead of appending it to the end.
                    draggingId?.let(groupOfTab) == groupId ->
                        firstTabAfter(item)?.let { if (it == draggingId) tabItemKey(it) else tabBeforeKey(it) } ?: key
                    else -> key
                }
            }
            key.startsWith("footer:") -> groupAfterKey(key.removePrefix("footer:"))
            key.startsWith("tab:") -> {
                val tabId = key.removePrefix("tab:")
                if (tabId == draggingId) return key
                // Where the dragged card sits now decides the direction: a target after it means
                // "go after the target", a target before it means "go before". No band of the
                // target is a no-op any more.
                val draggedIndex = gridState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == draggingId?.let(::tabItemKey) }?.index
                val forward = draggedIndex == null || item.index > draggedIndex
                val swapKey = if (forward) tabAfterKey(tabId) else tabBeforeKey(tabId)
                val progress = approachProgress(item, inGrid, forward, draggedIndex)
                if (!mergeEnabled || !canMergeWith(tabId)) {
                    return if (progress >= SWAP_ENTER_FRACTION) swapKey else stayKey()
                }
                val fx = ((inGrid.x - item.offset.x) / item.size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                val fy = ((inGrid.y - item.offset.y) / item.size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                val keeping = mergeCandidate == tabId
                val lo = if (keeping) ZONE_KEEP_LO else ZONE_LO
                val hi = if (keeping) ZONE_KEEP_HI else ZONE_HI
                // A single-column list row is wide: only its height decides.
                val inZone = fy in lo..hi && (columns <= 1 || fx in lo..hi)
                zoneTabId = tabId
                zoneEdgeVertical = columns > 1 && draggedIndex?.let { idx ->
                    gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }?.offset?.y == item.offset.y
                } == true
                when {
                    inZone && (keeping || !isMovingFast()) -> mergeOrHoldKey(tabId)
                    progress >= FAR_FRACTION || inZone -> { zoneTabId = null; swapKey }
                    else -> {
                        // Near side of the target: sit right next to it without displacing it.
                        zoneEdge = if (forward) -1 else 1
                        val adjacent = draggedIndex != null && kotlin.math.abs(item.index - draggedIndex) == 1
                        if (adjacent) stayKey() else if (forward) tabBeforeKey(tabId) else tabAfterKey(tabId)
                    }
                }
            }
            else -> key
        }
    }

    /** Keeps the current arrangement (the dragged card's own slot). */
    private fun stayKey(): String? = draggingId?.let(::tabItemKey)

    /**
     * How far the finger has travelled into [item] from the side it approaches from, 0..1.
     * Same row (or a single-column list): along x / y. Different rows: along y.
     */
    private fun approachProgress(item: LazyGridItemInfo, inGrid: Offset, forward: Boolean, draggedIndex: Int?): Float {
        val dragged = draggedIndex?.let { idx -> gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx } }
        val sameRow = columns > 1 && dragged != null && dragged.offset.y == item.offset.y
        val fraction = if (sameRow) {
            (inGrid.x - item.offset.x) / item.size.width.coerceAtLeast(1)
        } else {
            (inGrid.y - item.offset.y) / item.size.height.coerceAtLeast(1)
        }.coerceIn(0f, 1f)
        return if (forward) fraction else 1f - fraction
    }

    private fun trackSpeed(delta: Offset) {
        if (delta == Offset.Zero) return
        val now = SystemClock.uptimeMillis()
        val last = recentMoves.lastOrNull()?.second ?: Offset.Zero
        recentMoves.addLast(now to (last + delta))
        while (recentMoves.size > 2 && now - recentMoves.first().first > SPEED_WINDOW_MS) recentMoves.removeFirst()
    }

    private fun isMovingFast(): Boolean {
        val now = SystemClock.uptimeMillis()
        val first = recentMoves.firstOrNull() ?: return false
        val last = recentMoves.last()
        // No movement event for a while: the finger is resting.
        if (now - last.first > SPEED_WINDOW_MS) return false
        val dt = (now - first.first).coerceAtLeast(16L)
        return (last.second - first.second).getDistance() / dt > fastSpeedPxPerMs
    }

    private fun mergeOrHoldKey(tabId: String): String {
        val now = SystemClock.uptimeMillis()
        if (mergeCandidate != tabId) {
            mergeCandidate = tabId
            mergeCandidateSince = now
            holdSince = now
        }
        return if (now - mergeCandidateSince >= MERGE_DWELL_MS) tabMergeKey(tabId) else tabMergeHoldKey(tabId)
    }

    private fun firstTabAfter(header: LazyGridItemInfo): String? =
        gridState.layoutInfo.visibleItemsInfo
            .filter { it.index > header.index }
            .sortedBy { it.index }
            .firstOrNull { (it.key as? String)?.startsWith("tab:") == true }
            ?.let { (it.key as String).removePrefix("tab:") }

    private fun endDropKey(inGrid: Offset): Any? {
        val info = gridState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return null
        if (last.index != info.totalItemsCount - 1) return null
        val belowContent = inGrid.y > last.offset.y + last.size.height
        val besideLastItem = inGrid.y >= last.offset.y && inGrid.x > last.offset.x + last.size.width
        return if (belowContent || besideLastItem) END_DROP_KEY else null
    }

    fun autoScrollStep(edge: Float, maxStep: Float): Float {
        if (!active || edge <= 0f) return 0f
        if ((hoverKey as? String)?.startsWith("chip:") == true) return 0f
        val y = (pointerStartInRoot + dragOffset - gridOriginInRoot).y // the finger drives auto-scroll
        val height = gridState.layoutInfo.viewportSize.height.toFloat()
        return when {
            y < edge -> {
                val depth = ((edge - y) / edge).coerceAtMost(1f)
                -maxStep * depth * depth
            }
            y > height - edge -> {
                val depth = ((y - (height - edge)) / edge).coerceAtMost(1f)
                maxStep * depth * depth
            }
            else -> 0f
        }
    }

    fun end() {
        draggingId = null
        draggingGroupId = null
        dragOffset = Offset.Zero
        hoverKey = null
        primaryPointerId = null
        mergeCandidate = null
        holdSince = 0L
        zoneTabId = null
        zoneEdge = 0
        recentMoves.clear()
    }
}

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

internal fun mergeTargetTabId(key: Any?): String? =
    (key as? String)?.takeIf { it.startsWith("merge:") }?.removePrefix("merge:")

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

    var gridOriginInRoot: Offset = Offset.Zero
    val chipBounds = mutableMapOf<String, Rect>()
    val handleOrigins = mutableMapOf<String, Offset>()
    var collapsedGroupIds: Set<String> = emptySet()
    var columns: Int = 1
    var mergeEnabled: Boolean = true
    var canMergeWith: (String) -> Boolean = { true }
    var groupOfTab: (String) -> String? = { null }
    var primaryPointerId: PointerId? = null
    private var pointerStartInRoot: Offset = Offset.Zero
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
        originOffset = Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
        originSize = info.size
        pointerStartInRoot = handleOrigins[handleKey]?.plus(touchOffset)
            ?: (gridOriginInRoot + originOffset + Offset(originSize.width / 2f, originSize.height / 2f))
        hoverKey = null
        mergeCandidate = null
        return true
    }

    fun drag(delta: Offset) {
        if (!active) return
        dragOffset += delta
        val pointer = pointerStartInRoot + dragOffset
        val inGrid = pointer - gridOriginInRoot
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
        if (draggingGroupId != null) {
            hoverKey = if (item != null) refineGroupDragKey(item, probe) else edgeDropKey(inGrid)
            return
        }
        hoverKey = chipBounds.entries.firstOrNull { it.value.contains(pointer) }?.let { groupChipKey(it.key) }
            ?: if (item != null) refineItemKey(item, probe) else edgeDropKey(inGrid)
        if ((hoverKey as? String)?.startsWith("hold:") != true) mergeCandidate = null
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
                val fraction = if (columns > 1) {
                    (inGrid.x - item.offset.x) / item.size.width.coerceAtLeast(1)
                } else {
                    (inGrid.y - item.offset.y) / item.size.height.coerceAtLeast(1)
                }
                when {
                    !mergeEnabled || !canMergeWith(tabId) -> if (fraction < 0.5f) tabBeforeKey(tabId) else tabAfterKey(tabId)
                    fraction < 0.3f -> tabBeforeKey(tabId)
                    fraction > 0.7f -> tabAfterKey(tabId)
                    else -> mergeOrHoldKey(tabId)
                }
            }
            else -> key
        }
    }

    private fun mergeOrHoldKey(tabId: String): String {
        val now = SystemClock.uptimeMillis()
        if (mergeCandidate != tabId) {
            mergeCandidate = tabId
            mergeCandidateSince = now
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
        val y = (pointerStartInRoot + dragOffset - gridOriginInRoot).y
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
    }
}

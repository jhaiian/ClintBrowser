package com.jhaiian.clint.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import android.os.SystemClock
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.delegates.closeTabsWithUndo
import com.jhaiian.clint.browser.delegates.toggleTabMute
import com.jhaiian.clint.browser.delegates.createTabGroupWithNewTab
import com.jhaiian.clint.browser.delegates.onTabGroupsChanged
import com.jhaiian.clint.browser.delegates.openNewTabInGroup
import com.jhaiian.clint.browser.delegates.saveTabs
import com.jhaiian.clint.ui.TabMenuOverflowMenu
import com.jhaiian.clint.ui.TabSelectionOverflowMenu
import com.jhaiian.clint.ui.listscreen.ClintSearchField
import com.jhaiian.clint.ui.showClintTwoActionSnackbar
import com.jhaiian.clint.ui.showClintSnackbar
import com.jhaiian.clint.ui.UNDO_SNACKBAR_TIMEOUT_MS
import com.jhaiian.clint.ui.ClintDialog
import androidx.compose.foundation.clickable
import com.jhaiian.clint.ui.theme.LocalClintColors

private val GridEdgePadding = 7.dp
private val CellPadding = 5.dp
private val CellMinWidth = 160.dp
private val ListCellMinWidth = 340.dp
private const val PREF_TAB_MENU_LIST_VIEW = "tab_menu_list_view"
private const val PREF_TAB_MENU_BUTTONS_AT_BOTTOM = "tab_menu_buttons_at_bottom"
private const val PREF_TAB_MENU_TABS_AT_BOTTOM = "tab_menu_tabs_at_bottom"
private val GroupCorner = 18.dp

private sealed class TabMenuItem {
    abstract val key: String
    open val fullSpan: Boolean = false

    data class SectionHeader(val isIncognitoSection: Boolean) : TabMenuItem() {
        override val key: String get() = "section:$isIncognitoSection"
        override val fullSpan: Boolean get() = true
    }

    data class GroupHeader(val group: TabGroup, val tabs: List<TabPreview>) : TabMenuItem() {
        override val key: String get() = groupHeaderKey(group.id)
        override val fullSpan: Boolean get() = true
    }

    data class Tab(val preview: TabPreview, val group: TabGroup?) : TabMenuItem() {
        override val key: String get() = tabItemKey(preview.id)
    }

    data class GroupFiller(val group: TabGroup, val slot: Int) : TabMenuItem() {
        override val key: String get() = groupFillerKey(group.id, slot)
    }

    data class GroupFooter(val group: TabGroup) : TabMenuItem() {
        override val key: String get() = groupFooterKey(group.id)
        override val fullSpan: Boolean get() = true
    }

    data class RowGap(override val key: String) : TabMenuItem()
}

private fun <T> padPartialRow(items: List<T>, columns: Int, partialRowFirst: Boolean, filler: (Int) -> T): List<T> {
    val remainder = items.size % columns
    if (remainder == 0) return items
    val fillers = List(columns - remainder, filler)
    return if (partialRowFirst) items.take(remainder) + fillers + items.drop(remainder) else items + fillers
}

private sealed class TabGroupEditorRequest {
    data class CreateWithNewTab(val isIncognito: Boolean) : TabGroupEditorRequest()
    data class CreateFromTabs(val tabIds: List<String>) : TabGroupEditorRequest()
    data class Edit(val group: TabGroup) : TabGroupEditorRequest()
}

private fun buildRenderList(
    tabs: List<TabPreview>,
    groups: List<TabGroup>,
    columns: Int,
    forceCollapsed: Boolean = false,
    forceExpanded: Boolean = false,
    reversed: Boolean = false
): List<TabMenuItem> {
    val hasBothSections = tabs.any { !it.isIncognito } && tabs.any { it.isIncognito }
    val groupsById = groups.associateBy { it.id }
    val result = mutableListOf<TabMenuItem>()
    for (incognitoSection in if (reversed) listOf(true, false) else listOf(false, true)) {
        val sectionTabs = tabs.filter { it.isIncognito == incognitoSection }.let { if (reversed) it.asReversed() else it }
        if (sectionTabs.isEmpty()) continue
        if (hasBothSections) result.add(TabMenuItem.SectionHeader(incognitoSection))
        val emitted = mutableSetOf<String>()
        val membersByGroup = sectionTabs.groupBy { it.groupId }
        val looseRun = mutableListOf<TabMenuItem>()
        var runIndex = 0
        fun flushLooseRun() {
            if (looseRun.isEmpty()) return
            val run = runIndex++
            result.addAll(
                if (reversed) {
                    padPartialRow(looseRun.toList(), columns, partialRowFirst = true) { slot ->
                        TabMenuItem.RowGap("gap:$incognitoSection:$run:$slot")
                    }
                } else {
                    looseRun
                }
            )
            looseRun.clear()
        }
        sectionTabs.forEach { preview ->
            val group = preview.groupId?.let { groupsById[it] }?.takeIf { it.isIncognito == incognitoSection }
            if (group == null) {
                looseRun.add(TabMenuItem.Tab(preview, null))
            } else if (emitted.add(group.id)) {
                flushLooseRun()
                val members = membersByGroup[group.id].orEmpty()
                val collapsed = forceCollapsed || (group.collapsed && !forceExpanded)
                result.add(TabMenuItem.GroupHeader(if (collapsed != group.collapsed) group.copy(collapsed = collapsed) else group, members))
                if (!collapsed) {
                    result.addAll(
                        padPartialRow(members.map { TabMenuItem.Tab(it, group) }, columns, partialRowFirst = reversed) { slot ->
                            TabMenuItem.GroupFiller(group, slot)
                        }
                    )
                    result.add(TabMenuItem.GroupFooter(group))
                }
            }
        }
        flushLooseRun()
    }
    return result
}

private fun visibleGroups(tabs: List<TabPreview>, groups: List<TabGroup>): List<TabGroup> {
    val used = tabs.mapNotNull { it.groupId }.toSet()
    return groups.filter { it.id in used }.sortedBy { if (it.isIncognito) 1 else 0 }
}

private fun TabPreview.matchesQuery(query: String): Boolean =
    title.contains(query, ignoreCase = true) || url.contains(query, ignoreCase = true)

private fun normalizedTabUrl(url: String): String = url.substringBefore('#').trimEnd('/')

private fun duplicateTabIds(tabs: List<TabPreview>, activeTabId: String?): List<String> =
    tabs.filter { normalizedTabUrl(it.url).isNotEmpty() }
        .groupBy { Triple(it.isIncognito, it.profileId, normalizedTabUrl(it.url)) }
        .values
        .filter { it.size > 1 }
        .flatMap { copies ->
            val keeper = copies.firstOrNull { it.id == activeTabId } ?: copies.firstOrNull { it.isPinned } ?: copies.first()
            copies.filter { it.id != keeper.id && !it.isPinned }.map { it.id }
        }

/** How long a card must be held before it lifts for dragging (system long press is ~400 ms). */
private const val LIFT_HOLD_MS = 600L

/**
 * Waits until the pointer [down] has been held still for [holdMs]. Returns the latest change for
 * it, or null when it lifted, moved past touch slop, or another gesture consumed it first.
 */
private suspend fun AwaitPointerEventScope.awaitLongHold(down: PointerInputChange, holdMs: Long): PointerInputChange? {
    val slop = viewConfiguration.touchSlop
    var last = down
    // Finishes (non-null) only when the hold is broken; times out (null) when it lasted.
    val broken = withTimeoutOrNull(holdMs) {
        var holding = true
        while (holding) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            holding = change != null && change.pressed && !change.isConsumed &&
                (change.position - down.position).getDistance() <= slop
            if (holding && change != null) last = change
        }
    }
    return if (broken == null) last else null
}

/** What a drag commit changed, so its undo can put back order, groups and pins together. */
private data class ArrangementSnapshot(
    val order: List<String>,
    val assignments: Map<String, String?>,
    val groups: List<TabGroup>,
    val pinned: Map<String, Boolean>
)

private fun TabManager.arrangementSnapshot(): ArrangementSnapshot = ArrangementSnapshot(
    order = tabs.map { it.id },
    assignments = tabs.associate { it.id to it.groupId },
    groups = groups.toList(),
    pinned = tabs.associate { it.id to it.isPinned }
)

/** The finger that lifted a card by long press, followed by the screen-level pointer handler. */
private class DragPointerSession {
    var pointerId: PointerId? = null
        private set
    var onDrop: () -> Unit = {}
        private set
    var onLongPress: () -> Unit = {}
        private set
    var travelled = 0f
    var scrolled = false

    fun arm(id: PointerId, drop: () -> Unit, longPress: () -> Unit) {
        pointerId = id
        onDrop = drop
        onLongPress = longPress
        travelled = 0f
        scrolled = false
    }

    fun reset() {
        pointerId = null
        onDrop = {}
        onLongPress = {}
        travelled = 0f
        scrolled = false
    }
}

private class DragCallbacks {
    var moved: () -> Unit = {}
    var cancel: () -> Unit = {}
}

private fun canMergeTabs(dragged: TabPreview, target: TabPreview): Boolean =
    dragged.id != target.id && dragged.isIncognito == target.isIncognito &&
        (target.groupId == null || target.groupId != dragged.groupId)

@Composable
fun TabMenuScreen(activity: MainActivity, onDismiss: () -> Unit) {
    val colors = LocalClintColors.current
    val density = LocalDensity.current
    val tabManager = activity.tabManager
    val uiState = remember { TabMenuUiState() }
    val tabs = remember { mutableStateListOf<TabPreview>().apply { addAll(tabManager.previews()) } }
    val groups = remember { mutableStateListOf<TabGroup>().apply { addAll(tabManager.groups) } }
    var activeTabId by remember { mutableStateOf(tabManager.activeTab?.id) }
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity) }
    val hideStatusBar = remember { prefs.getBoolean("hide_status_bar", false) }
    val hideSystemNavigation = remember { prefs.getBoolean("hide_system_navigation", false) }
    var showGroupBar by remember { mutableStateOf(prefs.getBoolean(TabGroupPrefs.PREF_SHOW_GROUP_BAR, true)) }
    val groupsEnabled = remember { TabGroupPrefs.isEnabled(prefs) }
    var editorRequest by remember { mutableStateOf<TabGroupEditorRequest?>(null) }
    var showMovePicker by remember { mutableStateOf(false) }
    var showRecentlyClosed by remember { mutableStateOf(false) }
    var scrollToGroupId by remember { mutableStateOf<String?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var listView by remember { mutableStateOf(prefs.getBoolean(PREF_TAB_MENU_LIST_VIEW, false)) }
    var buttonsAtBottom by remember { mutableStateOf(prefs.getBoolean(PREF_TAB_MENU_BUTTONS_AT_BOTTOM, false)) }
    var tabsAtBottom by remember { mutableStateOf(prefs.getBoolean(PREF_TAB_MENU_TABS_AT_BOTTOM, false)) }
    val hasDuplicates by remember { derivedStateOf { duplicateTabIds(tabs, activeTabId).isNotEmpty() } }

    fun closeSearch() {
        searchActive = false
        searchQuery = ""
    }

    fun syncFromManager() {
        tabs.clear(); tabs.addAll(tabManager.previews())
        groups.clear(); groups.addAll(tabManager.groups)
        activeTabId = tabManager.activeTab?.id
        uiState.retainExisting(tabs.map { it.id })
        if (uiState.selectionMode && uiState.selectedIds.isEmpty() && tabs.isEmpty()) uiState.exitSelectionMode()
    }

    fun commitGroups(change: TabManager.() -> Unit) {
        tabManager.change()
        activity.onTabGroupsChanged()
        syncFromManager()
    }

    val focusGroupId = remember {
        val requested = activity.uiState.tabMenuFocusGroupId
        activity.uiState.tabMenuFocusGroupId = null
        if (requested != null && tabManager.group(requested)?.collapsed == true) {
            tabManager.updateGroup(requested) { it.copy(collapsed = false) }
            groups.clear(); groups.addAll(tabManager.groups)
            activity.saveTabs()
        }
        requested
    }

    LaunchedEffect(activity.uiState.tabsRevision) { syncFromManager() }

    fun openTab(tabId: String) {
        val idx = tabManager.tabs.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        activity.onTabSelected(idx)
        onDismiss()
    }

    fun closeTabs(ids: List<String>, message: String? = null) {
        if (ids.isEmpty()) return
        val closingAll = ids.toSet().containsAll(tabs.map { it.id })
        activity.closeTabsWithUndo(
            ids,
            message ?: activity.resources.getQuantityString(R.plurals.tab_menu_closed_tabs, ids.size, ids.size)
        )
        if (closingAll) { onDismiss(); return }
        syncFromManager()
    }

    fun toggleCollapsed(group: TabGroup) {
        commitGroups { updateGroup(group.id) { it.copy(collapsed = !it.collapsed) } }
    }

    fun revealGroup(groupId: String) {
        if (tabManager.group(groupId)?.collapsed == true) {
            commitGroups { updateGroup(groupId) { it.copy(collapsed = false) } }
        }
        scrollToGroupId = groupId
    }

    fun setAllCollapsed(collapsed: Boolean) {
        val groupIds = groups.map { it.id }
        commitGroups { groupIds.forEach { id -> updateGroup(id) { it.copy(collapsed = collapsed) } } }
    }

    BackHandler {
        when {
            uiState.selectionMode -> uiState.exitSelectionMode()
            searchActive -> closeSearch()
            else -> onDismiss()
        }
    }

    var showSortDialog by remember { mutableStateOf(false) }

    fun undoArrangement(previous: ArrangementSnapshot) {
        commitGroups {
            previous.groups.forEach { restoreGroup(it) }
            // Joining a group unpins a tab; give the pin back before restoring order and groups.
            this.tabs.forEach { tab -> previous.pinned[tab.id]?.let { tab.isPinned = it } }
            val currentIds = this.tabs.map { it.id }
            val currentSet = currentIds.toSet()
            val previousSet = previous.order.toSet()
            applyArrangement(previous.order.filter { it in currentSet } + currentIds.filter { it !in previousSet }, previous.assignments)
        }
    }

    fun sortTabsWithUndo(mode: TabSortMode) {
        val previous = tabManager.arrangementSnapshot()
        commitGroups { sortTabs(mode) }
        activity.showClintSnackbar(
            message = activity.getString(R.string.tab_menu_tabs_sorted),
            actionLabel = activity.getString(R.string.action_undo),
            timeoutMs = UNDO_SNACKBAR_TIMEOUT_MS
        ) { undoArrangement(previous) }
    }

    fun groupBySiteWithUndo() {
        val previous = tabManager.arrangementSnapshot()
        var created = 0
        commitGroups { created = groupBySite() }
        if (created > 0) {
            activity.showClintSnackbar(
                message = activity.resources.getQuantityString(R.plurals.tab_menu_grouped_by_site, created, created),
                actionLabel = activity.getString(R.string.action_undo),
                timeoutMs = UNDO_SNACKBAR_TIMEOUT_MS
            ) { undoArrangement(previous) }
        } else {
            activity.showClintSnackbar(message = activity.getString(R.string.tab_menu_nothing_to_group))
        }
    }

    if (showSortDialog) {
        ClintDialog(
            title = stringResource(R.string.tab_menu_sort_tabs),
            hideStatusBar = hideStatusBar,
            hideSystemNavigation = hideSystemNavigation,
            onDismiss = { showSortDialog = false }
        ) {
            listOf(
                TabSortMode.LAST_USED to R.string.tab_sort_last_used,
                TabSortMode.SITE to R.string.tab_sort_site,
                TabSortMode.TITLE to R.string.tab_sort_title
            ).forEach { (mode, label) ->
                Text(
                    stringResource(label),
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSortDialog = false; sortTabsWithUndo(mode) }
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                )
            }
        }
    }

    var showProfilePicker by remember { mutableStateOf(false) }
    if (showProfilePicker) {
        com.jhaiian.clint.profiles.ProfilePickerDialog(
            profiles = remember { com.jhaiian.clint.profiles.ProfileRepository.all(activity) },
            hideStatusBar = hideStatusBar,
            hideSystemNavigation = hideSystemNavigation,
            onPick = { id -> showProfilePicker = false; activity.onSwitchProfile(id); onDismiss() },
            selectedId = activity.tabManager.activeProfileId,
            onManage = {
                showProfilePicker = false
                activity.startActivity(android.content.Intent(activity, com.jhaiian.clint.profiles.ProfilesActivity::class.java))
                onDismiss()
            },
            onDismiss = { showProfilePicker = false }
        )
    }

    if (showRecentlyClosed) {
        RecentlyClosedSheet(activity, onDismiss = { showRecentlyClosed = false }, onReopened = onDismiss)
    }

    editorRequest?.let { request ->
        val usedColors = groups.map { it.color }
        val initialName = when (request) {
            is TabGroupEditorRequest.Edit -> request.group.name
            is TabGroupEditorRequest.CreateFromTabs -> remember(request) {
                suggestGroupName(request.tabIds.mapNotNull { id -> tabs.firstOrNull { it.id == id }?.url?.takeIf { url -> url.isNotEmpty() } })
            }
            is TabGroupEditorRequest.CreateWithNewTab -> ""
        }
        val initialColor = when (request) {
            is TabGroupEditorRequest.Edit -> request.group.color
            else -> TabGroupColor.next(usedColors)
        }
        TabGroupEditorDialog(
            title = stringResource(if (request is TabGroupEditorRequest.Edit) R.string.tab_group_edit_title else R.string.tab_group_new),
            positiveLabel = stringResource(if (request is TabGroupEditorRequest.Edit) R.string.action_save else R.string.tab_group_create),
            initialName = initialName,
            initialColor = initialColor,
            hideStatusBar = hideStatusBar,
            hideSystemNavigation = hideSystemNavigation,
            onDismiss = { editorRequest = null },
            onConfirm = { name, color ->
                editorRequest = null
                when (request) {
                    is TabGroupEditorRequest.Edit -> commitGroups { updateGroup(request.group.id) { it.copy(name = name, color = color) } }
                    is TabGroupEditorRequest.CreateFromTabs -> {
                        var createdId: String? = null
                        commitGroups { createdId = createGroup(name, color, request.tabIds)?.id }
                        uiState.exitSelectionMode()
                        createdId?.let { scrollToGroupId = it }
                    }
                    is TabGroupEditorRequest.CreateWithNewTab -> {
                        activity.createTabGroupWithNewTab(name, color, request.isIncognito)
                        onDismiss()
                    }
                }
            }
        )
    }

    if (showMovePicker) {
        val selected = uiState.selectedIds.toList()
        val selectedModes = tabs.filter { it.id in selected }.map { it.isIncognito }.toSet()
        val candidates = visibleGroups(tabs, groups).filter { it.isIncognito in selectedModes }
        TabGroupPickerDialog(
            groups = candidates,
            counts = tabs.groupingBy { it.groupId ?: "" }.eachCount(),
            hideStatusBar = hideStatusBar,
            hideSystemNavigation = hideSystemNavigation,
            onPick = { groupId ->
                showMovePicker = false
                commitGroups { addToGroup(selected, groupId) }
                uiState.exitSelectionMode()
                scrollToGroupId = groupId
            },
            onNewGroup = {
                showMovePicker = false
                editorRequest = TabGroupEditorRequest.CreateFromTabs(selected)
            },
            onDismiss = { showMovePicker = false }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().navigationBarsPadding()) {
        val columns = ((maxWidth - GridEdgePadding * 2) / if (listView) ListCellMinWidth else CellMinWidth).toInt().coerceAtLeast(1)
        val initialIndex = remember {
            val renderList = buildRenderList(tabs, groups, columns, reversed = tabsAtBottom)
            val activeGroupId = tabs.firstOrNull { it.id == activeTabId }?.groupId
            val focusIndex = focusGroupId?.let { id -> renderList.indexOfFirst { it.key == groupHeaderKey(id) } } ?: -1
            val activeIndex = renderList.indexOfFirst { it is TabMenuItem.Tab && it.preview.id == activeTabId }
            val activeGroupIndex = activeGroupId?.let { id -> renderList.indexOfFirst { it.key == groupHeaderKey(id) } } ?: -1
            when {
                focusIndex >= 0 -> focusIndex
                activeIndex >= 0 -> activeIndex
                else -> activeGroupIndex.coerceAtLeast(0)
            }
        }
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex)
        val dragState = remember(gridState) { TabDragState(gridState) }
        val renderList by remember(columns, tabsAtBottom) {
            derivedStateOf {
                val query = searchQuery.trim()
                buildRenderList(
                    if (query.isEmpty()) tabs.toList() else tabs.filter { it.matchesQuery(query) },
                    groups,
                    columns,
                    forceCollapsed = dragState.draggingGroupId != null,
                    forceExpanded = query.isNotEmpty(),
                    reversed = tabsAtBottom
                )
            }
        }
        val searching = searchQuery.isNotBlank()
        val dragAllowed = !uiState.selectionMode && !searching
        val draggingPreview = dragState.draggingId?.let { id -> tabs.find { it.id == id } }
        val collapsedGroupIds = groups.filter { it.collapsed }.map { it.id }.toSet()
        SideEffect {
            dragState.collapsedGroupIds = collapsedGroupIds
            dragState.contentPaddingOffset = with(density) { GridEdgePadding.toPx() }.let { Offset(it, it) }
            // ~600 dp/s: only a quick flick through a card's centre counts as passing over it.
            dragState.fastSpeedPxPerMs = with(density) { 0.6.dp.toPx() }
            dragState.columns = columns
            dragState.mergeEnabled = groupsEnabled
            dragState.canMergeWith = { targetId ->
                val dragged = dragState.draggingId?.let { id -> tabs.find { it.id == id } }
                val target = tabs.find { it.id == targetId }
                dragged != null && target != null && canMergeTabs(dragged, target)
            }
            dragState.groupOfTab = { id -> tabs.find { it.id == id }?.groupId }
        }
        val dragSession = remember { DragPointerSession() }
        val shownGroups = visibleGroups(tabs, groups)
        val groupCounts = tabs.groupingBy { it.groupId ?: "" }.eachCount()
        val activeGroupId = tabs.firstOrNull { it.id == activeTabId }?.groupId

        LaunchedEffect(scrollToGroupId, renderList.size) {
            val target = scrollToGroupId ?: return@LaunchedEffect
            val index = renderList.indexOfFirst { it.key == groupHeaderKey(target) }
            if (index >= 0) gridState.animateScrollToItem(index)
            scrollToGroupId = null
        }

        val haptic = LocalHapticFeedback.current

        fun orderedHoverKey(): String? {
            val key = dragState.hoverKey as? String ?: return null
            if (!tabsAtBottom) return key
            return when {
                key.startsWith("tabBefore:") -> tabAfterKey(key.removePrefix("tabBefore:"))
                key.startsWith("tabAfter:") -> tabBeforeKey(key.removePrefix("tabAfter:"))
                key.startsWith("before:") -> groupAfterKey(key.removePrefix("before:"))
                key.startsWith("after:") -> groupBeforeKey(key.removePrefix("after:"))
                else -> key
            }
        }

        fun endDropIndex(others: List<TabPreview>, incognito: Boolean): Int =
            if (tabsAtBottom) others.indexOfFirst { it.isIncognito == incognito }.coerceAtLeast(0)
            else others.indexOfLast { it.isIncognito == incognito } + 1

        fun startDropIndex(others: List<TabPreview>, incognito: Boolean): Int =
            if (tabsAtBottom) others.indexOfLast { it.isIncognito == incognito } + 1
            else others.indexOfFirst { it.isIncognito == incognito }.let { if (it < 0) others.size else it }

        fun onDragMoved() {
            val draggedId = dragState.draggingId ?: return
            val dragged = tabs.find { it.id == draggedId } ?: return
            // Group the tab belonged to when the drag started. Sliding past the edge of another
            // group's member must not join that group; only the centre dwell (merge) does.
            val homeGroupId = tabManager.tabs.firstOrNull { it.id == draggedId }?.groupId
            val hoverKey = orderedHoverKey() ?: return
            val others = tabs.filter { it.id != draggedId }
            var targetGroupId: String? = null
            fun sameModeGroup(groupId: String): Boolean =
                groups.any { it.id == groupId && it.isIncognito == dragged.isIncognito }
            val insertAt = when {
                hoverKey == END_DROP_KEY -> endDropIndex(others, dragged.isIncognito)
                hoverKey == START_DROP_KEY -> startDropIndex(others, dragged.isIncognito)
                hoverKey.startsWith("before:") -> {
                    val groupId = hoverKey.removePrefix("before:")
                    if (!sameModeGroup(groupId)) return
                    others.indexOfFirst { it.groupId == groupId }.takeIf { it >= 0 } ?: return
                }
                hoverKey.startsWith("after:") -> {
                    val groupId = hoverKey.removePrefix("after:")
                    if (!sameModeGroup(groupId)) return
                    (others.indexOfLast { it.groupId == groupId }.takeIf { it >= 0 } ?: return) + 1
                }
                hoverKey.startsWith("tabBefore:") || hoverKey.startsWith("tabAfter:") -> {
                    val after = hoverKey.startsWith("tabAfter:")
                    val targetId = hoverKey.substringAfter(':')
                    val target = others.find { it.id == targetId } ?: return
                    if (target.isIncognito != dragged.isIncognito) return
                    val otherGroupId = target.groupId
                    if (otherGroupId != null && otherGroupId != homeGroupId) {
                        // Next to a member of another group: land before or after that whole group.
                        if (after) others.indexOfLast { it.groupId == otherGroupId } + 1
                        else others.indexOfFirst { it.groupId == otherGroupId }
                    } else {
                        targetGroupId = otherGroupId
                        others.indexOf(target) + if (after) 1 else 0
                    }
                }
                else -> return
            }
            val arranged = others.toMutableList().apply { add(insertAt.coerceIn(0, size), dragged.copy(groupId = targetGroupId)) }
            if (arranged.map { it.id to it.groupId } != tabs.map { it.id to it.groupId }) {
                tabs.clear()
                tabs.addAll(arranged)
            }
        }

        fun commitDrag() {
            // Judge the drop at lift time: a dwell that completed since the last frame counts.
            dragState.drag(Offset.Zero)
            onDragMoved()
            val draggedId = dragState.draggingId
            val dropKey = dragState.hoverKey
            dragState.end()
            if (draggedId == null) return
            val dragged = tabs.find { it.id == draggedId }
            val mergeTarget = mergeTargetTabId(dropKey)?.let { id -> tabs.find { it.id == id } }
                ?.takeIf { groupsEnabled && dragged != null && canMergeTabs(dragged, it) }
            val dropGroupId = dropTargetGroupId(dropKey) ?: mergeTarget?.groupId
            val previous = tabManager.arrangementSnapshot()
            val previousOrder = previous.order
            val previousAssignments = previous.assignments
            val orderedIds = tabs.map { it.id }
            val assignments = tabs.associate { it.id to it.groupId }
            var createdGroupId: String? = null
            commitGroups {
                applyArrangement(orderedIds, assignments)
                if (dropGroupId != null) {
                    addToGroup(listOf(draggedId), dropGroupId)
                } else if (mergeTarget != null && dragged != null) {
                    createdGroupId = createGroup(
                        suggestGroupName(listOf(mergeTarget.url, dragged.url).filter { it.isNotEmpty() }),
                        TabGroupColor.next(groups.map { it.color }),
                        listOf(mergeTarget.id, draggedId)
                    )?.id
                }
            }
            val newGroupId = createdGroupId
            if (newGroupId != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scrollToGroupId = newGroupId
                activity.showClintTwoActionSnackbar(
                    message = activity.getString(R.string.tab_group_created_message),
                    actionLabel = activity.getString(R.string.action_undo),
                    onAction = { undoArrangement(previous) },
                    secondaryActionLabel = activity.getString(R.string.action_edit),
                    onSecondaryAction = { tabManager.group(newGroupId)?.let { editorRequest = TabGroupEditorRequest.Edit(it) } }
                )
            } else {
                if (dropGroupId != null && tabManager.group(dropGroupId)?.collapsed == true) {
                    scrollToGroupId = dropGroupId
                }
                val changed = tabManager.tabs.map { it.id } != previousOrder ||
                    tabManager.tabs.any { it.groupId != previousAssignments[it.id] }
                if (changed) {
                    val movedIntoGroup = dropGroupId != null && previousAssignments[draggedId] != dropGroupId
                    activity.showClintSnackbar(
                        message = activity.getString(if (movedIntoGroup) R.string.tab_moved_to_group_message else R.string.tab_moved_message),
                        actionLabel = activity.getString(R.string.action_undo),
                        timeoutMs = UNDO_SNACKBAR_TIMEOUT_MS
                    ) { undoArrangement(previous) }
                }
            }
        }

        fun onGroupDragMoved() {
            val groupId = dragState.draggingGroupId ?: return
            val group = groups.find { it.id == groupId } ?: return
            val hoverKey = orderedHoverKey() ?: return
            val members = tabs.filter { it.groupId == groupId }
            if (members.isEmpty()) return
            val others = tabs.filter { it.groupId != groupId }
            val insertAt = when {
                hoverKey == END_DROP_KEY -> endDropIndex(others, group.isIncognito)
                hoverKey == START_DROP_KEY -> startDropIndex(others, group.isIncognito)
                hoverKey.startsWith("before:") -> {
                    val targetId = hoverKey.removePrefix("before:")
                    others.indexOfFirst { it.groupId == targetId && it.isIncognito == group.isIncognito }.takeIf { it >= 0 } ?: return
                }
                hoverKey.startsWith("after:") -> {
                    val targetId = hoverKey.removePrefix("after:")
                    (others.indexOfLast { it.groupId == targetId && it.isIncognito == group.isIncognito }.takeIf { it >= 0 } ?: return) + 1
                }
                hoverKey.startsWith("tabBefore:") || hoverKey.startsWith("tabAfter:") -> {
                    val targetId = hoverKey.substringAfter(':')
                    val target = others.find { it.id == targetId } ?: return
                    if (target.isIncognito != group.isIncognito || target.groupId != null) return
                    others.indexOf(target) + if (hoverKey.startsWith("tabAfter:")) 1 else 0
                }
                else -> return
            }
            val arranged = others.toMutableList().apply { addAll(insertAt.coerceIn(0, size), members) }
            if (arranged.map { it.id } != tabs.map { it.id }) {
                tabs.clear()
                tabs.addAll(arranged)
            }
        }

        fun commitGroupDrag() {
            dragState.drag(Offset.Zero)
            onGroupDragMoved()
            val groupId = dragState.draggingGroupId
            dragState.end()
            if (groupId == null) return
            val previous = tabManager.arrangementSnapshot()
            val orderedIds = tabs.map { it.id }
            val assignments = tabs.associate { it.id to it.groupId }
            commitGroups { applyArrangement(orderedIds, assignments) }
            scrollToGroupId = groupId
            if (tabManager.tabs.map { it.id } != previous.order) {
                activity.showClintSnackbar(
                    message = activity.getString(R.string.tab_group_moved_message),
                    actionLabel = activity.getString(R.string.action_undo),
                    timeoutMs = UNDO_SNACKBAR_TIMEOUT_MS
                ) { undoArrangement(previous) }
            }
        }

        LaunchedEffect(dragState.draggingGroupId) {
            val groupId = dragState.draggingGroupId ?: return@LaunchedEffect
            val index = renderList.indexOfFirst { it.key == groupHeaderKey(groupId) }
            if (index < 0) return@LaunchedEffect
            gridState.scrollToItem(index)
            val info = gridState.layoutInfo.visibleItemsInfo.find { it.key == groupHeaderKey(groupId) } ?: return@LaunchedEffect
            gridState.scrollBy(info.offset.y + dragState.contentPaddingOffset.y - dragState.originOffset.y)
            dragState.drag(Offset.Zero)
        }

        val autoScrollEdgePx = with(density) { 72.dp.toPx() }
        val autoScrollMaxStepPx = with(density) { 20.dp.toPx() }
        LaunchedEffect(dragState.active) {
            if (!dragState.active) return@LaunchedEffect
            while (dragState.active) {
                withFrameNanos { }
                val step = dragState.autoScrollStep(autoScrollEdgePx, autoScrollMaxStepPx)
                val scrolled = step != 0f && gridState.scrollBy(step) != 0f
                if (scrolled || dragState.mergeDwellPending) {
                    // Re-evaluate under the still finger: after a scroll, or while a merge dwell runs.
                    dragState.drag(Offset.Zero)
                    if (dragState.draggingGroupId != null) onGroupDragMoved() else onDragMoved()
                }
            }
        }

        LaunchedEffect(dragState.hoverKey) {
            val key = dragState.hoverKey as? String ?: return@LaunchedEffect
            val isDropZone = key.startsWith("group:") || key.startsWith("chip:") || key.startsWith("before:") ||
                key.startsWith("after:") || key.startsWith("merge:") || key == END_DROP_KEY || key == START_DROP_KEY
            if (dragState.active && isDropZone) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        fun cancelDrag() {
            dragState.end()
            syncFromManager()
        }

        val dragCallbacks = remember { DragCallbacks() }
        SideEffect {
            dragCallbacks.moved = { if (dragState.draggingGroupId != null) onGroupDragMoved() else onDragMoved() }
            dragCallbacks.cancel = { cancelDrag() }
        }

        fun Modifier.longPressDrag(
            originKey: String,
            onLift: (Offset, PointerId) -> Boolean,
            onDrop: () -> Unit,
            onLongPress: () -> Unit
        ): Modifier = onGloballyPositioned { dragState.handleOrigins[originKey] = it.positionInRoot() }
            .pointerInput(originKey) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A finger landing while another card is already lifted belongs to scrolling.
                    if (dragState.active) return@awaitEachGesture
                    val longPress = awaitLongHold(down, LIFT_HOLD_MS) ?: return@awaitEachGesture
                    if (dragState.active) return@awaitEachGesture
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Lift right away; the screen-level pointer handler follows this finger from
                    // here on, so a second finger can scroll the list meanwhile.
                    if (onLift(longPress.position, longPress.id)) {
                        longPress.consume()
                        dragSession.arm(longPress.id, onDrop, onLongPress)
                    } else {
                        while (awaitPointerEvent().changes.any { it.id == longPress.id && it.pressed }) Unit
                        onLongPress()
                    }
                }
            }

        val topBar: @Composable () -> Unit = {
            TabMenuTopBar(
                tabCount = tabs.size,
                selectionMode = uiState.selectionMode,
                selectedCount = uiState.selectedIds.size,
                groupsEnabled = groupsEnabled,
                hasGroups = shownGroups.isNotEmpty(),
                allGroupsCollapsed = shownGroups.isNotEmpty() && shownGroups.all { it.collapsed },
                showGroupBar = showGroupBar,
                searchActive = searchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onOpenSearch = { searchActive = true },
                onCloseSearch = { closeSearch() },
                listView = listView,
                onToggleListView = {
                    listView = !listView
                    prefs.edit().putBoolean(PREF_TAB_MENU_LIST_VIEW, listView).apply()
                },
                onCloseDuplicates = if (hasDuplicates) ({
                    val ids = duplicateTabIds(tabs, activeTabId)
                    closeTabs(ids, activity.resources.getQuantityString(R.plurals.tab_menu_closed_duplicates, ids.size, ids.size))
                }) else null,
                onSortTabs = if (tabs.size >= 2) ({ showSortDialog = true }) else null,
                onGroupBySite = if (groupsEnabled) ({ groupBySiteWithUndo() }) else null,
                onRecentlyClosed = { showRecentlyClosed = true },
                onToggleSelectionMode = { uiState.selectionMode = true },
                onSelectAll = { uiState.selectAll(tabs.map { it.id }) },
                onInvertSelection = { uiState.invertSelection(tabs.map { it.id }) },
                onDeleteSelected = { closeTabs(uiState.selectedIds.toList()); uiState.exitSelectionMode() },
                onNewTab = { activity.onNewTab(); onDismiss() },
                onNewIncognitoTab = { activity.onNewIncognitoTab(); onDismiss() },
                onNewTabGroup = { editorRequest = TabGroupEditorRequest.CreateWithNewTab(false) },
                onToggleGroupBar = {
                    showGroupBar = !showGroupBar
                    prefs.edit().putBoolean(TabGroupPrefs.PREF_SHOW_GROUP_BAR, showGroupBar).apply()
                },
                onCollapseAllGroups = { setAllCollapsed(true) },
                onExpandAllGroups = { setAllCollapsed(false) },
                onNewProfileTab = {
                    if (com.jhaiian.clint.profiles.ProfileRepository.all(activity).isEmpty()) {
                        activity.startActivity(android.content.Intent(activity, com.jhaiian.clint.profiles.ProfilesActivity::class.java))
                        onDismiss()
                    } else {
                        showProfilePicker = true
                    }
                },
                onCloseAllTabs = { closeTabs(tabs.filter { !it.isPinned }.map { it.id }) },
                buttonsAtBottom = buttonsAtBottom,
                onToggleButtonsAtBottom = {
                    buttonsAtBottom = !buttonsAtBottom
                    prefs.edit().putBoolean(PREF_TAB_MENU_BUTTONS_AT_BOTTOM, buttonsAtBottom).apply()
                },
                tabsAtBottom = tabsAtBottom,
                onToggleTabsAtBottom = {
                    tabsAtBottom = !tabsAtBottom
                    prefs.edit().putBoolean(PREF_TAB_MENU_TABS_AT_BOTTOM, tabsAtBottom).apply()
                }
            )
        }

        val newTabButtons: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (buttonsAtBottom) 4.dp else 0.dp,
                    bottom = if (buttonsAtBottom) 4.dp else 10.dp
                )
            ) {
                NewTabButton(
                    text = stringResource(R.string.new_tab),
                    iconRes = Icons.Filled.Add,
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    onClick = { activity.onNewTab(); onDismiss() }
                )
                NewTabButton(
                    text = stringResource(R.string.new_incognito_tab),
                    iconRes = Icons.Filled.VisibilityOff,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    onClick = { activity.onNewIncognitoTab(); onDismiss() }
                )
            }
        }

        val groupChips: @Composable () -> Unit = {
            TabGroupChipsRow(
                groups = shownGroups,
                counts = groupCounts,
                activeGroupId = activeGroupId,
                hoverKey = dragState.hoverKey,
                onChipClick = { revealGroup(it.id) },
                onNewGroup = { editorRequest = TabGroupEditorRequest.CreateWithNewTab(false) },
                onChipBounds = { id, bounds -> if (bounds == null) dragState.chipBounds.remove(id) else dragState.chipBounds[id] = bounds },
                modifier = Modifier.padding(top = if (buttonsAtBottom) 6.dp else 0.dp, bottom = 6.dp)
            )
        }

        val quickActions: @Composable ColumnScope.() -> Unit = {
            AnimatedVisibility(
                visible = !uiState.selectionMode && !searchActive,
                enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.85f)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(150))
            ) {
                Column {
                    if (buttonsAtBottom) {
                        if (groupsEnabled) groupChips()
                        newTabButtons()
                    } else {
                        newTabButtons()
                        if (groupsEnabled) groupChips()
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            if (!buttonsAtBottom) {
                topBar()
                quickActions()
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { dragState.gridOriginInRoot = it.positionInRoot() }
                    .pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (!dragState.active) {
                                        dragSession.reset()
                                        continue
                                    }
                                    // A handle drag learns its pointer on its first move; until then
                                    // leave every finger alone so the handle's detector is not starved.
                                    val primary = dragState.primaryPointerId ?: continue
                                    var moved = false
                                    for (change in event.changes) {
                                        if (change.id == primary) {
                                            // A drag started from a drag handle keeps its own detector.
                                            if (dragSession.pointerId != primary) continue
                                            if (change.pressed) {
                                                val delta = change.positionChange()
                                                if (delta != Offset.Zero) {
                                                    dragSession.travelled += delta.getDistance()
                                                    dragState.drag(delta)
                                                    moved = true
                                                }
                                                change.consume()
                                            } else {
                                                // Compose delivers a cancelled gesture as an already
                                                // consumed release: put the preview back, commit nothing.
                                                val cancelled = change.isConsumed
                                                change.consume()
                                                val asLongPress = dragSession.travelled < slop && !dragSession.scrolled
                                                val onDrop = dragSession.onDrop
                                                val onLongPress = dragSession.onLongPress
                                                dragSession.reset()
                                                if (cancelled) {
                                                    dragCallbacks.cancel()
                                                } else if (asLongPress) {
                                                    dragCallbacks.cancel()
                                                    onLongPress()
                                                } else {
                                                    onDrop()
                                                }
                                            }
                                        } else if (change.pressed) {
                                            // Any other finger scrolls the list while the card stays under the first one.
                                            val dy = change.position.y - change.previousPosition.y
                                            if (change.previousPressed && dy != 0f && gridState.dispatchRawDelta(-dy) != 0f) {
                                                dragSession.scrolled = true
                                                dragState.drag(Offset.Zero)
                                                moved = true
                                            }
                                            change.consume()
                                        }
                                    }
                                    if (moved && dragState.active) dragCallbacks.moved()
                                }
                            }
                        } finally {
                            // Handler torn down mid-drag: drop the preview instead of leaving it lifted.
                            if (dragSession.pointerId != null) {
                                dragSession.reset()
                                dragCallbacks.cancel()
                            }
                        }
                    }
            ) {
                if (tabs.isEmpty()) {
                    Text(stringResource(R.string.no_tabs), color = colors.secondaryText, modifier = Modifier.align(Alignment.Center))
                } else if (renderList.isEmpty()) {
                    Text(
                        stringResource(R.string.tab_menu_search_no_results),
                        color = colors.secondaryText,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(GridEdgePadding),
                        verticalArrangement = if (tabsAtBottom) Arrangement.Bottom else Arrangement.Top
                    ) {
                        items(
                            items = renderList,
                            key = { it.key },
                            span = { item -> if (item.fullSpan) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                            contentType = { it::class }
                        ) { item ->
                            AnimatedGridItem {
                                when (item) {
                                    is TabMenuItem.SectionHeader -> TabMenuSectionHeader(
                                        isIncognitoSection = item.isIncognitoSection,
                                        modifier = Modifier.padding(horizontal = CellPadding)
                                    )
                                    is TabMenuItem.GroupHeader -> {
                                        val group = item.group
                                        val groupTabIds = item.tabs.map { it.id }
                                        val isDraggingGroup = dragState.draggingGroupId == group.id
                                        val pinnableContainer = LocalPinnableContainer.current
                                        DisposableEffect(isDraggingGroup) {
                                            val pinHandle = if (isDraggingGroup) pinnableContainer?.pin() else null
                                            onDispose { pinHandle?.release() }
                                        }
                                        val headerAlpha by animateFloatAsState(if (isDraggingGroup) 0.25f else 1f, label = "groupGhostAlpha")
                                        TabGroupHeader(
                                            group = group,
                                            tabs = item.tabs,
                                            containsActive = groupTabIds.contains(activeTabId),
                                            highlighted = dragState.draggingId != null && dropTargetGroupId(dragState.hoverKey) == group.id,
                                            selectionMode = uiState.selectionMode,
                                            allSelected = groupTabIds.isNotEmpty() && groupTabIds.all { uiState.selectedIds.contains(it) },
                                            onToggleCollapsed = { toggleCollapsed(group) },
                                            onLongPress = { uiState.setSelected(groupTabIds, true) },
                                            onToggleSelection = {
                                                uiState.setSelected(groupTabIds, !groupTabIds.all { uiState.selectedIds.contains(it) })
                                            },
                                            onNewTab = { activity.openNewTabInGroup(group.id); onDismiss() },
                                            onEdit = { editorRequest = TabGroupEditorRequest.Edit(group) },
                                            onSelectTabs = { uiState.setSelected(groupTabIds, true) },
                                            onUngroup = { commitGroups { ungroup(group.id) } },
                                            onCloseGroup = {
                                                closeTabs(
                                                    groupTabIds,
                                                    activity.getString(R.string.tab_group_closed_message, group.name.ifBlank { activity.getString(R.string.tab_group_untitled) })
                                                )
                                            },
                                            modifier = Modifier
                                                .padding(top = 6.dp, bottom = if (group.collapsed) 6.dp else 0.dp)
                                                .alpha(headerAlpha),
                                            dragHandleModifier = if (searching) null else Modifier
                                                .onGloballyPositioned { dragState.handleOrigins[groupHeaderKey(group.id)] = it.positionInRoot() }
                                                .pointerInput(group.id) {
                                                    var owned = false
                                                    detectDragGestures(
                                                        onDragStart = { offset -> owned = dragState.startGroup(group.id, offset) },
                                                        onDrag = { change, amount ->
                                                            if (owned) {
                                                                if (dragState.primaryPointerId == null) dragState.primaryPointerId = change.id
                                                                change.consume(); dragState.drag(amount); onGroupDragMoved()
                                                            }
                                                        },
                                                        onDragEnd = { if (owned) { owned = false; commitGroupDrag() } },
                                                        onDragCancel = { if (owned) { owned = false; cancelDrag() } }
                                                    )
                                                },
                                            longPressDragModifier = if (dragAllowed) {
                                                val groupId = group.id
                                                Modifier.longPressDrag(
                                                    originKey = groupCardOriginKey(groupId),
                                                    onLift = { offset, pointer -> dragState.startGroup(groupId, offset, groupCardOriginKey(groupId), pointer) },
                                                    onDrop = { commitGroupDrag() },
                                                    onLongPress = { uiState.setSelected(tabs.filter { it.groupId == groupId }.map { it.id }, true) }
                                                )
                                            } else {
                                                null
                                            }
                                        )
                                    }
                                    is TabMenuItem.GroupFiller -> Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(tabGroupTint(item.group.color, colors.isLight))
                                            .padding(CellPadding)
                                    ) {
                                        Spacer(if (listView) Modifier.fillMaxWidth().height(TabListRowHeight) else Modifier.fillMaxWidth().aspectRatio(0.76f))
                                    }
                                    is TabMenuItem.RowGap -> Spacer(Modifier.fillMaxWidth())
                                    is TabMenuItem.GroupFooter -> Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                            .height(10.dp)
                                            .background(
                                                tabGroupTint(item.group.color, colors.isLight),
                                                RoundedCornerShape(bottomStart = GroupCorner, bottomEnd = GroupCorner)
                                            )
                                    )
                                    is TabMenuItem.Tab -> {
                                        val preview = item.preview
                                        val isDragging = dragState.draggingId == preview.id
                                        val pinnableContainer = LocalPinnableContainer.current
                                        DisposableEffect(isDragging) {
                                            val pinHandle = if (isDragging) pinnableContainer?.pin() else null
                                            onDispose { pinHandle?.release() }
                                        }
                                        val ghostAlpha by animateFloatAsState(if (isDragging) 0.25f else 1f, label = "ghostAlpha")
                                        val mergeHighlighted = draggingPreview != null &&
                                            mergeTargetTabId(dragState.hoverKey) == preview.id &&
                                            canMergeTabs(draggingPreview, preview)
                                        val handleModifier = Modifier
                                            .onGloballyPositioned { dragState.handleOrigins[preview.id] = it.positionInRoot() }
                                            .pointerInput(preview.id) {
                                                var owned = false
                                                detectDragGestures(
                                                    onDragStart = { offset -> owned = dragState.start(preview.id, offset) },
                                                    onDrag = { change, amount ->
                                                        if (owned) {
                                                            if (dragState.primaryPointerId == null) dragState.primaryPointerId = change.id
                                                            change.consume(); dragState.drag(amount); onDragMoved()
                                                        }
                                                    },
                                                    onDragEnd = { if (owned) { owned = false; commitDrag() } },
                                                    onDragCancel = { if (owned) { owned = false; cancelDrag() } }
                                                )
                                            }
                                        val cardDragModifier = if (dragAllowed) {
                                            val tabId = preview.id
                                            Modifier.longPressDrag(
                                                originKey = tabCardOriginKey(tabId),
                                                onLift = { offset, pointer -> dragState.start(tabId, offset, tabCardOriginKey(tabId), pointer) },
                                                onDrop = { commitDrag() },
                                                onLongPress = { uiState.enterSelectionMode(tabId) }
                                            )
                                        } else {
                                            null
                                        }
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .then(item.group?.let { Modifier.background(tabGroupTint(it.color, colors.isLight)) } ?: Modifier)
                                                .padding(CellPadding)
                                        ) {
                                            SwipeToCloseContainer(
                                                enabled = !uiState.selectionMode && !dragState.active,
                                                onClose = { closeTabs(listOf(preview.id)) }
                                            ) {
                                                if (listView) {
                                                    TabMenuListRow(
                                                        preview = preview,
                                                        isActive = preview.id == activeTabId,
                                                        selectionMode = uiState.selectionMode,
                                                        selected = uiState.selectedIds.contains(preview.id),
                                                        dragHandleModifier = handleModifier,
                                                        onOpen = { openTab(preview.id) },
                                                        onEnterSelection = { uiState.enterSelectionMode(preview.id) },
                                                        onToggleSelect = { uiState.toggleSelected(preview.id) },
                                                        onClose = { closeTabs(listOf(preview.id)) },
                                                        onToggleMute = { activity.toggleTabMute(preview.id) },
                                                        onTogglePin = { commitGroups { setPinned(listOf(preview.id), !preview.isPinned) } },
                                                        mergeHighlighted = mergeHighlighted,
                                                        showDragHandle = !searching,
                                                        longPressDragModifier = cardDragModifier,
                                                        modifier = Modifier.alpha(ghostAlpha)
                                                    )
                                                } else {
                                                    val thumbnail by produceState(TabThumbnailCache.peek(preview.id), preview.id) {
                                                        if (value == null) value = withContext(Dispatchers.IO) { TabThumbnailCache.get(activity, preview.id) }
                                                    }
                                                    TabMenuCard(
                                                        preview = preview,
                                                        isActive = preview.id == activeTabId,
                                                        thumbnail = thumbnail,
                                                        selectionMode = uiState.selectionMode,
                                                        selected = uiState.selectedIds.contains(preview.id),
                                                        dragHandleModifier = handleModifier,
                                                        onOpen = { openTab(preview.id) },
                                                        onEnterSelection = { uiState.enterSelectionMode(preview.id) },
                                                        onToggleSelect = { uiState.toggleSelected(preview.id) },
                                                        onClose = { closeTabs(listOf(preview.id)) },
                                                        onToggleMute = { activity.toggleTabMute(preview.id) },
                                                        onTogglePin = { commitGroups { setPinned(listOf(preview.id), !preview.isPinned) } },
                                                        mergeHighlighted = mergeHighlighted,
                                                        showDragHandle = !searching,
                                                        longPressDragModifier = cardDragModifier,
                                                        modifier = Modifier.alpha(ghostAlpha)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val draggedPreview = draggingPreview
                    if (draggedPreview != null) {
                        val cardWidth = with(density) { dragState.originSize.width.toDp() }
                        val cardHeight = with(density) { dragState.originSize.height.toDp() }
                        // Starts at 1f each time the overlay appears, so lifting visibly pops up.
                        val liftScale = remember { Animatable(1f) }
                        LaunchedEffect(Unit) { liftScale.animateTo(1.06f, spring(dampingRatio = 0.5f, stiffness = 900f)) }
                        Box(
                            Modifier
                                // Lambda offset: following the finger only re-places the overlay,
                                // no recomposition or re-measure per frame.
                                .offset { (dragState.originOffset + dragState.dragOffset).round() }
                                .size(cardWidth, cardHeight)
                                .padding(CellPadding)
                                // Translucent while lifted so the slot and merge zone underneath stay visible;
                                // the layer composites shadow + card together, so the shadow doesn't show through.
                                .graphicsLayer {
                                    scaleX = liftScale.value
                                    scaleY = liftScale.value
                                    alpha = 1f - (liftScale.value - 1f) / 0.06f * 0.3f
                                }
                                .zIndex(10f)
                                .shadow(12.dp, RoundedCornerShape(if (listView) 14.dp else 16.dp))
                        ) {
                            if (listView) {
                                TabMenuListRow(
                                    preview = draggedPreview,
                                    isActive = draggedPreview.id == activeTabId,
                                    selectionMode = false,
                                    selected = false,
                                    dragHandleModifier = Modifier,
                                    onOpen = {},
                                    onEnterSelection = {},
                                    onToggleSelect = {},
                                    onClose = {}
                                )
                            } else {
                                val thumbnail by produceState(TabThumbnailCache.peek(draggedPreview.id), draggedPreview.id) {
                                    if (value == null) value = withContext(Dispatchers.IO) { TabThumbnailCache.get(activity, draggedPreview.id) }
                                }
                                TabMenuCard(
                                    preview = draggedPreview,
                                    isActive = draggedPreview.id == activeTabId,
                                    thumbnail = thumbnail,
                                    selectionMode = false,
                                    selected = false,
                                    dragHandleModifier = Modifier,
                                    onOpen = {},
                                    onEnterSelection = {},
                                    onToggleSelect = {},
                                    onClose = {}
                                )
                            }
                        }

                        // Merge zone sits above the lifted card so the translucent card never hides it.
                        val zoneTabId = dragState.zoneTabId
                            ?: holdTargetTabId(dragState.hoverKey)
                            ?: mergeTargetTabId(dragState.hoverKey)
                        val zoneItem = zoneTabId?.let { id ->
                            gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tabItemKey(id) }
                        }
                        if (zoneTabId != null && zoneItem != null) {
                            androidx.compose.runtime.key(zoneTabId) {
                                DragZoneOverlay(
                                    dragState = dragState,
                                    tabId = zoneTabId,
                                    accent = colors.primary,
                                    listRow = listView,
                                    modifier = Modifier
                                        .offset {
                                            val info = gridState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == tabItemKey(zoneTabId) }
                                            val o = info?.offset ?: zoneItem.offset
                                            (Offset(o.x.toFloat(), o.y.toFloat()) + dragState.contentPaddingOffset).round()
                                        }
                                        .size(
                                            with(density) { zoneItem.size.width.toDp() },
                                            with(density) { zoneItem.size.height.toDp() }
                                        )
                                        .padding(CellPadding)
                                        .zIndex(11f)
                                )
                            }
                        }
                    }

                    val draggedGroup = dragState.draggingGroupId?.let { id -> groups.find { it.id == id } }
                    if (draggedGroup != null) {
                        val members = tabs.filter { it.groupId == draggedGroup.id }
                        val headerWidth = with(density) { dragState.originSize.width.toDp() }
                        val liftScale = remember { Animatable(1f) }
                        LaunchedEffect(Unit) { liftScale.animateTo(1.03f, spring(dampingRatio = 0.5f, stiffness = 900f)) }
                        Box(
                            Modifier
                                .offset { (dragState.originOffset + dragState.dragOffset).round() }
                                .width(headerWidth)
                                .padding(vertical = 6.dp)
                                .graphicsLayer { scaleX = liftScale.value; scaleY = liftScale.value }
                                .zIndex(10f)
                                .shadow(12.dp, RoundedCornerShape(18.dp))
                        ) {
                            TabGroupHeader(
                                group = draggedGroup.copy(collapsed = true),
                                tabs = members,
                                containsActive = members.any { it.id == activeTabId },
                                highlighted = false,
                                selectionMode = false,
                                allSelected = false,
                                onToggleCollapsed = {},
                                onLongPress = {},
                                onToggleSelection = {},
                                onNewTab = {},
                                onEdit = {},
                                onSelectTabs = {},
                                onUngroup = {},
                                onCloseGroup = {},
                                dragHandleModifier = Modifier
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.selectionMode,
                enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.85f)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(150))
            ) {
                val selectedIds = uiState.selectedIds.toList()
                TabSelectionActionBar(
                    hasSelection = selectedIds.isNotEmpty(),
                    canUngroup = tabs.any { it.id in selectedIds && it.groupId != null },
                    groupsEnabled = groupsEnabled,
                    onGroup = { editorRequest = TabGroupEditorRequest.CreateFromTabs(selectedIds) },
                    onMove = { showMovePicker = true },
                    onUngroup = {
                        commitGroups { removeFromGroup(selectedIds) }
                        uiState.exitSelectionMode()
                    },
                    onClose = {
                        closeTabs(selectedIds)
                        uiState.exitSelectionMode()
                    }
                )
            }

            if (buttonsAtBottom) {
                quickActions()
                topBar()
            }
        }
    }
}

@Composable
private fun LazyGridItemScope.AnimatedGridItem(content: @Composable () -> Unit) {
    Box(
        Modifier.animateItem(
            fadeInSpec = tween(220),
            placementSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
            fadeOutSpec = tween(150)
        )
    ) {
        content()
    }
}

@Composable
private fun TabMenuTopBar(
    tabCount: Int,
    selectionMode: Boolean,
    selectedCount: Int,
    groupsEnabled: Boolean,
    hasGroups: Boolean,
    allGroupsCollapsed: Boolean,
    showGroupBar: Boolean,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    listView: Boolean,
    onToggleListView: () -> Unit,
    onCloseDuplicates: (() -> Unit)?,
    onSortTabs: (() -> Unit)?,
    onGroupBySite: (() -> Unit)?,
    onRecentlyClosed: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onNewTabGroup: () -> Unit,
    onToggleGroupBar: () -> Unit,
    onCollapseAllGroups: () -> Unit,
    onExpandAllGroups: () -> Unit,
    onNewProfileTab: () -> Unit,
    onCloseAllTabs: () -> Unit,
    buttonsAtBottom: Boolean,
    onToggleButtonsAtBottom: () -> Unit,
    tabsAtBottom: Boolean,
    onToggleTabsAtBottom: () -> Unit
) {
    val colors = LocalClintColors.current
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(
            start = 20.dp,
            end = 8.dp,
            top = if (buttonsAtBottom) 4.dp else 8.dp,
            bottom = if (buttonsAtBottom) 8.dp else 4.dp
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searchActive && !selectionMode) {
            ClintSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                hint = stringResource(R.string.tab_menu_search_hint),
                onClose = onCloseSearch
            )
        } else {
            AnimatedContent(
                targetState = selectionMode,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
                },
                label = "tabMenuTitle"
            ) { inSelectionMode ->
                Text(
                    if (inSelectionMode) {
                        pluralStringResource(R.plurals.tab_menu_selected_count, selectedCount, selectedCount)
                    } else if (tabCount == 0) {
                        stringResource(R.string.no_tabs)
                    } else {
                        pluralStringResource(R.plurals.tab_menu_tab_count, tabCount, tabCount)
                    },
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!selectionMode) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.tab_menu_search_hint), tint = colors.primary)
                }
            }
            AnimatedContent(
                targetState = selectionMode,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "tabMenuActions"
            ) { inSelectionMode ->
                Box {
                    IconButton(onClick = { overflowMenuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.tab_menu_overflow_desc), tint = colors.primary)
                    }
                    if (inSelectionMode) {
                        TabSelectionOverflowMenu(
                            expanded = overflowMenuExpanded,
                            onDismiss = { overflowMenuExpanded = false },
                            onSelectAll = onSelectAll,
                            onInvertSelection = onInvertSelection,
                            onDeleteSelected = onDeleteSelected
                        )
                    } else {
                        TabMenuOverflowMenu(
                            expanded = overflowMenuExpanded,
                            onDismiss = { overflowMenuExpanded = false },
                            onNewTab = onNewTab,
                            onNewIncognitoTab = onNewIncognitoTab,
                            onCloseAllTabs = onCloseAllTabs,
                            onSelectTabs = onToggleSelectionMode,
                            showProfileTab = com.jhaiian.clint.profiles.WebProfiles.isSupported(),
                            onNewProfileTab = onNewProfileTab,
                            onNewTabGroup = if (groupsEnabled) onNewTabGroup else null,
                            showGroupBar = showGroupBar,
                            onToggleGroupBar = if (groupsEnabled) onToggleGroupBar else null,
                            onCollapseAllGroups = if (hasGroups && !allGroupsCollapsed) onCollapseAllGroups else null,
                            onExpandAllGroups = if (hasGroups && allGroupsCollapsed) onExpandAllGroups else null,
                            onCloseDuplicates = onCloseDuplicates,
                            onSortTabs = onSortTabs,
                            onGroupBySite = onGroupBySite,
                            onRecentlyClosed = onRecentlyClosed,
                            listView = listView,
                            onToggleListView = onToggleListView,
                            buttonsAtBottom = buttonsAtBottom,
                            onToggleButtonsAtBottom = onToggleButtonsAtBottom,
                            tabsAtBottom = tabsAtBottom,
                            onToggleTabsAtBottom = onToggleTabsAtBottom
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shows where a dragged tab will land on the tab under the finger: an edge bar on the near
 * side (drop next to it), a dashed centre box that fills while the finger rests there, and a
 * solid box once letting go would group the two tabs.
 */
@Composable
private fun DragZoneOverlay(
    dragState: TabDragState,
    tabId: String,
    accent: Color,
    listRow: Boolean,
    modifier: Modifier = Modifier
) {
    val shown = dragState.zoneTabId == tabId
    val holding = holdTargetTabId(dragState.hoverKey) == tabId
    val merging = mergeTargetTabId(dragState.hoverKey) == tabId
    val edge = if (shown && !holding && !merging) dragState.zoneEdge else 0
    val visible by animateFloatAsState(if (shown || merging) 1f else 0f, tween(120), label = "zoneAlpha")
    val fill = remember(tabId) { Animatable(0f) }
    val holdSince = dragState.holdSince
    LaunchedEffect(holding, merging, holdSince) {
        when {
            merging -> fill.animateTo(1f, tween(80))
            holding -> {
                val elapsed = (SystemClock.uptimeMillis() - holdSince).coerceAtLeast(0L)
                val remaining = (dragState.mergeDwellMs - elapsed).coerceAtLeast(0L).toInt()
                fill.animateTo(1f, tween(remaining, easing = LinearEasing))
            }
            else -> fill.snapTo(0f)
        }
    }
    if (visible <= 0f) return
    val density = LocalDensity.current
    val edgeVertical = dragState.zoneEdgeVertical
    val label = when {
        merging -> stringResource(R.string.tab_drag_zone_release)
        holding -> stringResource(R.string.tab_drag_zone_hold)
        else -> stringResource(R.string.tab_drag_zone_beside)
    }
    Box(modifier.graphicsLayer { alpha = visible }) {
    Canvas(Modifier.matchParentSize()) {
        val w = size.width
        val h = size.height
        val left = if (listRow) w * 0.04f else w * 0.25f
        val right = if (listRow) w * 0.96f else w * 0.75f
        val top = h * 0.25f
        val bottom = h * 0.75f
        val radius = with(density) { 12.dp.toPx() }
        val stroke = with(density) { 2.dp.toPx() }
        val active = holding || merging
        val zoneSize = Size(right - left, bottom - top)
        if (fill.value > 0f) {
            val path = Path().apply {
                addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(radius, radius)))
            }
            clipPath(path) {
                val filled = (bottom - top) * fill.value
                drawRect(
                    color = accent.copy(alpha = if (merging) 0.55f else 0.32f),
                    topLeft = Offset(left, bottom - filled),
                    size = Size(right - left, filled)
                )
            }
        }
        drawRoundRect(
            color = accent.copy(alpha = if (active) 1f else 0.5f),
            topLeft = Offset(left, top),
            size = zoneSize,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(
                width = stroke,
                pathEffect = if (merging) null else PathEffect.dashPathEffect(
                    floatArrayOf(with(density) { 6.dp.toPx() }, with(density) { 4.dp.toPx() })
                )
            )
        )
        if (edge != 0) {
            // Inside this cell's own padding, so a neighbour's group tint can't cover it.
            val gap = with(density) { CellPadding.toPx() }
            val bar = with(density) { 4.dp.toPx() }
            if (edgeVertical) {
                val x = if (edge < 0) -gap / 2f - bar / 2f else w + gap / 2f - bar / 2f
                drawRoundRect(accent, Offset(x, h * 0.1f), Size(bar, h * 0.8f), CornerRadius(bar / 2f, bar / 2f))
            } else {
                val y = if (edge < 0) -gap / 2f - bar / 2f else h + gap / 2f - bar / 2f
                drawRoundRect(accent, Offset(w * 0.1f, y), Size(w * 0.8f, bar), CornerRadius(bar / 2f, bar / 2f))
            }
        }
    }
    Text(
        text = label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .align(Alignment.Center)
            .background(accent.copy(alpha = if (holding || merging) 0.95f else 0.8f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
    }
}

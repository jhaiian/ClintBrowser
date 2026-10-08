package com.jhaiian.clint.userscripts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import com.jhaiian.clint.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import com.jhaiian.clint.ui.ClintSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhaiian.clint.settings.common.SettingsRow
import com.jhaiian.clint.ui.AdaptiveWidthContainer
import com.jhaiian.clint.ui.rememberClintFavicon
import com.jhaiian.clint.ui.listscreen.ClintSearchField
import com.jhaiian.clint.ui.listscreen.ListFastScroller
import com.jhaiian.clint.ui.listscreen.SelectionOptionsMenu
import com.jhaiian.clint.ui.listscreen.ListSortKey
import com.jhaiian.clint.ui.listscreen.SortMenu
import com.jhaiian.clint.ui.listscreen.ListSortOrder
import com.jhaiian.clint.ui.theme.LocalClintColors

@Composable
fun UserScriptsScreen(
    state: UserScriptsUiState,
    maxContentWidth: Dp?,
    hideStatusBar: Boolean, hideSystemNavigation: Boolean,
    onExit: () -> Unit,
    onToggleMasterEnabled: (Boolean) -> Unit,
    onCreateNew: () -> Unit,
    onUpload: () -> Unit,
    onAddFromLink: () -> Unit,
    onEditScript: (UserScript) -> Unit,
    onToggleScriptEnabled: (UserScript, Boolean) -> Unit,
    onDeleteSelected: () -> Unit,
    onRefreshClick: () -> Unit,
    onScriptActionsClick: () -> Unit,
    onItemCheckUpdate: (UserScript) -> Unit,
    onItemForceUpdate: (UserScript) -> Unit,
    onItemRemove: (UserScript) -> Unit,
    onItemCopyName: (UserScript) -> Unit,
    onItemCopyLink: (UserScript) -> Unit,
    onItemShareLink: (UserScript) -> Unit,
    onSelectionCheckUpdate: () -> Unit,
    onSelectionForceUpdate: () -> Unit,
    onSelectionCopyName: () -> Unit,
    onSelectionCopyLink: () -> Unit,
    onSelectionShareLink: () -> Unit,
    onCheckUpdateActive: () -> Unit,
    onCheckUpdateAll: () -> Unit,
    onForceUpdateActive: () -> Unit,
    onForceUpdateAll: () -> Unit
) {
    val colors = LocalClintColors.current
    val allItems = remember(state.scripts) { buildListItems(state.scripts) }
    val displayed = remember(allItems, state.searchQuery, state.sortKey, state.sortOrder) {
        filterAndSortUserScripts(allItems, state.searchQuery, state.sortKey, state.sortOrder)
    }
    val listState = rememberLazyListState()
    val locked = state.isUpdateRunning
    val fastScrollerInteractive = !state.isSearchMode && state.sortKey == ListSortKey.TITLE
    val showDeleteFab = state.isInSelectionMode && state.selectedIds.isNotEmpty()
    val showAddFabs = !state.isInSelectionMode

    fun handleBack() {
        when {
            state.isFabMenuOpen -> state.isFabMenuOpen = false
            state.isSearchMode -> { state.isSearchMode = false; state.searchQuery = "" }
            state.isInSelectionMode -> state.exitSelectionMode()
            else -> onExit()
        }
    }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            UserScriptsToolbar(
                state = state,
                onBack = ::handleBack,
                onSelectAll = { state.selectAll(displayed) },
                onInvertSelection = { state.invertSelection(displayed) },
                onRefreshClick = onRefreshClick,
                onScriptActionsClick = onScriptActionsClick,
                onSelectionCheckUpdate = onSelectionCheckUpdate,
                onSelectionForceUpdate = onSelectionForceUpdate,
                onSelectionRemove = onDeleteSelected,
                onSelectionCopyName = onSelectionCopyName,
                onSelectionCopyLink = onSelectionCopyLink,
                onSelectionShareLink = onSelectionShareLink,
                onCheckUpdateActive = onCheckUpdateActive,
                onCheckUpdateAll = onCheckUpdateAll,
                onForceUpdateActive = onForceUpdateActive,
                onForceUpdateAll = onForceUpdateAll
            )
            HorizontalDivider(color = colors.divider, thickness = 1.dp)

            state.bannerText?.let { message ->
                Row(
                    Modifier.fillMaxWidth().background(colors.colorErrorContainer).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Warning, contentDescription = null, tint = colors.colorError, modifier = Modifier.size(20.dp))
                    Text(message, color = colors.colorOnErrorContainer, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }

            MasterSwitchRow(masterEnabled = state.isEnabled, onToggle = onToggleMasterEnabled)

            Text(
                stringResource(R.string.user_scripts_section_scripts),
                color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp).alpha(if (state.isEnabled) 1f else 0.38f)
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (displayed.isEmpty()) {
                    Text(
                        stringResource(R.string.user_scripts_empty),
                        color = colors.secondaryText, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                } else {
                    AdaptiveWidthContainer(maxContentWidth) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 2.dp, bottom = 100.dp)) {
                            items(displayed, key = { it.script.id }) { item ->
                                UserScriptRow(
                                    item = item,
                                    isSelected = item.script.id in state.selectedIds,
                                    isUpdating = item.script.id in state.updatingIds,
                                    masterEnabled = state.isEnabled,
                                    interactionLocked = locked,
                                    isInSelectionMode = state.isInSelectionMode,
                                    onClick = {
                                        if (state.isInSelectionMode) state.toggleSelection(item.script.id) else onEditScript(item.script)
                                    },
                                    onLongClick = {
                                        if (!state.isInSelectionMode) state.enterSelectionMode(item.script.id)
                                        else if (item.script.id !in state.selectedIds) state.toggleSelection(item.script.id)
                                    },
                                    onToggleEnabled = { enabled -> onToggleScriptEnabled(item.script, enabled) },
                                    onCheckUpdate = { onItemCheckUpdate(item.script) },
                                    onForceUpdate = { onItemForceUpdate(item.script) },
                                    onRemove = { onItemRemove(item.script) },
                                    onCopyName = { onItemCopyName(item.script) },
                                    onCopyLink = { onItemCopyLink(item.script) },
                                    onShareLink = { onItemShareLink(item.script) }
                                )
                            }
                        }
                        ListFastScroller(
                            listState = listState,
                            itemCount = displayed.size,
                            isInteractive = fastScrollerInteractive,
                            sectionLetterAt = { index -> sectionLetterForUserScript(displayed[index], state.sortKey) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                UserScriptsFabMenu(
                    isOpen = state.isFabMenuOpen,
                    enabled = state.isEnabled && !locked,
                    showDeleteFab = showDeleteFab,
                    showAddFabs = showAddFabs,
                    onScrimClick = { state.isFabMenuOpen = false },
                    onToggleMenu = { state.isFabMenuOpen = !state.isFabMenuOpen },
                    onDeleteClick = onDeleteSelected,
                    onLinkClick = { state.isFabMenuOpen = false; onAddFromLink() },
                    onCreateClick = { state.isFabMenuOpen = false; onCreateNew() },
                    onUploadClick = { state.isFabMenuOpen = false; onUpload() }
                )
            }
        }
    }
}

@Composable
private fun MasterSwitchRow(masterEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalClintColors.current
    SettingsRow(
        icon = androidx.compose.material.icons.Icons.Filled.Code,
        title = stringResource(R.string.user_scripts_master_switch_title),
        summary = stringResource(R.string.user_scripts_master_switch_summary),
        colors = colors,
        onClick = { onToggle(!masterEnabled) },
        trailing = {
            ClintSwitch(checked = masterEnabled)
        }
    )
}

@Composable
private fun UserScriptsToolbar(
    state: UserScriptsUiState,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onRefreshClick: () -> Unit,
    onScriptActionsClick: () -> Unit,
    onSelectionCheckUpdate: () -> Unit,
    onSelectionForceUpdate: () -> Unit,
    onSelectionRemove: () -> Unit,
    onSelectionCopyName: () -> Unit,
    onSelectionCopyLink: () -> Unit,
    onSelectionShareLink: () -> Unit,
    onCheckUpdateActive: () -> Unit,
    onCheckUpdateAll: () -> Unit,
    onForceUpdateActive: () -> Unit,
    onForceUpdateAll: () -> Unit
) {
    val colors = LocalClintColors.current
    val showToolbarIcons = !state.isInSelectionMode && !state.isSearchMode
    var selectionItemOptionsMenuOpen by remember { mutableStateOf(false) }

    Surface(color = colors.surface, shadowElevation = 4.dp, modifier = Modifier.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    if (state.isInSelectionMode) androidx.compose.material.icons.Icons.Filled.Close else androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(if (state.isInSelectionMode) R.string.history_cancel_selection_desc else R.string.back),
                    tint = colors.onSurface
                )
            }

            if (state.isSearchMode) {
                ClintSearchField(
                    query = state.searchQuery,
                    onQueryChange = { state.searchQuery = it },
                    hint = stringResource(R.string.user_scripts_search_hint),
                    onClose = { state.isSearchMode = false; state.searchQuery = "" }
                )
            } else {
                Text(
                    text = if (state.isInSelectionMode) stringResource(R.string.history_selected_count, state.selectedIds.size) else stringResource(R.string.user_scripts_title),
                    color = colors.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }

            if (showToolbarIcons) {
                IconButton(onClick = { state.isSearchMode = true }) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Search, contentDescription = stringResource(R.string.history_search), tint = colors.iconTint)
                }
                IconButton(onClick = onRefreshClick) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Update, contentDescription = null, tint = colors.iconTint)
                }
                Box {
                    IconButton(onClick = { state.sortMenuOpen = true }) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = colors.iconTint)
                    }
                    SortMenu(
                        expanded = state.sortMenuOpen,
                        onDismiss = { state.sortMenuOpen = false },
                        sortKey = state.sortKey, sortOrder = state.sortOrder,
                        onSortByTitle = { state.sortKey = ListSortKey.TITLE; state.sortOrder = ListSortOrder.ASCENDING },
                        onSortByDateAdded = { state.sortKey = ListSortKey.DATE_ADDED; state.sortOrder = ListSortOrder.DESCENDING },
                        onSortAscending = { state.sortOrder = ListSortOrder.ASCENDING },
                        onSortDescending = { state.sortOrder = ListSortOrder.DESCENDING }
                    )
                }
                Box {
                    IconButton(onClick = onScriptActionsClick) {
                        Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = stringResource(R.string.history_more_options), tint = colors.iconTint)
                    }
                    UserScriptActionsMenu(
                        expanded = state.scriptActionsMenuOpen,
                        onDismiss = { state.scriptActionsMenuOpen = false },
                        onCheckUpdateActive = onCheckUpdateActive,
                        onCheckUpdateAll = onCheckUpdateAll,
                        onForceUpdateActive = onForceUpdateActive,
                        onForceUpdateAll = onForceUpdateAll
                    )
                }
            }
            if (state.isInSelectionMode) {
                Box {
                    IconButton(onClick = { state.selectionOptionsMenuOpen = true }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Checklist, contentDescription = stringResource(R.string.history_more_options), tint = colors.primary)
                    }
                    SelectionOptionsMenu(
                        expanded = state.selectionOptionsMenuOpen,
                        onDismiss = { state.selectionOptionsMenuOpen = false },
                        onSelectAll = onSelectAll, onInvertSelection = onInvertSelection,
                        onDeselectAll = { state.deselectAll() }
                    )
                }
                if (state.selectedIds.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { selectionItemOptionsMenuOpen = true }) {
                            Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = null, tint = colors.iconTint)
                        }
                        UserScriptItemOptionsMenu(
                            expanded = selectionItemOptionsMenuOpen,
                            isLocal = false,
                            onDismiss = { selectionItemOptionsMenuOpen = false },
                            onCheckUpdate = onSelectionCheckUpdate,
                            onForceUpdate = onSelectionForceUpdate,
                            onRemove = onSelectionRemove,
                            onCopyName = onSelectionCopyName,
                            onCopyLink = onSelectionCopyLink,
                            onShareLink = onSelectionShareLink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserScriptRow(
    item: UserScriptListItem,
    isSelected: Boolean,
    isUpdating: Boolean,
    masterEnabled: Boolean,
    interactionLocked: Boolean,
    isInSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onForceUpdate: () -> Unit,
    onRemove: () -> Unit,
    onCopyName: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit
) {
    var optionsMenuOpen by remember { mutableStateOf(false) }
    val colors = LocalClintColors.current
    val cardColor = if (isSelected) lerp(colors.cardBackground, colors.primary, 0.22f) else colors.cardBackground
    val rowAlpha = when {
        !masterEnabled -> 0.38f
        isUpdating -> 0.6f
        !item.script.enabled -> 0.6f
        else -> 1f
    }
    val patternCount = matchPatternCount(item.metadata)
    val subtitle = item.metadata.description.ifBlank {
        if (patternCount == 0) stringResource(R.string.user_scripts_runs_all_sites)
        else stringResource(R.string.user_scripts_runs_on_patterns, patternCount)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .alpha(rowAlpha)
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .combinedClickable(enabled = masterEnabled && !interactionLocked, onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val scriptIconUrl = remember(item.metadata.icon, item.script.sourceUrl) {
            resolveUserScriptIconUrl(item.metadata.icon, item.script.sourceUrl)
        }
        val scriptIcon = rememberClintFavicon("", scriptIconUrl)
        val siteFavicon = rememberClintFavicon(if (scriptIcon == null) item.script.sourceUrl ?: "" else "")
        val favicon = scriptIcon ?: siteFavicon
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
            if (favicon != null) {
                Image(bitmap = favicon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(22.dp))
            } else {
                Icon(androidx.compose.material.icons.Icons.Filled.Code, contentDescription = null, tint = colors.iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Text(item.metadata.name, color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (!isInSelectionMode) {
            Box(
                Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    enabled = masterEnabled && !interactionLocked
                ) { onToggleEnabled(!item.script.enabled) },
                contentAlignment = Alignment.Center
            ) {
                ClintSwitch(checked = item.script.enabled)
            }
        }
        Box {
            IconButton(onClick = { optionsMenuOpen = true }, enabled = masterEnabled && !interactionLocked) {
                Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = null, tint = colors.iconTint)
            }
            UserScriptItemOptionsMenu(
                expanded = optionsMenuOpen,
                isLocal = item.script.isLocal,
                onDismiss = { optionsMenuOpen = false },
                onCheckUpdate = onCheckUpdate,
                onForceUpdate = onForceUpdate,
                onRemove = onRemove,
                onCopyName = onCopyName,
                onCopyLink = onCopyLink,
                onShareLink = onShareLink
            )
        }
    }
}

@Composable
private fun BoxScope.UserScriptsFabMenu(
    isOpen: Boolean,
    enabled: Boolean,
    showDeleteFab: Boolean,
    showAddFabs: Boolean,
    onScrimClick: () -> Unit,
    onToggleMenu: () -> Unit,
    onDeleteClick: () -> Unit,
    onLinkClick: () -> Unit,
    onCreateClick: () -> Unit,
    onUploadClick: () -> Unit
) {
    val colors = LocalClintColors.current
    val rotation by animateFloatAsState(if (isOpen) 45f else 0f, label = "fabMenuRotation")

    if (isOpen) {
        Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.6f)).clickable(onClick = onScrimClick))
    }

    if (showDeleteFab) {
        FloatingActionButton(
            onClick = onDeleteClick,
            containerColor = colors.buttonBackground, contentColor = colors.buttonIconTint,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 24.dp, end = 20.dp)
        ) {
            Icon(androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }

    if (showAddFabs) {
        AnimatedVisibility(
            visible = isOpen,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 96.dp, end = 20.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.End) {
                FabMenuPill(text = stringResource(R.string.user_scripts_add_menu_link), icon = androidx.compose.material.icons.Icons.Filled.Link, onClick = onLinkClick)
                Spacer(Modifier.height(10.dp))
                FabMenuPill(text = stringResource(R.string.user_scripts_add_menu_create), icon = androidx.compose.material.icons.Icons.Filled.Code, onClick = onCreateClick)
                Spacer(Modifier.height(10.dp))
                FabMenuPill(text = stringResource(R.string.user_scripts_add_menu_upload), icon = androidx.compose.material.icons.Icons.Filled.UploadFile, onClick = onUploadClick)
            }
        }

        FloatingActionButton(
            onClick = onToggleMenu,
            containerColor = colors.buttonBackground, contentColor = colors.buttonIconTint,
            modifier = Modifier
                .align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 24.dp, end = 20.dp)
                .alpha(if (!enabled) 0.38f else 1f)
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Filled.Add,
                contentDescription = stringResource(if (isOpen) R.string.action_close else R.string.user_scripts_add_fab_desc),
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
private fun FabMenuPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = LocalClintColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.buttonBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = colors.buttonTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 10.dp))
        Icon(icon, contentDescription = null, tint = colors.buttonIconTint, modifier = Modifier.size(18.dp))
    }
}

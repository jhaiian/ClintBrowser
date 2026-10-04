package com.jhaiian.clint.tabs
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.VisibilityOff

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.ui.FaviconCache
import com.jhaiian.clint.ui.theme.LocalClintColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherSheet(activity: MainActivity, onDismiss: () -> Unit) {
    val colors = LocalClintColors.current
    val tabs = remember { mutableStateListOf<TabPreview>().apply { addAll(activity.tabManager.previews()) } }

    val activeTabId = activity.tabManager.activeTab?.id
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideStatusBar = remember { PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("hide_status_bar", false) }
    val hideSystemNavigation = remember { PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("hide_system_navigation", false) }
    val listState = rememberLazyListState()

    val flingBoundaryConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }
    val configuration = LocalConfiguration.current

    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val maxSheetHeight = if (isPortrait) {
        (configuration.screenHeightDp.dp * 0.5f).coerceAtLeast(320.dp)
    } else {
        (configuration.screenHeightDp.dp - 96.dp).coerceAtLeast(320.dp)
    }

    fun openTab(tabId: String) {
        val idx = activity.tabManager.tabs.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        activity.onTabSelected(idx)
        onDismiss()
    }

    fun closeTab(tabId: String) {
        val idx = activity.tabManager.tabs.indexOfFirst { it.id == tabId }
        if (idx >= 0) activity.onTabClosed(idx)
        val listIdx = tabs.indexOfFirst { it.id == tabId }
        if (listIdx >= 0) tabs.removeAt(listIdx)
        if (tabs.isEmpty()) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.popupBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.divider) }
    ) {
        com.jhaiian.clint.ui.ClintDialogStatusBarEffect(hideStatusBar, hideSystemNavigation)
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).nestedScroll(flingBoundaryConnection),
            state = listState
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    val regularCount = tabs.count { !it.isIncognito }
                    val incognitoCount = tabs.count { it.isIncognito }
                    val headerParts = buildList {
                        if (regularCount > 0) add("$regularCount tab${if (regularCount != 1) "s" else ""}")
                        if (incognitoCount > 0) add("$incognitoCount incognito")
                    }
                    val headerText = if (tabs.isEmpty()) stringResource(R.string.no_tabs) else headerParts.joinToString("  ·  ")

                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(headerText, color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }

                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        NewTabButton(
                            text = stringResource(R.string.new_tab),
                            iconRes = androidx.compose.material.icons.Icons.Filled.Add,
                            modifier = Modifier.weight(1f).padding(end = 6.dp),
                            onClick = { activity.onNewTab(); onDismiss() }
                        )
                        NewTabButton(
                            text = stringResource(R.string.new_incognito_tab),
                            iconRes = androidx.compose.material.icons.Icons.Filled.VisibilityOff,
                            modifier = Modifier.weight(1f).padding(start = 6.dp),
                            onClick = { activity.onNewIncognitoTab(); onDismiss() }
                        )
                    }

                    HorizontalDivider(color = colors.divider, thickness = 1.dp)

                    val normalTabs = tabs.filter { !it.isIncognito }
                    val incognitoTabs = tabs.filter { it.isIncognito }

                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 24.dp)) {
                        if (normalTabs.isNotEmpty()) {
                            TabSectionHeader(isIncognito = false)
                            normalTabs.forEach { tab ->
                                key(tab.id) {
                                    TabRow(
                                        tab = tab,
                                        isActive = tab.id == activeTabId,
                                        onClick = { openTab(tab.id) },
                                        onClose = { closeTab(tab.id) }
                                    )
                                }
                            }
                        }
                        if (incognitoTabs.isNotEmpty()) {
                            TabSectionHeader(isIncognito = true)
                            incognitoTabs.forEach { tab ->
                                key(tab.id) {
                                    TabRow(
                                        tab = tab,
                                        isActive = tab.id == activeTabId,
                                        onClick = { openTab(tab.id) },
                                        onClose = { closeTab(tab.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NewTabButton(text: String, iconRes: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalClintColors.current
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.buttonBackground, contentColor = colors.buttonTextColor),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
    ) {
        Icon(iconRes, contentDescription = null, tint = colors.buttonIconTint, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 13.sp, color = colors.buttonTextColor, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TabSectionHeader(isIncognito: Boolean) {
    val colors = LocalClintColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isIncognito) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Tab,
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(14.dp)
        )
        Text(
            stringResource(if (isIncognito) R.string.tabs_section_incognito else R.string.tabs_section_normal),
            color = colors.secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun TabRow(tab: TabPreview, isActive: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val colors = LocalClintColors.current
    val favicon = rememberTabFavicon(tab)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .background(if (isActive) colors.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(2.dp))
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(width = 12.dp, height = 0.dp))

            if (favicon != null) {
                Image(favicon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(22.dp))
            } else {
                Icon(
                    if (tab.isIncognito) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Public,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    tab.title.ifBlank { stringResource(R.string.new_tab) },
                    color = colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    tab.url.removePrefix("https://").removePrefix("http://"),
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(onClick = onClose, modifier = Modifier.size(36.dp).padding(start = 8.dp)) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close_tab),
                    tint = colors.secondaryText
                )
            }
        }
    }
}

@Composable
internal fun rememberTabFavicon(tab: TabPreview): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(tab.url, tab.isIncognito) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(tab.url, tab.isIncognito) {
        val faviconUrl = FaviconCache.faviconUrlFor(tab.url)
        if (faviconUrl.isEmpty()) return@LaunchedEffect
        if (tab.isIncognito) {
            FaviconCache.loadMemoryOnly(context, faviconUrl) { bmp -> bitmap = bmp }
        } else {
            FaviconCache.load(context, faviconUrl) { bmp -> bitmap = bmp }
        }
    }
    return bitmap
}

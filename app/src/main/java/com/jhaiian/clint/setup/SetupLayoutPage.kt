package com.jhaiian.clint.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jhaiian.clint.ui.ClintSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.ThemeSwatchUtils
import com.jhaiian.clint.ui.theme.LocalClintColors

@Composable
fun SetupLayoutPage(
    addressBarPosition: String,
    menuStyle: String,
    scrollHideMode: String,
    hideStatusBar: Boolean, hideSystemNavigation: Boolean,
    theme: String,
    accent: String,
    onAddressBarPositionSelected: (String) -> Unit,
    onMenuStyleSelected: (String) -> Unit,
    onScrollHideModeSelected: (String) -> Unit,
    onHideStatusBarToggled: (Boolean) -> Unit,
    onHideSystemNavigationToggled: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    val colors = LocalClintColors.current
    val context = LocalContext.current

    val swatch = remember(theme, accent, colors.isLight) {
        ThemeSwatchUtils.resolveSwatchColors(context, theme, accent)
    }
    val bg = androidx.compose.ui.graphics.Color(swatch.bg)
    val surface = androidx.compose.ui.graphics.Color(swatch.surface)
    val onSurface = colors.onSurface
    val panelBg = colors.popupBackground

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Text(stringResource(R.string.setup_layout_title), color = colors.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        Text(stringResource(R.string.setup_layout_subtitle), color = colors.secondaryText, fontSize = 13.sp, lineHeight = 19.5.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp))

        SectionLabel(stringResource(R.string.setup_section_address_bar), colors.primary, Modifier.fillMaxWidth().padding(bottom = 10.dp))
        data class AddrOption(val key: String, val titleRes: Int, val descRes: Int, val bottomSpacing: androidx.compose.ui.unit.Dp)
        listOf(
            AddrOption("top", R.string.address_bar_position_top, R.string.address_bar_position_top_desc, 8.dp),
            AddrOption("bottom", R.string.address_bar_position_bottom, R.string.address_bar_position_bottom_desc, 8.dp),
            AddrOption("split", R.string.address_bar_position_split, R.string.address_bar_position_split_desc, 20.dp)
        ).forEach { option ->
            SelectableCard(
                selected = addressBarPosition == option.key, onClick = { onAddressBarPositionSelected(option.key) },
                cardBackground = colors.cardBackground, primary = colors.primary, contentPadding = 14.dp, bottomSpacing = option.bottomSpacing
            ) {
                AddressBarPreview(option.key, bg, surface, onSurface)
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(stringResource(option.titleRes), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(option.descRes), color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                CheckSlot(addressBarPosition == option.key, colors.primary)
            }
        }

        SectionLabel(stringResource(R.string.setup_section_menu_style), colors.primary, Modifier.fillMaxWidth().padding(bottom = 10.dp))
        data class MenuOption(val key: String, val variant: String, val titleRes: Int, val descRes: Int, val bottomSpacing: androidx.compose.ui.unit.Dp)
        listOf(
            MenuOption("popup", "popup", R.string.menu_style_popup, R.string.menu_style_popup_desc, 8.dp),
            MenuOption("bottom_sheet", "sheet", R.string.menu_style_bottom_sheet, R.string.menu_style_bottom_sheet_desc, 20.dp)
        ).forEach { option ->
            SelectableCard(
                selected = menuStyle == option.key, onClick = { onMenuStyleSelected(option.key) },
                cardBackground = colors.cardBackground, primary = colors.primary, contentPadding = 14.dp, bottomSpacing = option.bottomSpacing
            ) {
                MenuStylePreview(option.variant, addressBarPosition, bg, surface, onSurface, panelBg)
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(stringResource(option.titleRes), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(option.descRes), color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                CheckSlot(menuStyle == option.key, colors.primary)
            }
        }

        SectionLabel(stringResource(R.string.setup_section_nested_scroll), colors.primary, Modifier.fillMaxWidth().padding(bottom = 10.dp))
        data class ScrollOption(val key: String, val bottomSpacing: androidx.compose.ui.unit.Dp)
        listOf(
            ScrollOption("off", 8.dp),
            ScrollOption("search_bar", 8.dp),
            ScrollOption("navigation_bar", 8.dp),
            ScrollOption("both", 20.dp)
        ).forEach { option ->
            if (scrollCardVisible(option.key, addressBarPosition)) {
                val (titleRes, descRes) = when (option.key) {
                    "off" -> R.string.nested_scroll_off to R.string.nested_scroll_off_desc
                    "search_bar" -> R.string.nested_scroll_search_bar to R.string.nested_scroll_search_bar_desc
                    "navigation_bar" -> navBarSlotTitleRes(addressBarPosition) to navBarSlotDescRes(addressBarPosition)
                    else -> R.string.nested_scroll_both to R.string.nested_scroll_both_desc
                }
                val selected = scrollHideMode == option.key
                SelectableCard(
                    selected = selected, onClick = { onScrollHideModeSelected(option.key) },
                    cardBackground = colors.cardBackground, primary = colors.primary, contentPadding = 14.dp, bottomSpacing = option.bottomSpacing
                ) {
                    ScrollHidePreview(option.key, addressBarPosition, bg, surface, onSurface, animate = option.key != "off")
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        Text(stringResource(titleRes), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(descRes), color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    CheckSlot(selected, colors.primary)
                }
            }
        }

        SectionLabel(stringResource(R.string.setup_section_status_bar), colors.primary, Modifier.fillMaxWidth().padding(bottom = 10.dp))
        Card(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable { onHideStatusBarToggled(!hideStatusBar) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.hide_status_bar), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.hide_status_bar_summary), color = colors.secondaryText, fontSize = 12.sp, lineHeight = 15.6.sp, modifier = Modifier.padding(top = 2.dp))
                }
                ClintSwitch(checked = hideStatusBar)
            }
        }
        Card(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .clickable { onHideSystemNavigationToggled(!hideSystemNavigation) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.hide_system_navigation), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.hide_system_navigation_summary), color = colors.secondaryText, fontSize = 12.sp, lineHeight = 15.6.sp, modifier = Modifier.padding(top = 2.dp))
                }
                ClintSwitch(checked = hideSystemNavigation)
            }
        }

        SetupPrimaryButton(stringResource(R.string.next), onNext, colors.buttonBackground, Modifier.padding(bottom = 24.dp))
    }
}

package com.jhaiian.clint.setup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import com.jhaiian.clint.ui.ClintRadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.lookandfeel.LanguageSelectorDialog
import com.jhaiian.clint.settings.lookandfeel.languageSummaryText
import com.jhaiian.clint.ui.ThemeSwatchUtils
import com.jhaiian.clint.ui.theme.LocalClintColors

@Composable
fun SetupWelcomePage(
    language: String,
    hideStatusBar: Boolean,
    hideSystemNavigation: Boolean,
    onLanguageSelected: (String) -> Unit,
    consentChecked: Boolean,
    onConsentCheckedChange: (Boolean) -> Unit,
    onPrivacyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onContinue: () -> Unit
) {
    val colors = LocalClintColors.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    if (showLanguageDialog) {
        LanguageSelectorDialog(
            current = language,
            hideStatusBar = hideStatusBar,
            hideSystemNavigation = hideSystemNavigation,
            onSelect = { showLanguageDialog = false; onLanguageSelected(it) },
            onDismiss = { showLanguageDialog = false }
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_clint_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.padding(top = 32.dp).size(100.dp)
        )
        Text(
            stringResource(R.string.setup_welcome_title),
            color = colors.onSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            stringResource(R.string.setup_welcome_subtitle),
            color = colors.secondaryText,
            fontSize = 14.sp,
            lineHeight = 19.6.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Card(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { showLanguageDialog = true },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.Language, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.pref_language_title),
                    color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                Text(
                    languageSummaryText(language),
                    color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
        }
        Card(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Shield, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.document_viewer_privacy_policy_title),
                        color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                    Text(
                        stringResource(R.string.setup_terms_read),
                        color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onPrivacyClick)
                            .padding(4.dp)
                    )
                }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(colors.surfaceVariant))
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Info, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.document_viewer_terms_title),
                        color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                    Text(
                        stringResource(R.string.setup_terms_read),
                        color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onTermsClick)
                            .padding(4.dp)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = consentChecked,
                onCheckedChange = onConsentCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = colors.primary)
            )
            Text(
                stringResource(R.string.setup_terms_agree),
                color = colors.secondaryText, fontSize = 13.sp, lineHeight = 18.2.sp,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }
        SetupPrimaryButton(
            text = stringResource(R.string.setup_terms_continue),
            onClick = onContinue,
            backgroundColor = colors.buttonBackground,
            enabled = consentChecked,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

data class IntensitySwatchColors(
    val noTintBg: Color, val noTintSurface: Color,
    val softBg: Color, val softSurface: Color,
    val strongBg: Color, val strongSurface: Color,
    val pureBg: Color, val pureSurface: Color,
    val accent: Color
)

@Composable
fun rememberIntensitySwatchColors(theme: String, accent: String): IntensitySwatchColors {
    val context = LocalContext.current
    return remember(theme, accent) {
        val isLight = theme == "light"
        val accentColorInt = ThemeSwatchUtils.resolveSwatchColors(context, theme, accent).accent
        val (noTintBg, noTintSurface) = ThemeSwatchUtils.resolveNoTintSwatchBgSurface(context, theme, accent)
        val (softBg, softSurface) = ThemeSwatchUtils.resolveSoftTintSwatchBgSurface(context, theme, accent)
        val strongAccent = accent
        val strongSwatch = ThemeSwatchUtils.resolveSwatchColors(context, theme, strongAccent)
        val pure = if (isLight) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        IntensitySwatchColors(
            noTintBg = Color(noTintBg), noTintSurface = Color(noTintSurface),
            softBg = Color(softBg), softSurface = Color(softSurface),
            strongBg = Color(strongSwatch.bg),
            strongSurface = Color(strongSwatch.surface),
            pureBg = Color(pure), pureSurface = Color(pure),
            accent = Color(accentColorInt)
        )
    }
}

@Composable
fun SetupThemePage(
    scrollState: androidx.compose.foundation.ScrollState,
    theme: String,
    accent: String,
    intensity: String,
    onThemeSelected: (String) -> Unit,
    onAccentSelected: (String) -> Unit,
    onIntensitySelected: (String) -> Unit,
    onNext: () -> Unit
) {
    val colors = LocalClintColors.current
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.setup_theme_title), color = colors.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        Text(stringResource(R.string.setup_theme_subtitle), color = colors.secondaryText, fontSize = 13.sp, lineHeight = 19.5.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp))

        data class ThemeOption(val key: String, val titleRes: Int, val descRes: Int, val drawableRes: Int)
        listOf(
            ThemeOption("dark", R.string.theme_dark, R.string.theme_dark_desc, R.drawable.theme_swatch_dark),
            ThemeOption("light", R.string.theme_light, R.string.theme_light_desc, R.drawable.theme_swatch_light)
        ).forEach { option ->
            SelectableCard(selected = theme == option.key, onClick = { onThemeSelected(option.key) }, cardBackground = colors.cardBackground, primary = colors.primary) {
                DrawableImage(option.drawableRes, modifier = Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                    Text(stringResource(option.titleRes), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(option.descRes), color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                CheckSlot(theme == option.key, colors.primary)
            }
        }

        SectionLabel(stringResource(R.string.setup_accent_section_label), colors.primary, Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp))

        data class AccentOption(val key: String, val titleRes: Int, val descRes: Int)
        listOf(
            AccentOption("material_you", R.string.accent_material_you, R.string.accent_material_you_desc),
            AccentOption("purple", R.string.accent_purple, R.string.accent_purple_desc),
            AccentOption("deep_purple", R.string.accent_deep_purple, R.string.accent_deep_purple_desc),
            AccentOption("royal_purple", R.string.accent_royal_purple, R.string.accent_royal_purple_desc),
            AccentOption("amethyst", R.string.accent_amethyst, R.string.accent_amethyst_desc),
            AccentOption("lavender", R.string.accent_lavender, R.string.accent_lavender_desc),
            AccentOption("plum", R.string.accent_plum, R.string.accent_plum_desc),
            AccentOption("violet", R.string.accent_violet, R.string.accent_violet_desc),
            AccentOption("default", R.string.accent_default, R.string.accent_default_desc),
            AccentOption("charcoal", R.string.accent_charcoal, R.string.accent_charcoal_desc),
            AccentOption("slate", R.string.accent_slate, R.string.accent_slate_desc),
            AccentOption("graphite", R.string.accent_graphite, R.string.accent_graphite_desc),
            AccentOption("obsidian", R.string.accent_obsidian, R.string.accent_obsidian_desc),
            AccentOption("onyx", R.string.accent_onyx, R.string.accent_onyx_desc),
            AccentOption("titanium", R.string.accent_titanium, R.string.accent_titanium_desc),
            AccentOption("indigo", R.string.accent_indigo, R.string.accent_indigo_desc),
            AccentOption("blue", R.string.accent_blue, R.string.accent_blue_desc),
            AccentOption("midnight", R.string.accent_midnight, R.string.accent_midnight_desc),
            AccentOption("cyan", R.string.accent_cyan, R.string.accent_cyan_desc),
            AccentOption("sky", R.string.accent_sky, R.string.accent_sky_desc),
            AccentOption("teal", R.string.accent_teal, R.string.accent_teal_desc),
            AccentOption("azure", R.string.accent_azure, R.string.accent_azure_desc),
            AccentOption("yellow", R.string.accent_yellow, R.string.accent_yellow_desc),
            AccentOption("lemon", R.string.accent_lemon, R.string.accent_lemon_desc),
            AccentOption("gold", R.string.accent_gold, R.string.accent_gold_desc),
            AccentOption("amber", R.string.accent_amber, R.string.accent_amber_desc),
            AccentOption("sand", R.string.accent_sand, R.string.accent_sand_desc),
            AccentOption("sepia", R.string.accent_sepia, R.string.accent_sepia_desc),
            AccentOption("mustard", R.string.accent_mustard, R.string.accent_mustard_desc),
            AccentOption("red", R.string.accent_red, R.string.accent_red_desc),
            AccentOption("crimson", R.string.accent_crimson, R.string.accent_crimson_desc),
            AccentOption("ruby", R.string.accent_ruby, R.string.accent_ruby_desc),
            AccentOption("pink", R.string.accent_pink, R.string.accent_pink_desc),
            AccentOption("coral", R.string.accent_coral, R.string.accent_coral_desc),
            AccentOption("burgundy", R.string.accent_burgundy, R.string.accent_burgundy_desc),
            AccentOption("scarlet", R.string.accent_scarlet, R.string.accent_scarlet_desc),
            AccentOption("orange", R.string.accent_orange, R.string.accent_orange_desc),
            AccentOption("deep_orange", R.string.accent_deep_orange, R.string.accent_deep_orange_desc),
            AccentOption("tangerine", R.string.accent_tangerine, R.string.accent_tangerine_desc),
            AccentOption("apricot", R.string.accent_apricot, R.string.accent_apricot_desc),
            AccentOption("copper", R.string.accent_copper, R.string.accent_copper_desc),
            AccentOption("peach", R.string.accent_peach, R.string.accent_peach_desc),
            AccentOption("terracotta", R.string.accent_terracotta, R.string.accent_terracotta_desc),
            AccentOption("green", R.string.accent_green, R.string.accent_green_desc),
            AccentOption("emerald", R.string.accent_emerald, R.string.accent_emerald_desc),
            AccentOption("mint", R.string.accent_mint, R.string.accent_mint_desc),
            AccentOption("forest", R.string.accent_forest, R.string.accent_forest_desc),
            AccentOption("sage", R.string.accent_sage, R.string.accent_sage_desc),
            AccentOption("lime", R.string.accent_lime, R.string.accent_lime_desc),
            AccentOption("olive", R.string.accent_olive, R.string.accent_olive_desc)
        ).forEach { option ->
            val swatch = remember(theme, option.key) {
                ThemeSwatchUtils.resolveSwatchColors(context, theme, option.key)
            }
            SelectableCard(selected = accent == option.key, onClick = { onAccentSelected(option.key) }, cardBackground = colors.cardBackground, primary = colors.primary) {
                AccentSwatch(Color(swatch.bg), Color(swatch.surface), Color(swatch.accent))
                Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                    Text(stringResource(option.titleRes), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(option.descRes), color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                CheckSlot(accent == option.key, colors.primary)
            }
        }

        val strongVisible = accent in setOf("purple", "deep_purple", "royal_purple", "amethyst", "lavender", "teal", "pink", "indigo", "cyan", "amber", "mint", "crimson", "slate", "graphite", "obsidian", "onyx", "coral", "midnight", "sepia", "forest", "plum", "sand", "ruby", "sky", "charcoal", "peach", "emerald", "blue", "yellow", "lemon", "gold", "red", "green", "orange", "deep_orange", "tangerine", "apricot", "copper", "scarlet", "lime", "olive", "default", "material_you", "violet", "titanium", "azure", "mustard", "burgundy", "terracotta", "sage")
        val swatches = rememberIntensitySwatchColors(theme, accent)
        SectionLabel(stringResource(R.string.setup_intensity_section_label), colors.primary, Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp))

        SelectableCard(selected = intensity == "no_tint", onClick = { onIntensitySelected("no_tint") }, cardBackground = colors.cardBackground, primary = colors.primary) {
            AccentSwatch(swatches.noTintBg, swatches.noTintSurface, swatches.accent)
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(stringResource(R.string.surface_intensity_no_tint), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.surface_intensity_no_tint_desc), color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            CheckSlot(intensity == "no_tint", colors.primary)
        }
        SelectableCard(selected = intensity == "soft_tint", onClick = { onIntensitySelected("soft_tint") }, cardBackground = colors.cardBackground, primary = colors.primary) {
            AccentSwatch(swatches.softBg, swatches.softSurface, swatches.accent)
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(stringResource(R.string.surface_intensity_soft), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.surface_intensity_soft_desc), color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            CheckSlot(intensity == "soft_tint", colors.primary)
        }
        if (strongVisible) {
            SelectableCard(selected = intensity == "strong_tint", onClick = { onIntensitySelected("strong_tint") }, cardBackground = colors.cardBackground, primary = colors.primary) {
                AccentSwatch(swatches.strongBg, swatches.strongSurface, swatches.accent)
                Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                    Text(stringResource(R.string.surface_intensity_strong), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.surface_intensity_strong_desc), color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                CheckSlot(intensity == "strong_tint", colors.primary)
            }
        }
        SelectableCard(selected = intensity == "pure_mode", onClick = { onIntensitySelected("pure_mode") }, cardBackground = colors.cardBackground, primary = colors.primary) {
            AccentSwatch(swatches.pureBg, swatches.pureSurface, swatches.accent)
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(stringResource(R.string.surface_intensity_pure), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(if (theme == "light") R.string.surface_intensity_pure_light_desc else R.string.surface_intensity_pure_dark_desc),
                    color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
            CheckSlot(intensity == "pure_mode", colors.primary)
        }
        SelectableCard(selected = intensity == "amoled_no_tint", onClick = { onIntensitySelected("amoled_no_tint") }, cardBackground = colors.cardBackground, primary = colors.primary) {
            AccentSwatch(swatches.pureBg, swatches.pureSurface, swatches.accent)
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(stringResource(R.string.surface_intensity_amoled_no_tint), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(if (theme == "light") R.string.surface_intensity_amoled_no_tint_light_desc else R.string.surface_intensity_amoled_no_tint_dark_desc),
                    color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
            CheckSlot(intensity == "amoled_no_tint", colors.primary)
        }

        SetupPrimaryButton(stringResource(R.string.next), onNext, colors.buttonBackground, Modifier.padding(top = 24.dp, bottom = 24.dp))
    }
}

@Composable
fun SetupEnginePage(
    engine: String,
    customName: String,
    customUrl: String,
    hideStatusBar: Boolean, hideSystemNavigation: Boolean,
    onEngineSelected: (String) -> Unit,
    onCustomEngineSaved: (name: String, url: String) -> Unit,
    onNext: () -> Unit
) {
    val colors = LocalClintColors.current
    var customEditorOpen by remember { mutableStateOf(false) }
    val hasCustomEngine = customName.isNotBlank() && customUrl.isNotBlank()

    if (customEditorOpen) {
        com.jhaiian.clint.ui.CustomSearchEngineDialog(
            initialName = customName,
            initialUrl = customUrl,
            hideStatusBar = hideStatusBar, hideSystemNavigation = hideSystemNavigation,
            onConfirm = { name, url ->
                onCustomEngineSaved(name, url)
                customEditorOpen = false
            },
            onDismiss = { customEditorOpen = false }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.ic_clint_logo), stringResource(R.string.app_name), modifier = Modifier.padding(top = 24.dp).size(140.dp))
        Text(stringResource(R.string.app_name), color = colors.onSurface, fontSize = 32.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 20.dp))
        Text(stringResource(R.string.setup_subtitle), color = colors.secondaryText, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 19.6.sp, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.choose_search_engine), color = colors.primary, fontSize = 12.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp))

        data class EngineOption(val key: String, val titleRes: Int, val descRes: Int, val showDefault: Boolean)
        listOf(
            EngineOption("duckduckgo", R.string.engine_duckduckgo, R.string.engine_duckduckgo_desc, true),
            EngineOption("brave", R.string.engine_brave, R.string.engine_brave_desc, false),
            EngineOption("ecosia", R.string.engine_ecosia, R.string.engine_ecosia_desc, false),
            EngineOption("google", R.string.engine_google, R.string.engine_google_desc, false)
        ).forEach { option ->
            val sel = engine == option.key
            SelectableCard(selected = sel, onClick = { onEngineSelected(option.key) }, cardBackground = colors.cardBackground, primary = colors.primary) {
                ClintRadioButton(selected = sel)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(option.titleRes), color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(option.descRes), color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (option.showDefault) DefaultChip(stringResource(R.string.default_label), colors.primary)
            }
        }
        val customSel = engine == "custom"
        SelectableCard(
            selected = customSel,
            onClick = { if (hasCustomEngine) onEngineSelected("custom") else customEditorOpen = true },
            cardBackground = colors.cardBackground, primary = colors.primary
        ) {
            ClintRadioButton(selected = customSel)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (hasCustomEngine) customName else stringResource(R.string.engine_custom),
                    color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    if (hasCustomEngine) customUrl else stringResource(R.string.engine_custom_desc),
                    color = colors.secondaryText, fontSize = 13.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            androidx.compose.material3.IconButton(onClick = { customEditorOpen = true }) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.action_edit),
                    tint = colors.iconTint
                )
            }
        }

        SetupPrimaryButton(stringResource(R.string.next), onNext, colors.buttonBackground, Modifier.padding(top = 24.dp, bottom = 24.dp))
    }
}

@Composable
fun SetupDefaultBrowserPage(
    isDefaultBrowser: Boolean,
    onSetDefault: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = LocalClintColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.setup_default_browser_title), color = colors.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        Text(stringResource(R.string.setup_default_browser_description), color = colors.secondaryText, fontSize = 13.sp, lineHeight = 19.5.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp))

        Card(Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = colors.cardBackground)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Filled.Language, null, tint = colors.primary, modifier = Modifier.size(36.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(stringResource(R.string.setup_default_browser_card_title), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.setup_default_browser_card_desc), color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (isDefaultBrowser) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Check, null, tint = colors.primary, modifier = Modifier.padding(start = 8.dp).size(22.dp))
                }
            }
        }

        SetupPrimaryButton(
            text = stringResource(if (isDefaultBrowser) R.string.get_started else R.string.setup_default_browser_set_button),
            onClick = onSetDefault,
            backgroundColor = colors.buttonBackground
        )
        SetupPrimaryButton(
            text = stringResource(R.string.setup_default_browser_skip),
            onClick = onSkip,
            backgroundColor = colors.buttonBackground,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
        )
    }
}

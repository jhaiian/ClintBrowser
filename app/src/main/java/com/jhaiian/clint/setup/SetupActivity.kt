package com.jhaiian.clint.setup

import android.app.role.RoleManager

import android.content.Intent

import android.content.pm.PackageManager

import android.animation.ValueAnimator

import android.graphics.drawable.GradientDrawable

import android.view.animation.AccelerateDecelerateInterpolator

import android.net.Uri

import android.os.Build

import android.os.Bundle

import android.provider.Settings

import android.view.Gravity

import android.view.View

import android.widget.CheckBox

import android.widget.FrameLayout

import android.widget.TextView

import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.content.ContextCompat

import androidx.core.view.ViewCompat

import androidx.core.view.WindowCompat

import androidx.core.view.WindowInsetsCompat

import androidx.preference.PreferenceManager

import com.google.android.material.button.MaterialButton

import com.google.android.material.color.MaterialColors

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import com.jhaiian.clint.R

import com.jhaiian.clint.base.ClintActivity

import com.jhaiian.clint.browser.MainActivity

import com.jhaiian.clint.crash.CrashHandler

import com.jhaiian.clint.databinding.ActivitySetupBinding

import com.jhaiian.clint.network.DohManager

import com.jhaiian.clint.ui.ClintToast

import com.jhaiian.clint.ui.DocumentViewer

import com.jhaiian.clint.ui.ThemeSwatchUtils

class SetupActivity : ClintActivity() {

private lateinit var binding: ActivitySetupBinding

    private var selectedEngine = "duckduckgo"

    private var selectedTheme = "default"

    private var selectedAccent = "default"

    private var selectedIntensity = "soft_tint"

private var selectedAddressBarPosition = "top"

    private var selectedMenuStyle = "popup"

    private var selectedScrollHideMode = "off"

    private var selectedHideStatusBar = false

private var scrollAnimatorSearchBar: android.animation.ValueAnimator? = null

    private var scrollAnimatorNavBar: android.animation.ValueAnimator? = null

    private var scrollAnimatorBoth: android.animation.ValueAnimator? = null

private val browserRoleLauncher = registerForActivityResult(

        ActivityResultContracts.StartActivityForResult()

    ) {

        refreshPage5()

    }

    private var selectedDohMode = DohManager.MODE_OFF

    private var selectedProvider = DohManager.PROVIDER_CLOUDFLARE

    private var currentPage = 0

companion object {

        const val PRIVACY_POLICY_URL = "https://github.com/jhaiian/ClintBrowser/blob/main/PRIVACY_POLICY.md"

        const val TERMS_URL = "https://github.com/jhaiian/ClintBrowser/blob/main/TERMS_OF_SERVICE.md"

        private const val KEY_PENDING_PAGE = "setup_pending_page"

        private const val KEY_PENDING_SCROLL = "setup_pending_scroll"

        private const val KEY_PENDING_HIDE_STATUS_BAR = "setup_pending_hide_status_bar"

    }

override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        CrashHandler.install(this)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (prefs.getBoolean("setup_complete", false)) { startMainActivity(); return }

        selectedHideStatusBar = prefs.getBoolean("hide_status_bar", false)

        if (prefs.contains(KEY_PENDING_HIDE_STATUS_BAR)) {

            selectedHideStatusBar = prefs.getBoolean(KEY_PENDING_HIDE_STATUS_BAR, false)

            prefs.edit().remove(KEY_PENDING_HIDE_STATUS_BAR).apply()

        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySetupBinding.inflate(layoutInflater)

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->

            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val topInset = if (selectedHideStatusBar) 0 else bars.top

            v.setPadding(bars.left, topInset, bars.right, bars.bottom)

            insets

        }

selectedTheme = prefs.getString("app_theme", "default") ?: "default"

        selectedAccent = prefs.getString("accent_color", "default") ?: "default"

        selectedIntensity = prefs.getString("surface_intensity", "soft_tint") ?: "soft_tint"

        selectedAddressBarPosition = prefs.getString("address_bar_position", "top") ?: "top"

        selectedMenuStyle = prefs.getString("menu_style", "popup") ?: "popup"

        selectedScrollHideMode = prefs.getString("scroll_hide_mode", "off") ?: "off"

setupPage0()

        setupPage1()

        setupPage2()

        setupPage3()

        setupPage4()

        setupPage5()

val pendingPage = prefs.getInt(KEY_PENDING_PAGE, -1)

        if (pendingPage >= 0) {

            prefs.edit().remove(KEY_PENDING_PAGE).apply()

            showPage(pendingPage, animate = false)

            val pendingScroll = prefs.getInt(KEY_PENDING_SCROLL, 0)

            if (pendingScroll > 0) {

                prefs.edit().remove(KEY_PENDING_SCROLL).apply()

                binding.scrollSetupPage1.post { binding.scrollSetupPage1.scrollTo(0, pendingScroll) }

            }

        } else {

            showPage(0, animate = false)

        }

    }

override fun onResume() {

        super.onResume()

        if (currentPage == 4) refreshPage4Button()

        if (currentPage == 5) refreshPage5()

    }

private fun setupPage0() {

        val checkbox = binding.root.findViewById<CheckBox>(R.id.checkboxConsent)

        val btnContinue = binding.root.findViewById<MaterialButton>(R.id.btnAgreeAndContinue)

        val tvPrivacy = binding.root.findViewById<TextView>(R.id.tvConsentPrivacyLink)

        val tvTerms = binding.root.findViewById<TextView>(R.id.tvConsentTermsLink)

checkbox.setOnCheckedChangeListener { _, isChecked ->

            btnContinue.isEnabled = isChecked

            btnContinue.alpha = if (isChecked) 1.0f else 0.5f

        }

        btnContinue.alpha = 0.5f

        btnContinue.setOnClickListener { showPage(1) }

tvPrivacy.setOnClickListener {

            DocumentViewer.show(this, getString(R.string.document_viewer_privacy_policy_title), DocumentViewer.PRIVACY_POLICY_URL)

        }

        tvTerms.setOnClickListener {

            DocumentViewer.show(this, getString(R.string.document_viewer_terms_title), DocumentViewer.TERMS_URL)

        }

    }

private fun setupPage1() {

        selectSetupTheme(selectedTheme)

        selectSetupAccent(selectedAccent)

        selectSetupIntensity(selectedIntensity)

        binding.cardSetupThemeDefault.setOnClickListener { onSetupThemeSelected("default") }

        binding.cardSetupThemeDark.setOnClickListener { onSetupThemeSelected("dark") }

        binding.cardSetupThemeLight.setOnClickListener { onSetupThemeSelected("light") }

        binding.cardSetupAccentDefault.setOnClickListener { onSetupAccentSelected("default") }

        binding.cardSetupAccentMaterialYou.setOnClickListener { onSetupAccentSelected("material_you") }

        binding.cardSetupAccentPurple.setOnClickListener { onSetupAccentSelected("purple") }

        binding.cardSetupAccentBlue.setOnClickListener { onSetupAccentSelected("blue") }

        binding.cardSetupAccentYellow.setOnClickListener { onSetupAccentSelected("yellow") }

        binding.cardSetupAccentRed.setOnClickListener { onSetupAccentSelected("red") }

        binding.cardSetupAccentGreen.setOnClickListener { onSetupAccentSelected("green") }

        binding.cardSetupAccentOrange.setOnClickListener { onSetupAccentSelected("orange") }

        binding.cardSetupIntensitySoftTint.setOnClickListener { onSetupIntensitySelected("soft_tint") }

        binding.cardSetupIntensityStrongTint.setOnClickListener { onSetupIntensitySelected("strong_tint") }

        binding.cardSetupIntensityPureMode.setOnClickListener { onSetupIntensitySelected("pure_mode") }

        binding.btnSetupThemeNext.setOnClickListener { showPage(2) }

    }

private fun setupPage2() {

        selectSetupAddressBarPosition(selectedAddressBarPosition)

        selectSetupMenuStyle(selectedMenuStyle)

        selectSetupScrollHideMode(selectedScrollHideMode)

        binding.switchSetupHideStatusBar.isSaveEnabled = false
        binding.switchSetupHideStatusBar.isChecked = selectedHideStatusBar

        updatePage2Swatches()

binding.cardSetupAddrTop.setOnClickListener { selectSetupAddressBarPosition("top") }

        binding.cardSetupAddrBottom.setOnClickListener { selectSetupAddressBarPosition("bottom") }

        binding.cardSetupAddrSplit.setOnClickListener { selectSetupAddressBarPosition("split") }

binding.cardSetupMenuPopup.setOnClickListener { selectSetupMenuStyle("popup") }

        binding.cardSetupMenuSheet.setOnClickListener { selectSetupMenuStyle("bottom_sheet") }

binding.cardSetupScrollOff.setOnClickListener { selectSetupScrollHideMode("off") }

        binding.cardSetupScrollSearchBar.setOnClickListener { selectSetupScrollHideMode("search_bar") }

        binding.cardSetupScrollNavBar.setOnClickListener { selectSetupScrollHideMode("navigation_bar") }

        binding.cardSetupScrollBoth.setOnClickListener { selectSetupScrollHideMode("both") }

binding.cardSetupHideStatusBar.setOnClickListener {

            binding.switchSetupHideStatusBar.isChecked = !binding.switchSetupHideStatusBar.isChecked

        }

        binding.switchSetupHideStatusBar.setOnCheckedChangeListener { _, isChecked ->

            selectedHideStatusBar = isChecked

            ClintToast.show(this, getString(R.string.setup_status_bar_applied_later), R.drawable.ic_check_24)

        }

binding.btnSetupLayoutNext.setOnClickListener { onNextFromPage2() }

    }

private fun selectSetupAddressBarPosition(position: String) {

        selectedAddressBarPosition = position

        listOf(

            Triple(binding.cardSetupAddrTop, binding.checkSetupAddrTop, "top"),

            Triple(binding.cardSetupAddrBottom, binding.checkSetupAddrBottom, "bottom"),

            Triple(binding.cardSetupAddrSplit, binding.checkSetupAddrSplit, "split")

        ).forEach { (card, check, key) ->

            val sel = key == position

            card.alpha = if (sel) 1.0f else 0.45f

            card.strokeWidth = if (sel) 3 else 0

            check.visibility = if (sel) View.VISIBLE else View.INVISIBLE

        }

        updatePage2Swatches()

    }

private fun selectSetupMenuStyle(style: String) {

        selectedMenuStyle = style

        listOf(

            Triple(binding.cardSetupMenuPopup, binding.checkSetupMenuPopup, "popup"),

            Triple(binding.cardSetupMenuSheet, binding.checkSetupMenuSheet, "bottom_sheet")

        ).forEach { (card, check, key) ->

            val sel = key == style

            card.alpha = if (sel) 1.0f else 0.45f

            card.strokeWidth = if (sel) 3 else 0

            check.visibility = if (sel) View.VISIBLE else View.INVISIBLE

        }

    }

private fun selectSetupScrollHideMode(mode: String) {

        selectedScrollHideMode = mode

        listOf(

            Triple(binding.cardSetupScrollOff, binding.checkSetupScrollOff, "off"),

            Triple(binding.cardSetupScrollSearchBar, binding.checkSetupScrollSearchBar, "search_bar"),

            Triple(binding.cardSetupScrollNavBar, binding.checkSetupScrollNavBar, "navigation_bar"),

            Triple(binding.cardSetupScrollBoth, binding.checkSetupScrollBoth, "both")

        ).forEach { (card, check, key) ->

            val sel = key == mode

            card.alpha = if (sel) 1.0f else 0.45f

            card.strokeWidth = if (sel) 3 else 0

            check.visibility = if (sel) View.VISIBLE else View.INVISIBLE

        }

    }

private fun updatePage2Swatches() {
        val ctx = this
        val (bgColor, surfaceColor, _) = when (selectedAccent) {
            "material_you" -> ThemeSwatchUtils.resolveMaterialYouSwatchColors(ctx, selectedTheme)
            "purple" -> ThemeSwatchUtils.resolvePurpleSwatchColors(ctx, selectedTheme)
            "blue" -> ThemeSwatchUtils.resolveBlueSwatchColors(ctx, selectedTheme)
            "yellow" -> ThemeSwatchUtils.resolveYellowSwatchColors(ctx, selectedTheme)
            "red" -> ThemeSwatchUtils.resolveRedSwatchColors(ctx, selectedTheme)
            "green" -> ThemeSwatchUtils.resolveGreenSwatchColors(ctx, selectedTheme)
            "orange" -> ThemeSwatchUtils.resolveOrangeSwatchColors(ctx, selectedTheme)
            else -> ThemeSwatchUtils.resolveDefaultSwatchColors(ctx, selectedTheme)
        }
        val popupBgColor = MaterialColors.getColor(ctx, R.attr.clintPopupBackground, bgColor)
        val colorOnSurface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0)

        val density = resources.displayMetrics.density
        val cornerRadius = 8f * density
        val barHeightPx = 14f * density

        fun makeFrameBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(color)
        }

        fun makePillBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 3f * density
            setColor(color)
        }

        fun makeCardBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 4f * density
            setColor(color)
        }

        fun makeSheetPanelBg(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f)
            setColor(color)
        }

        val topBarColor = if (selectedAddressBarPosition != "bottom") surfaceColor else bgColor
        val bottomBarColor = if (selectedAddressBarPosition != "top") surfaceColor else bgColor

        binding.swatchSetupAddrTop.background = makeFrameBg(bgColor)
        binding.swatchSetupAddrBottom.background = makeFrameBg(bgColor)
        binding.swatchSetupAddrSplit.background = makeFrameBg(bgColor)
        binding.swatchSetupAddrTopBar.setBackgroundColor(surfaceColor)
        binding.swatchSetupAddrTopPill.background = makePillBg(colorOnSurface)
        binding.swatchSetupAddrBottomBar.setBackgroundColor(surfaceColor)
        binding.swatchSetupAddrBottomPill.background = makePillBg(colorOnSurface)
        binding.swatchSetupAddrSplitTopBar.setBackgroundColor(surfaceColor)
        binding.swatchSetupAddrSplitTopPill.background = makePillBg(colorOnSurface)
        binding.swatchSetupAddrSplitBottomBar.setBackgroundColor(surfaceColor)

        binding.swatchSetupMenuPopup.background = makeFrameBg(bgColor)
        binding.swatchSetupMenuSheet.background = makeFrameBg(bgColor)
        binding.swatchSetupMenuPopupTopBar.setBackgroundColor(topBarColor)
        binding.swatchSetupMenuSheetTopBar.setBackgroundColor(topBarColor)
        binding.swatchSetupMenuPopupPill.background = makePillBg(colorOnSurface)
        binding.swatchSetupMenuSheetPill.background = makePillBg(colorOnSurface)
        binding.swatchSetupMenuPopupCardBg.background = makeCardBg(popupBgColor)
        binding.swatchSetupMenuSheetPanelBg.background = makeSheetPanelBg(popupBgColor)

        when (selectedAddressBarPosition) {
            "bottom" -> {
                binding.swatchSetupMenuPopupBottomBar.visibility = View.VISIBLE
                binding.swatchSetupMenuPopupBottomBar.setBackgroundColor(bottomBarColor)
                binding.swatchSetupMenuSheetBottomBar.visibility = View.VISIBLE
                binding.swatchSetupMenuSheetBottomBar.setBackgroundColor(bottomBarColor)
                binding.swatchSetupMenuPopupNavDots.visibility = View.GONE
                binding.swatchSetupMenuSheetNavDots.visibility = View.GONE
                listOf(binding.swatchSetupMenuPopupPill, binding.swatchSetupMenuSheetPill).forEach { pill ->
                    val lp = pill.layoutParams as FrameLayout.LayoutParams
                    lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = 0
                    lp.bottomMargin = (4 * density).toInt()
                    pill.layoutParams = lp
                    pill.bringToFront()
                }
                binding.swatchSetupMenuPopupCard.layoutParams =
                    (binding.swatchSetupMenuPopupCard.layoutParams as FrameLayout.LayoutParams).apply {
                        gravity = Gravity.BOTTOM or Gravity.END
                        topMargin = 0
                        bottomMargin = (14 * density).toInt()
                    }
            }
            "split" -> {
                binding.swatchSetupMenuPopupBottomBar.visibility = View.VISIBLE
                binding.swatchSetupMenuPopupBottomBar.setBackgroundColor(bottomBarColor)
                binding.swatchSetupMenuSheetBottomBar.visibility = View.VISIBLE
                binding.swatchSetupMenuSheetBottomBar.setBackgroundColor(bottomBarColor)
                binding.swatchSetupMenuPopupNavDots.visibility = View.VISIBLE
                binding.swatchSetupMenuSheetNavDots.visibility = View.VISIBLE
                listOf(binding.swatchSetupMenuPopupPill, binding.swatchSetupMenuSheetPill).forEach { pill ->
                    val lp = pill.layoutParams as FrameLayout.LayoutParams
                    lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = (4 * density).toInt()
                    lp.bottomMargin = 0
                    pill.layoutParams = lp
                }
                binding.swatchSetupMenuPopupCard.layoutParams =
                    (binding.swatchSetupMenuPopupCard.layoutParams as FrameLayout.LayoutParams).apply {
                        gravity = Gravity.TOP or Gravity.END
                        topMargin = (14 * density).toInt()
                        bottomMargin = 0
                    }
            }
            else -> {
                binding.swatchSetupMenuPopupBottomBar.visibility = View.GONE
                binding.swatchSetupMenuSheetBottomBar.visibility = View.GONE
                binding.swatchSetupMenuPopupNavDots.visibility = View.GONE
                binding.swatchSetupMenuSheetNavDots.visibility = View.GONE
                listOf(binding.swatchSetupMenuPopupPill, binding.swatchSetupMenuSheetPill).forEach { pill ->
                    val lp = pill.layoutParams as FrameLayout.LayoutParams
                    lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = (4 * density).toInt()
                    lp.bottomMargin = 0
                    pill.layoutParams = lp
                }
                binding.swatchSetupMenuPopupCard.layoutParams =
                    (binding.swatchSetupMenuPopupCard.layoutParams as FrameLayout.LayoutParams).apply {
                        gravity = Gravity.TOP or Gravity.END
                        topMargin = (14 * density).toInt()
                        bottomMargin = 0
                    }
            }
        }

        listOf(
            binding.swatchSetupScrollOff,
            binding.swatchSetupScrollSearchBar,
            binding.swatchSetupScrollNavBar,
            binding.swatchSetupScrollBoth
        ).forEach { it.background = makeFrameBg(bgColor) }

        listOf(
            binding.swatchSetupScrollOffPill,
            binding.swatchSetupScrollSearchPill,
            binding.swatchSetupScrollNavPill,
            binding.swatchSetupScrollBothPill
        ).forEach { it.background = makePillBg(colorOnSurface) }

        listOf(
            binding.swatchSetupScrollOffTop,
            binding.swatchSetupScrollSearchTop,
            binding.swatchSetupScrollNavTop,
            binding.swatchSetupScrollBothTop
        ).forEach { it.setBackgroundColor(topBarColor) }

        listOf(
            binding.swatchSetupScrollOffBottom,
            binding.swatchSetupScrollSearchBottom,
            binding.swatchSetupScrollNavBottom,
            binding.swatchSetupScrollBothBottom
        ).forEach { it.setBackgroundColor(bottomBarColor) }

        when (selectedAddressBarPosition) {
            "top" -> {
                binding.cardSetupScrollNavBar.visibility = View.GONE
                binding.cardSetupScrollBoth.visibility = View.GONE
                if (selectedScrollHideMode == "navigation_bar" || selectedScrollHideMode == "both") {
                    selectSetupScrollHideMode("off")
                }
                binding.tvSetupScrollNavBarTitle.text = getString(R.string.nested_scroll_nav_bar)
                binding.tvSetupScrollNavBarDesc.text = getString(R.string.nested_scroll_nav_bar_desc)
                listOf(binding.swatchSetupScrollOffNavDots, binding.swatchSetupScrollSearchNavDots,
                    binding.swatchSetupScrollNavNavDots, binding.swatchSetupScrollBothNavDots).forEach {
                    it.visibility = View.GONE
                }
                listOf(binding.swatchSetupScrollOffPill, binding.swatchSetupScrollSearchPill).forEach { pill ->
                    val lp = pill.layoutParams as FrameLayout.LayoutParams
                    lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = (4 * density).toInt()
                    lp.bottomMargin = 0
                    pill.layoutParams = lp
                }
            }
            "bottom" -> {
                binding.cardSetupScrollSearchBar.visibility = View.GONE
                binding.cardSetupScrollNavBar.visibility = View.VISIBLE
                binding.cardSetupScrollBoth.visibility = View.GONE
                binding.tvSetupScrollNavBarTitle.text = getString(R.string.nested_scroll_search_bar)
                binding.tvSetupScrollNavBarDesc.text = getString(R.string.nested_scroll_search_bar_desc)
                if (selectedScrollHideMode == "search_bar" || selectedScrollHideMode == "both") {
                    selectSetupScrollHideMode("off")
                }
                listOf(binding.swatchSetupScrollOffNavDots, binding.swatchSetupScrollSearchNavDots,
                    binding.swatchSetupScrollNavNavDots, binding.swatchSetupScrollBothNavDots).forEach {
                    it.visibility = View.GONE
                }
                listOf(binding.swatchSetupScrollOffPill, binding.swatchSetupScrollNavPill).forEach { pill ->
                    val lp = pill.layoutParams as FrameLayout.LayoutParams
                    lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = 0
                    lp.bottomMargin = (4 * density).toInt()
                    pill.layoutParams = lp
                    pill.bringToFront()
                }
            }
            "split" -> {
                binding.cardSetupScrollSearchBar.visibility = View.VISIBLE
                binding.cardSetupScrollNavBar.visibility = View.VISIBLE
                binding.cardSetupScrollBoth.visibility = View.VISIBLE
                binding.tvSetupScrollNavBarTitle.text = getString(R.string.nested_scroll_nav_bar)
                binding.tvSetupScrollNavBarDesc.text = getString(R.string.nested_scroll_nav_bar_desc)
                listOf(binding.swatchSetupScrollOffNavDots, binding.swatchSetupScrollSearchNavDots,
                    binding.swatchSetupScrollNavNavDots, binding.swatchSetupScrollBothNavDots).forEach {
                    it.visibility = View.VISIBLE
                }
                listOf(binding.swatchSetupScrollOffPill, binding.swatchSetupScrollSearchPill,
                    binding.swatchSetupScrollNavPill, binding.swatchSetupScrollBothPill).forEach { pill ->
                    val lp = pill.layoutParams as FrameLayout.LayoutParams
                    lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = (4 * density).toInt()
                    lp.bottomMargin = 0
                    pill.layoutParams = lp
                }
            }
            else -> {
                binding.cardSetupScrollSearchBar.visibility = View.VISIBLE
                binding.cardSetupScrollNavBar.visibility = View.VISIBLE
                binding.cardSetupScrollBoth.visibility = View.VISIBLE
                binding.tvSetupScrollNavBarTitle.text = getString(R.string.nested_scroll_nav_bar)
                binding.tvSetupScrollNavBarDesc.text = getString(R.string.nested_scroll_nav_bar_desc)
                listOf(binding.swatchSetupScrollOffNavDots, binding.swatchSetupScrollSearchNavDots,
                    binding.swatchSetupScrollNavNavDots, binding.swatchSetupScrollBothNavDots).forEach {
                    it.visibility = View.GONE
                }
                listOf(binding.swatchSetupScrollOffPill, binding.swatchSetupScrollSearchPill,
                    binding.swatchSetupScrollNavPill, binding.swatchSetupScrollBothPill).forEach { pill ->
                    val lp = pill.layoutParams as FrameLayout.LayoutParams
                    lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = (4 * density).toInt()
                    lp.bottomMargin = 0
                    pill.layoutParams = lp
                }
            }
        }

        scrollAnimatorSearchBar?.cancel()
        scrollAnimatorNavBar?.cancel()
        scrollAnimatorBoth?.cancel()

        val isSearchBarVisible = binding.cardSetupScrollSearchBar.visibility == View.VISIBLE
        val isNavBarVisible = binding.cardSetupScrollNavBar.visibility == View.VISIBLE
        val isBothVisible = binding.cardSetupScrollBoth.visibility == View.VISIBLE

        if (isSearchBarVisible) {
            scrollAnimatorSearchBar = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val t = -(anim.animatedValue as Float) * barHeightPx
                    binding.swatchSetupScrollSearchTop.translationY = t
                    binding.swatchSetupScrollSearchPill.translationY = t
                }
                start()
            }
        }

        if (isNavBarVisible) {
            scrollAnimatorNavBar = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val t = (anim.animatedValue as Float) * barHeightPx
                    binding.swatchSetupScrollNavBottom.translationY = t
                    if (selectedAddressBarPosition == "bottom") binding.swatchSetupScrollNavPill.translationY = t
                    if (selectedAddressBarPosition == "split") binding.swatchSetupScrollNavNavDots.translationY = t
                }
                start()
            }
        }

        if (isBothVisible) {
            scrollAnimatorBoth = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    val topT = -f * barHeightPx
                    val botT = f * barHeightPx
                    binding.swatchSetupScrollBothTop.translationY = topT
                    binding.swatchSetupScrollBothPill.translationY = topT
                    binding.swatchSetupScrollBothBottom.translationY = botT
                    if (selectedAddressBarPosition == "split") binding.swatchSetupScrollBothNavDots.translationY = botT
                }
                start()
            }
        }
    }

private fun setupPage3() {

        selectEngine("duckduckgo")

        binding.cardDuckduckgo.setOnClickListener { selectEngine("duckduckgo") }

        binding.cardBrave.setOnClickListener { selectEngine("brave") }

        binding.cardGoogle.setOnClickListener { selectEngine("google") }

        binding.btnNext.setOnClickListener { onNextFromPage3() }

    }

private fun setupPage4() {

        selectDohMode(DohManager.MODE_OFF)

        selectProvider(DohManager.PROVIDER_CLOUDFLARE)

        listOf(

            binding.cardDohOff to DohManager.MODE_OFF,

            binding.cardDohDefault to DohManager.MODE_DEFAULT,

            binding.cardDohIncreased to DohManager.MODE_INCREASED,

            binding.cardDohMax to DohManager.MODE_MAX

        ).forEach { (card, mode) -> card.setOnClickListener { selectDohMode(mode) } }

        listOf(

            binding.cardSetupCloudflare to DohManager.PROVIDER_CLOUDFLARE,

            binding.cardSetupQuad9 to DohManager.PROVIDER_QUAD9

        ).forEach { (card, provider) -> card.setOnClickListener { selectProvider(provider) } }

    }

private fun refreshPage4Button() {

        if (isClintDefaultBrowser()) {

            binding.btnGetStarted.text = getString(R.string.get_started)

            binding.btnGetStarted.setOnClickListener { saveAndProceed() }

        } else {

            binding.btnGetStarted.text = getString(R.string.next)

            binding.btnGetStarted.setOnClickListener { showPage(5) }

        }

    }

private fun setupPage5() {

        binding.btnSkipDefaultBrowser.setOnClickListener { saveAndProceed() }

    }

private fun refreshPage5() {

        if (isClintDefaultBrowser()) {

            binding.ivDefaultBrowserCheck.visibility = View.VISIBLE

            binding.btnSetDefaultBrowser.text = getString(R.string.get_started)

            binding.btnSetDefaultBrowser.setOnClickListener { saveAndProceed() }

        } else {

            binding.ivDefaultBrowserCheck.visibility = View.GONE

            binding.btnSetDefaultBrowser.text = getString(R.string.setup_default_browser_set_button)

            binding.btnSetDefaultBrowser.setOnClickListener { openDefaultBrowserPicker() }

        }

    }

private fun showPage(page: Int, animate: Boolean = true) {

        if (currentPage == 2 && page != 2) {

            scrollAnimatorSearchBar?.cancel()

            scrollAnimatorNavBar?.cancel()

            scrollAnimatorBoth?.cancel()

        }

        currentPage = page

        if (!animate) binding.viewFlipper.setInAnimation(null).also { binding.viewFlipper.setOutAnimation(null) }

        binding.viewFlipper.displayedChild = page

        if (animate) {

            binding.viewFlipper.inAnimation = android.view.animation.AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)

            binding.viewFlipper.outAnimation = android.view.animation.AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)

        }

        if (page == 2) updatePage2Swatches()

        if (page == 4) refreshPage4Button()

        if (page == 5) refreshPage5()

    }

private fun isClintDefaultBrowser(): Boolean {

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))

        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        return info?.activityInfo?.packageName == packageName

    }

private fun openDefaultBrowserPicker() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            try {

                val roleManager = getSystemService(RoleManager::class.java)

                if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {

                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)

                    browserRoleLauncher.launch(intent)

                    return

                }

            } catch (_: Exception) {}

        }

        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))

    }

private fun onNextFromPage2() {
        if (selectedScrollHideMode != "off") {
            MaterialAlertDialogBuilder(this, getDialogTheme())
                .setTitle(getString(R.string.nested_scroll_warning_title))
                .setMessage(getString(R.string.nested_scroll_warning_message))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_enable_anyway)) { _, _ -> showPage(3) }
                .create().also { applyStatusBarFlagToDialog(it) }.show()
        } else {
            showPage(3)
        }
    }

private fun onNextFromPage3() {

        if (selectedEngine == "google") {

            MaterialAlertDialogBuilder(this, getDialogTheme())

                .setTitle(getString(R.string.google_warning_title))

                .setMessage(getString(R.string.google_warning_message))

                .setNegativeButton(getString(R.string.choose_another), null)

                .setPositiveButton(getString(R.string.use_google_anyway)) { _, _ -> showPage(4) }

                .create().also { applyStatusBarFlagToDialog(it) }.show()

        } else {

            showPage(4)

        }

    }

private fun onSetupThemeSelected(theme: String) {

        if (theme == selectedTheme) { showPage(2); return }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        prefs.edit()

            .putInt(KEY_PENDING_PAGE, 1)

            .putInt(KEY_PENDING_SCROLL, binding.scrollSetupPage1.scrollY)

            .putBoolean(KEY_PENDING_HIDE_STATUS_BAR, selectedHideStatusBar)

            .apply()

        captureAndRecreate(theme)

    }

private fun onSetupAccentSelected(accent: String) {

        if (accent == selectedAccent) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        prefs.edit()

            .putInt(KEY_PENDING_PAGE, 1)

            .putInt(KEY_PENDING_SCROLL, binding.scrollSetupPage1.scrollY)

            .putBoolean(KEY_PENDING_HIDE_STATUS_BAR, selectedHideStatusBar)

            .apply()

        captureAndApplyAccentColor(accent)

    }

private fun onSetupIntensitySelected(intensity: String) {

        if (intensity == selectedIntensity) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        prefs.edit()

            .putInt(KEY_PENDING_PAGE, 1)

            .putInt(KEY_PENDING_SCROLL, binding.scrollSetupPage1.scrollY)

            .putBoolean(KEY_PENDING_HIDE_STATUS_BAR, selectedHideStatusBar)

            .apply()

        captureAndApplySurfaceIntensity(intensity)

    }

private fun selectSetupTheme(theme: String) {

        selectedTheme = theme

        listOf(

            Triple(binding.cardSetupThemeDefault, binding.checkSetupThemeDefault, "default"),

            Triple(binding.cardSetupThemeDark, binding.checkSetupThemeDark, "dark"),

            Triple(binding.cardSetupThemeLight, binding.checkSetupThemeLight, "light")

        ).forEach { (card, check, key) ->

            val sel = key == theme

            card.alpha = if (sel) 1.0f else 0.45f

            card.strokeWidth = if (sel) 3 else 0

            check.visibility = if (sel) View.VISIBLE else View.INVISIBLE

        }

        val accentVisibility = View.VISIBLE

        binding.tvSetupAccentLabel.visibility = accentVisibility

        binding.cardSetupAccentDefault.visibility = accentVisibility

        binding.cardSetupAccentMaterialYou.visibility = accentVisibility

        binding.cardSetupAccentPurple.visibility = accentVisibility

        binding.cardSetupAccentBlue.visibility = accentVisibility

        binding.cardSetupAccentYellow.visibility = accentVisibility

        binding.cardSetupAccentRed.visibility = accentVisibility

        binding.cardSetupAccentGreen.visibility = accentVisibility

        binding.cardSetupAccentOrange.visibility = accentVisibility

        updateSetupAccentSwatches()

        updateSetupIntensityVisibility()

        updateSetupIntensitySwatches()

    }

private fun selectSetupAccent(accent: String) {

        selectedAccent = accent

        listOf(

            Triple(binding.cardSetupAccentDefault, binding.checkSetupAccentDefault, "default"),

            Triple(binding.cardSetupAccentMaterialYou, binding.checkSetupAccentMaterialYou, "material_you"),

            Triple(binding.cardSetupAccentPurple, binding.checkSetupAccentPurple, "purple"),

            Triple(binding.cardSetupAccentBlue, binding.checkSetupAccentBlue, "blue"),

            Triple(binding.cardSetupAccentYellow, binding.checkSetupAccentYellow, "yellow"),

            Triple(binding.cardSetupAccentRed, binding.checkSetupAccentRed, "red"),

            Triple(binding.cardSetupAccentGreen, binding.checkSetupAccentGreen, "green"),

            Triple(binding.cardSetupAccentOrange, binding.checkSetupAccentOrange, "orange")

        ).forEach { (card, check, key) ->

            val sel = key == accent

            card.alpha = if (sel) 1.0f else 0.45f

            card.strokeWidth = if (sel) 3 else 0

            check.visibility = if (sel) View.VISIBLE else View.INVISIBLE

        }

        updateSetupIntensityVisibility()

        updateSetupIntensitySwatches()

    }

private fun updateSetupAccentSwatches() {

        val (defaultBg, defaultSurface, defaultAccent) = ThemeSwatchUtils.resolveDefaultSwatchColors(this, selectedTheme)

        val (matYouBg, matYouSurface, matYouAccent) = ThemeSwatchUtils.resolveMaterialYouSwatchColors(this, selectedTheme)

        val (purpleBg, purpleSurface, purpleAccent) = ThemeSwatchUtils.resolvePurpleSwatchColors(this, selectedTheme)

        val (blueBg, blueSurface, blueAccent) = ThemeSwatchUtils.resolveBlueSwatchColors(this, selectedTheme)

        val (yellowBg, yellowSurface, yellowAccent) = ThemeSwatchUtils.resolveYellowSwatchColors(this, selectedTheme)

        val (redBg, redSurface, redAccent) = ThemeSwatchUtils.resolveRedSwatchColors(this, selectedTheme)

        val (greenBg, greenSurface, greenAccent) = ThemeSwatchUtils.resolveGreenSwatchColors(this, selectedTheme)

        val (orangeBg, orangeSurface, orangeAccent) = ThemeSwatchUtils.resolveOrangeSwatchColors(this, selectedTheme)

        binding.swatchSetupAccentDefault.background = ThemeSwatchUtils.buildSwatchDrawable(this, defaultBg, defaultSurface, defaultAccent)

        binding.swatchSetupAccentMaterialYou.background = ThemeSwatchUtils.buildSwatchDrawable(this, matYouBg, matYouSurface, matYouAccent)

        binding.swatchSetupAccentPurple.background = ThemeSwatchUtils.buildSwatchDrawable(this, purpleBg, purpleSurface, purpleAccent)

        binding.swatchSetupAccentBlue.background = ThemeSwatchUtils.buildSwatchDrawable(this, blueBg, blueSurface, blueAccent)

        binding.swatchSetupAccentYellow.background = ThemeSwatchUtils.buildSwatchDrawable(this, yellowBg, yellowSurface, yellowAccent)

        binding.swatchSetupAccentRed.background = ThemeSwatchUtils.buildSwatchDrawable(this, redBg, redSurface, redAccent)

        binding.swatchSetupAccentGreen.background = ThemeSwatchUtils.buildSwatchDrawable(this, greenBg, greenSurface, greenAccent)

        binding.swatchSetupAccentOrange.background = ThemeSwatchUtils.buildSwatchDrawable(this, orangeBg, orangeSurface, orangeAccent)

    }

private fun selectSetupIntensity(intensity: String) {

        selectedIntensity = intensity

        listOf(

            Triple(binding.cardSetupIntensitySoftTint, binding.checkSetupIntensitySoftTint, "soft_tint"),

            Triple(binding.cardSetupIntensityStrongTint, binding.checkSetupIntensityStrongTint, "strong_tint"),

            Triple(binding.cardSetupIntensityPureMode, binding.checkSetupIntensityPureMode, "pure_mode")

        ).forEach { (card, check, key) ->

            val sel = key == intensity

            card.alpha = if (sel) 1.0f else 0.45f

            card.strokeWidth = if (sel) 3 else 0

            check.visibility = if (sel) View.VISIBLE else View.INVISIBLE

        }

    }

private fun updateSetupIntensityVisibility() {

        val enabled = selectedTheme != "default"

        val isPurple = selectedAccent == "purple"

        val isBlue = selectedAccent == "blue"

        val isYellow = selectedAccent == "yellow"

        val isRed = selectedAccent == "red"

        val isGreen = selectedAccent == "green"

        val isOrange = selectedAccent == "orange"

        val sectionVisibility = if (enabled) View.VISIBLE else View.GONE

        val strongVisibility = if (enabled && (isPurple || isBlue || isYellow || isRed || isGreen || isOrange)) View.VISIBLE else View.GONE

        binding.tvSetupIntensityLabel.visibility = sectionVisibility

        binding.cardSetupIntensitySoftTint.visibility = sectionVisibility

        binding.cardSetupIntensityPureMode.visibility = sectionVisibility

        binding.cardSetupIntensityStrongTint.visibility = strongVisibility

        if (enabled) {

            val pureModeDesc = getString(

                if (selectedTheme == "light") R.string.surface_intensity_pure_light_desc

                else R.string.surface_intensity_pure_dark_desc

            )

            binding.tvSetupIntensityPureModeDesc.text = pureModeDesc

        }

    }

private fun updateSetupIntensitySwatches() {

        val isPurple = selectedAccent == "purple"

        val isBlue = selectedAccent == "blue"

        val isYellow = selectedAccent == "yellow"

        val isRed = selectedAccent == "red"

        val isGreen = selectedAccent == "green"

        val isOrange = selectedAccent == "orange"

        val isLight = selectedTheme == "light"

val accentColor = when {

            isPurple -> ThemeSwatchUtils.resolvePurpleSwatchColors(this, selectedTheme).accent

            isBlue -> ThemeSwatchUtils.resolveBlueSwatchColors(this, selectedTheme).accent

            isYellow -> ThemeSwatchUtils.resolveYellowSwatchColors(this, selectedTheme).accent

            isRed -> ThemeSwatchUtils.resolveRedSwatchColors(this, selectedTheme).accent

            isGreen -> ThemeSwatchUtils.resolveGreenSwatchColors(this, selectedTheme).accent

            isOrange -> ThemeSwatchUtils.resolveOrangeSwatchColors(this, selectedTheme).accent

            else -> MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFFBB86FC.toInt())

        }

val (softBg, softSurface) = ThemeSwatchUtils.resolveSoftTintSwatchBgSurface(this, selectedTheme, selectedAccent)

        val strongBg: Int

        val strongSurface: Int

        when {

            isBlue -> {

                strongBg = ContextCompat.getColor(this, if (isLight) R.color.blue_accent_light_bg else R.color.blue_accent_dark_bg)

                strongSurface = ContextCompat.getColor(this, if (isLight) R.color.blue_accent_light_surface else R.color.blue_accent_dark_surface)

            }

            isYellow -> {

                strongBg = ContextCompat.getColor(this, if (isLight) R.color.yellow_accent_light_bg else R.color.yellow_accent_dark_bg)

                strongSurface = ContextCompat.getColor(this, if (isLight) R.color.yellow_accent_light_surface else R.color.yellow_accent_dark_surface)

            }

            isRed -> {

                strongBg = ContextCompat.getColor(this, if (isLight) R.color.red_accent_light_bg else R.color.red_accent_dark_bg)

                strongSurface = ContextCompat.getColor(this, if (isLight) R.color.red_accent_light_surface else R.color.red_accent_dark_surface)

            }

            isGreen -> {

                strongBg = ContextCompat.getColor(this, if (isLight) R.color.green_accent_light_bg else R.color.green_accent_dark_bg)

                strongSurface = ContextCompat.getColor(this, if (isLight) R.color.green_accent_light_surface else R.color.green_accent_dark_surface)

            }

            isOrange -> {

                strongBg = ContextCompat.getColor(this, if (isLight) R.color.orange_accent_light_bg else R.color.orange_accent_dark_bg)

                strongSurface = ContextCompat.getColor(this, if (isLight) R.color.orange_accent_light_surface else R.color.orange_accent_dark_surface)

            }

            else -> {

                strongBg = ContextCompat.getColor(this, if (isLight) R.color.purple_accent_light_bg else R.color.purple_accent_dark_bg)

                strongSurface = ContextCompat.getColor(this, if (isLight) R.color.purple_accent_light_surface else R.color.purple_accent_dark_surface)

            }

        }

        val pureBg = if (isLight) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

        val pureSurface = if (isLight) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

binding.swatchSetupIntensitySoftTint.background = ThemeSwatchUtils.buildSwatchDrawable(this, softBg, softSurface, accentColor)

        binding.swatchSetupIntensityStrongTint.background = ThemeSwatchUtils.buildSwatchDrawable(this, strongBg, strongSurface, accentColor)

        binding.swatchSetupIntensityPureMode.background = ThemeSwatchUtils.buildSwatchDrawable(this, pureBg, pureSurface, accentColor)

    }

private fun selectEngine(engine: String) {

        selectedEngine = engine

        listOf(

            Triple(binding.cardDuckduckgo, binding.radioDuckduckgo, "duckduckgo"),

            Triple(binding.cardBrave, binding.radioBrave, "brave"),

            Triple(binding.cardGoogle, binding.radioGoogle, "google")

        ).forEach { (card, radio, key) ->

            val sel = key == engine

            card.alpha = if (sel) 1.0f else 0.45f

            card.strokeWidth = if (sel) 3 else 0

            radio.isChecked = sel

        }

    }

private fun selectDohMode(mode: String) {

        selectedDohMode = mode

        listOf(

            Triple(binding.cardDohOff, binding.radioDohSetupOff, DohManager.MODE_OFF),

            Triple(binding.cardDohDefault, binding.radioDohSetupDefault, DohManager.MODE_DEFAULT),

            Triple(binding.cardDohIncreased, binding.radioDohSetupIncreased, DohManager.MODE_INCREASED),

            Triple(binding.cardDohMax, binding.radioDohSetupMax, DohManager.MODE_MAX)

        ).forEach { (card, radio, key) ->

            val sel = key == mode

            card.alpha = if (sel) 1.0f else 0.45f

            card.strokeWidth = if (sel) 3 else 0

            radio.isChecked = sel

        }

        binding.setupProviderSection.visibility =

            if (mode == DohManager.MODE_OFF) View.GONE else View.VISIBLE

    }

private fun selectProvider(provider: String) {

        selectedProvider = provider

        listOf(

            Triple(binding.cardSetupCloudflare, binding.radioSetupCloudflare, DohManager.PROVIDER_CLOUDFLARE),

            Triple(binding.cardSetupQuad9, binding.radioSetupQuad9, DohManager.PROVIDER_QUAD9)

        ).forEach { (card, radio, key) ->

            val sel = key == provider

            card.alpha = if (sel) 1.0f else 0.45f

            card.strokeWidth = if (sel) 3 else 0

            radio.isChecked = sel

        }

    }

private fun saveAndProceed() {

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        prefs.edit()

            .putString("search_engine", selectedEngine)

            .putString("doh_mode", selectedDohMode)

            .putString("doh_provider", selectedProvider)

            .putString("address_bar_position", selectedAddressBarPosition)

            .putString("menu_style", selectedMenuStyle)

            .putString("scroll_hide_mode", selectedScrollHideMode)

            .putBoolean("hide_status_bar", selectedHideStatusBar)

            .putBoolean("setup_complete", true)

            .apply()

        startMainActivity()

    }

private fun startMainActivity() {

        startActivity(Intent(this, MainActivity::class.java))

        finish()

    }

override fun onDestroy() {

        super.onDestroy()

        scrollAnimatorSearchBar?.cancel()

        scrollAnimatorNavBar?.cancel()

        scrollAnimatorBoth?.cancel()

    }

override fun onBackPressed() {

        if (currentPage > 0) showPage(currentPage - 1)

        else super.onBackPressed()

    }

}
package com.jhaiian.clint.quiver

import android.view.View
import com.jhaiian.clint.R

// Expands or collapses the "add filter list" FAB into a small menu offering the
// two ways to add a list: from a local file or from a URL. This only applies
// while the FAB is in its "add" role - refreshFabState always closes the menu
// before switching the FAB into its "save"/compile role, since that role
// performs its action directly and never expands into a menu.
//
// ic_add_24 is a symmetric plus glyph, so rotating the FAB 45 degrees turns it
// into a close ("x") glyph without swapping the drawable.
private const val FAB_MENU_OPEN_ROTATION = 45f
private const val FAB_MENU_ANIMATION_MS = 180L

internal fun QuiverGuardActivity.toggleFabMenu() {
    if (isFabMenuOpen) closeFabMenu() else openFabMenu()
}

internal fun QuiverGuardActivity.openFabMenu() {
    if (isFabMenuOpen) return
    isFabMenuOpen = true

    fabMenuScrim.visibility = View.VISIBLE
    fabMenuOptions.visibility = View.VISIBLE
    fabMenuOptions.alpha = 0f
    fabMenuOptions.translationY = 24f
    fabMenuOptions.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(FAB_MENU_ANIMATION_MS)
        .start()

    fabAdd.animate().rotation(FAB_MENU_OPEN_ROTATION).setDuration(FAB_MENU_ANIMATION_MS).start()
    fabAdd.contentDescription = getString(R.string.filter_list_add_menu_close_desc)
}

internal fun QuiverGuardActivity.closeFabMenu() {
    if (!isFabMenuOpen) return
    isFabMenuOpen = false

    fabAdd.animate().rotation(0f).setDuration(FAB_MENU_ANIMATION_MS).start()
    fabAdd.contentDescription = getString(R.string.filter_list_add_fab_desc)

    fabMenuOptions.animate()
        .alpha(0f)
        .translationY(24f)
        .setDuration(FAB_MENU_ANIMATION_MS)
        .withEndAction {
            fabMenuOptions.visibility = View.GONE
            fabMenuOptions.translationY = 0f
        }
        .start()
    fabMenuScrim.visibility = View.GONE
}

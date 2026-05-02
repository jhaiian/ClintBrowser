package com.jhaiian.clint.settings

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.RadioButton
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity

class BrowserSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.browser_preferences, rootKey)
        applyIconTints()

        findPreference<Preference>("cache_mode")?.setOnPreferenceClickListener {
            showCacheModeDialog()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateCacheModeSummary()
    }

    private fun applyIconTints() {
        val color = MaterialColors.getColor(requireContext(), R.attr.clintIconTint, 0)
        val tint = ColorStateList.valueOf(color)
        listOf("javascript_enabled", "cache_mode").forEach { key ->
            findPreference<Preference>(key)?.let { pref ->
                pref.icon?.mutate()?.let { icon ->
                    DrawableCompat.setTintList(DrawableCompat.wrap(icon), tint)
                    pref.icon = icon
                }
            }
        }
    }

    private fun updateCacheModeSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val summary = when (prefs.getString("cache_mode", "default")) {
            "cache_else_network" -> getString(R.string.cache_mode_summary_cache_first)
            "no_cache"           -> getString(R.string.cache_mode_summary_always_fresh)
            "cache_only"         -> getString(R.string.cache_mode_summary_offline)
            else                 -> getString(R.string.cache_mode_summary_smart)
        }
        findPreference<Preference>("cache_mode")?.summary = summary
    }

    private fun showCacheModeDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = prefs.getString("cache_mode", "default") ?: "default"

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_cache_mode, null)

        val cardSmart       = dialogView.findViewById<MaterialCardView>(R.id.cardCacheSmart)
        val cardCacheFirst  = dialogView.findViewById<MaterialCardView>(R.id.cardCacheFirst)
        val cardAlwaysFresh = dialogView.findViewById<MaterialCardView>(R.id.cardCacheAlwaysFresh)
        val cardOffline     = dialogView.findViewById<MaterialCardView>(R.id.cardCacheOffline)

        val radioSmart       = dialogView.findViewById<RadioButton>(R.id.radioCacheSmart)
        val radioCacheFirst  = dialogView.findViewById<RadioButton>(R.id.radioCacheFirst)
        val radioAlwaysFresh = dialogView.findViewById<RadioButton>(R.id.radioCacheAlwaysFresh)
        val radioOffline     = dialogView.findViewById<RadioButton>(R.id.radioCacheOffline)

        val cardMap = mapOf(
            "default"            to cardSmart,
            "cache_else_network" to cardCacheFirst,
            "no_cache"           to cardAlwaysFresh,
            "cache_only"         to cardOffline
        )
        val radioMap = mapOf(
            "default"            to radioSmart,
            "cache_else_network" to radioCacheFirst,
            "no_cache"           to radioAlwaysFresh,
            "cache_only"         to radioOffline
        )

        var selected = current
        val strokePx = (3 * resources.displayMetrics.density).toInt()

        fun selectOption(key: String) {
            selected = key
            cardMap.forEach { (k, card) ->
                val active = k == key
                card.strokeWidth = if (active) strokePx else 0
                card.alpha = if (active) 1f else 0.45f
                radioMap[k]?.isChecked = active
            }
        }

        selectOption(current)

        cardSmart.setOnClickListener       { selectOption("default") }
        cardCacheFirst.setOnClickListener  { selectOption("cache_else_network") }
        cardAlwaysFresh.setOnClickListener { selectOption("no_cache") }
        cardOffline.setOnClickListener     { selectOption("cache_only") }

        MaterialAlertDialogBuilder(requireContext(), (requireActivity() as ClintActivity).getDialogTheme())
            .setTitle(getString(R.string.cache_mode_dialog_title))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putString("cache_mode", selected).apply()
                updateCacheModeSummary()
            }
            .create().also { (requireActivity() as ClintActivity).applyStatusBarFlagToDialog(it) }.show()
    }
}

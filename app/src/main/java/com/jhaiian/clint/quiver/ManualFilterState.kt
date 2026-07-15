package com.jhaiian.clint.quiver

import android.content.Context
import androidx.preference.PreferenceManager

// Small stateless helpers describing how the manual filter participates in a compile: the
// reserved id it is compiled under (parallel to a FilterList.id, but never stored in
// FilterListDatabase since it isn't a downloadable list), whether the user has switched it on,
// and a lightweight fingerprint used to detect rule-content changes without diffing the list.
internal object ManualFilterState {

    // Negative so it can never collide with a FilterListDatabase autoincrement row id.
    const val COMPILE_ID = -1L

    private const val PREF_ENABLED = "quiver_guard_manual_filter_enabled"

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PREF_ENABLED, enabled)
            .apply()
    }

    // Changes whenever rule text, count, or order changes. Stable across app restarts because
    // it is built from String.hashCode(), which uses a fixed algorithm rather than a
    // per-process seed, so it can be persisted in the compiled manifest and compared later.
    fun contentFingerprint(rules: List<ManualFilterRule>): String {
        val joined = rules.joinToString("\n") { it.ruleText }
        return "${rules.size}:${joined.hashCode()}"
    }
}

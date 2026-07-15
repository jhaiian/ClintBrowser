package com.jhaiian.clint.quiver

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.ClintToast

// Lets the user maintain their own list of AdBlock/uBlock filter rules alongside the downloaded
// filter lists QuiverGuardActivity manages. Rules are written straight to ManualFilterDatabase
// as the user adds, edits, or deletes them, since there is no staged/pending state the way
// filter list toggles have, but they still need a compile back in QuiverGuardActivity before
// they affect actual filtering, exactly like a newly downloaded filter list does.
class ManualFilterActivity : ClintActivity() {

    private lateinit var db: ManualFilterDatabase
    private lateinit var adapter: ManualFilterRuleAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var switchEnabled: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_manual_filter)

        val toolbar = findViewById<View>(R.id.manual_filter_toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        db = ManualFilterDatabase(this)
        recycler = findViewById(R.id.recycler_manual_filter_rules)
        tvEmpty = findViewById(R.id.tv_empty)
        switchEnabled = findViewById(R.id.switch_manual_filter)

        val fabAdd = findViewById<FloatingActionButton>(R.id.fab_add_rule)
        val fabMarginBottomPx = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(fabAdd) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = fabMarginBottomPx + navBars.bottom
            v.layoutParams = lp
            insets
        }

        val masterRow = findViewById<View>(R.id.row_manual_filter_master)
        switchEnabled.isChecked = ManualFilterState.isEnabled(this)
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ManualFilterState.setEnabled(this, isChecked)
        }
        masterRow.setOnClickListener { switchEnabled.isChecked = !switchEnabled.isChecked }

        adapter = ManualFilterRuleAdapter(
            onRuleClick = { rule -> showEditRuleDialog(rule) },
            onDeleteClick = { rule -> showDeleteRuleDialog(rule) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fabAdd.setOnClickListener { showAddRuleDialog() }

        refreshList()
    }

    private fun refreshList() {
        val rules = db.getAllRules()
        adapter.updateItems(rules)
        val hasItems = rules.isNotEmpty()
        recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
        tvEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
    }

    // Splits the input on newlines so pasting several rules at once adds them all in one go.
    // Blank lines and exact duplicates of existing rules are silently skipped rather than
    // rejected, since re-pasting the same block is a common, harmless mistake.
    private fun showAddRuleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_filter_rule, null)
        val til = dialogView.findViewById<TextInputLayout>(R.id.til_manual_filter_rule)
        val et = dialogView.findViewById<TextInputEditText>(R.id.et_manual_filter_rule)

        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.quiver_guard_manual_filter_add_dialog_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.filter_list_add_action_add)) { _, _ ->
                val lines = (et.text?.toString() ?: "")
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (lines.isEmpty()) {
                    til.error = getString(R.string.quiver_guard_manual_filter_error_empty)
                    return@setPositiveButton
                }
                val addedCount = db.addRules(lines)
                refreshList()
                val message = if (addedCount > 0) {
                    getString(R.string.quiver_guard_manual_filter_added_toast, addedCount)
                } else {
                    getString(R.string.quiver_guard_manual_filter_no_new_rules_toast)
                }
                ClintToast.show(this, message, R.drawable.ic_check_24)
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    // Newlines are collapsed to spaces before saving so a single edited rule can never
    // accidentally split into multiple lines in the on-disk rule file QuiverGuardCompiler reads.
    private fun showEditRuleDialog(rule: ManualFilterRule) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_filter_rule, null)
        val til = dialogView.findViewById<TextInputLayout>(R.id.til_manual_filter_rule)
        val et = dialogView.findViewById<TextInputEditText>(R.id.et_manual_filter_rule)
        et.setText(rule.ruleText)
        dialogView.findViewById<TextView>(R.id.tv_manual_filter_rule_helper).visibility = View.GONE

        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.quiver_guard_manual_filter_edit_dialog_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.quiver_guard_manual_filter_edit_action_save)) { _, _ ->
                val text = (et.text?.toString() ?: "").replace("\n", " ").trim()
                if (text.isEmpty()) {
                    til.error = getString(R.string.quiver_guard_manual_filter_error_empty)
                    return@setPositiveButton
                }
                db.updateRuleText(rule.id, text)
                refreshList()
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun showDeleteRuleDialog(rule: ManualFilterRule) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.quiver_guard_manual_filter_delete_confirm_title))
            .setMessage(getString(R.string.quiver_guard_manual_filter_delete_confirm_message, rule.ruleText))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.filter_list_menu_remove)) { _, _ ->
                db.deleteRule(rule.id)
                refreshList()
                ClintToast.show(
                    this,
                    getString(R.string.quiver_guard_manual_filter_rule_deleted_toast),
                    R.drawable.ic_delete_24
                )
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }
}

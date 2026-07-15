package com.jhaiian.clint.quiver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jhaiian.clint.R

// Flat, unsorted list of the user's manual filter rules. Unlike FilterListAdapter this has no
// search, sort, or multi-select: rules are shown in the order ManualFilterDatabase returns them
// (oldest first), and each row's only actions are tap-to-edit and a trailing delete button.
internal class ManualFilterRuleAdapter(
    private val onRuleClick: (ManualFilterRule) -> Unit,
    private val onDeleteClick: (ManualFilterRule) -> Unit
) : RecyclerView.Adapter<ManualFilterRuleAdapter.ViewHolder>() {

    private val items: MutableList<ManualFilterRule> = mutableListOf()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val row: View = view.findViewById(R.id.manual_filter_rule_row)
        val text: TextView = view.findViewById(R.id.manual_filter_rule_text)
        val btnDelete: ImageView = view.findViewById(R.id.manual_filter_rule_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manual_filter_rule, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.text.text = item.ruleText
        holder.row.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onRuleClick(items[pos])
        }
        holder.btnDelete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDeleteClick(items[pos])
        }
    }

    fun updateItems(newItems: List<ManualFilterRule>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

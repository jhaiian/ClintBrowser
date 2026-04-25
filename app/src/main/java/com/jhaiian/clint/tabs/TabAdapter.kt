package com.jhaiian.clint.tabs

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.FaviconCache

class TabAdapter(
    private val tabs: MutableList<TabPreview>,
    private val activeIndex: Int,
    private val onTabClick: (Int) -> Unit,
    private val onTabClose: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class Header(val isIncognito: Boolean) : Item()
        data class Tab(val preview: TabPreview, val tabIndex: Int) : Item()
    }

    private var items: List<Item> = buildItems()

    private fun buildItems(): List<Item> {
        val result = mutableListOf<Item>()
        val normal = tabs.mapIndexedNotNull { i, t -> if (!t.isIncognito) Pair(i, t) else null }
        val incognito = tabs.mapIndexedNotNull { i, t -> if (t.isIncognito) Pair(i, t) else null }
        if (normal.isNotEmpty()) {
            result.add(Item.Header(false))
            normal.forEach { (i, t) -> result.add(Item.Tab(t, i)) }
        }
        if (incognito.isNotEmpty()) {
            result.add(Item.Header(true))
            incognito.forEach { (i, t) -> result.add(Item.Tab(t, i)) }
        }
        return result
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.headerIcon)
        val label: TextView = view.findViewById(R.id.headerLabel)
    }

    inner class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.tabIcon)
        val title: TextView = view.findViewById(R.id.tabTitle)
        val url: TextView = view.findViewById(R.id.tabUrl)
        val closeBtn: ImageButton = view.findViewById(R.id.tabClose)
        val activeIndicator: View = view.findViewById(R.id.tabActiveIndicator)
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is Item.Header -> VIEW_TYPE_HEADER
        is Item.Tab -> VIEW_TYPE_TAB
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_tab_header, parent, false)
            )
            else -> TabViewHolder(
                inflater.inflate(R.layout.item_tab, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> bindHeader(holder as HeaderViewHolder, item)
            is Item.Tab -> bindTab(holder as TabViewHolder, item, position)
        }
    }

    private fun bindHeader(holder: HeaderViewHolder, item: Item.Header) {
        val ctx = holder.itemView.context
        if (item.isIncognito) {
            holder.label.text = ctx.getString(R.string.tabs_section_incognito)
            holder.icon.setImageResource(R.drawable.ic_incognito_24)
            val tint = ContextCompat.getColor(ctx, R.color.incognito_accent)
            ImageViewCompat.setImageTintList(holder.icon, ColorStateList.valueOf(tint))
            holder.label.setTextColor(tint)
        } else {
            holder.label.text = ctx.getString(R.string.tabs_section_normal)
            holder.icon.setImageResource(R.drawable.ic_tab_24)
            val tint = MaterialColors.getColor(holder.icon, R.attr.clintSecondaryTextColor)
            ImageViewCompat.setImageTintList(holder.icon, ColorStateList.valueOf(tint))
            holder.label.setTextColor(tint)
        }
    }

    private fun bindTab(holder: TabViewHolder, item: Item.Tab, position: Int) {
        val tab = item.preview
        val ctx = holder.itemView.context
        holder.title.text = tab.title.ifBlank { ctx.getString(R.string.new_tab) }
        holder.url.text = tab.url.removePrefix("https://").removePrefix("http://").ifBlank { "" }
        holder.activeIndicator.visibility = if (item.tabIndex == activeIndex) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onTabClick(item.tabIndex) }
        holder.closeBtn.setOnClickListener { onTabClose(item.tabIndex) }

        val primaryColor = MaterialColors.getColor(holder.icon, com.google.android.material.R.attr.colorPrimary)

        if (tab.isIncognito) {
            holder.icon.setImageResource(R.drawable.ic_incognito_24)
            ImageViewCompat.setImageTintList(holder.icon, ColorStateList.valueOf(primaryColor))
        } else {
            holder.icon.setImageResource(R.drawable.ic_globe_24)
            ImageViewCompat.setImageTintList(holder.icon, ColorStateList.valueOf(primaryColor))
        }

        val faviconUrl = FaviconCache.faviconUrlFor(tab.url)
        if (faviconUrl.isEmpty()) return

        if (tab.isIncognito) {
            FaviconCache.loadMemoryOnly(faviconUrl) { bmp ->
                if (holder.bindingAdapterPosition == position && bmp != null) {
                    holder.icon.setImageBitmap(bmp)
                    ImageViewCompat.setImageTintList(holder.icon, null)
                }
            }
        } else {
            FaviconCache.load(ctx, faviconUrl) { bmp ->
                if (holder.bindingAdapterPosition == position && bmp != null) {
                    holder.icon.setImageBitmap(bmp)
                    ImageViewCompat.setImageTintList(holder.icon, null)
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun removeAt(tabIndex: Int) {
        if (tabIndex in tabs.indices) {
            tabs.removeAt(tabIndex)
            items = buildItems()
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_TAB = 1
    }
}

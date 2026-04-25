package com.jhaiian.clint.bookmarks

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.FaviconCache

class BookmarksAdapter(
    private val items: MutableList<Bookmark>,
    private val onOpen: (Bookmark) -> Unit,
    private val onDelete: (Bookmark, Int) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val favicon: ImageView = view.findViewById(R.id.bookmark_favicon)
        val title: TextView = view.findViewById(R.id.bookmark_title)
        val url: TextView = view.findViewById(R.id.bookmark_url)
        val deleteBtn: ImageButton = view.findViewById(R.id.bookmark_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bookmark = items[position]
        holder.title.text = bookmark.title.ifBlank { bookmark.url }
        holder.url.text = bookmark.url.removePrefix("https://").removePrefix("http://")

        holder.favicon.setImageResource(R.drawable.ic_globe_24)
        val primaryColor = MaterialColors.getColor(holder.favicon, com.google.android.material.R.attr.colorPrimary)
        ImageViewCompat.setImageTintList(holder.favicon, ColorStateList.valueOf(primaryColor))

        val faviconUrl = FaviconCache.faviconUrlFor(bookmark.url)

        if (faviconUrl.isNotEmpty()) {
            FaviconCache.load(holder.itemView.context, faviconUrl) { bmp ->
                if (holder.bindingAdapterPosition == position && bmp != null) {
                    holder.favicon.setImageBitmap(bmp)
                    ImageViewCompat.setImageTintList(holder.favicon, null)
                }
            }
        }

        holder.itemView.setOnClickListener { onOpen(bookmark) }
        holder.deleteBtn.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) {
                onDelete(bookmark, pos)
            }
        }
    }

    fun removeAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }
}

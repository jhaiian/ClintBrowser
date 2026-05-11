package com.jhaiian.clint.browser.suggestions

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.bookmarks.Bookmark
import com.jhaiian.clint.databinding.ItemSearchSuggestionBinding
import com.jhaiian.clint.history.HistoryItem
import com.jhaiian.clint.ui.FaviconCache

internal class SearchSuggestionsAdapter(
    private val onItemClick: (String) -> Unit,
    private val onItemFill: (String) -> Unit,
    private val onHistoryDelete: (String) -> Unit
) : RecyclerView.Adapter<SearchSuggestionsAdapter.ViewHolder>() {

    enum class ItemType { BOOKMARK, HISTORY, SUGGESTION }

    data class Item(
        val query: String,
        val displayText: String,
        val type: ItemType
    )

    private val items = mutableListOf<Item>()

    fun submitCombined(bookmarks: List<Bookmark>, history: List<HistoryItem>, suggestions: List<String>) {
        items.clear()
        val seenUrls = mutableSetOf<String>()
        bookmarks.forEach {
            seenUrls.add(it.url)
            items.add(Item(it.url, it.title.ifBlank { it.url }, ItemType.BOOKMARK))
        }
        history.forEach {
            if (seenUrls.add(it.query)) {
                items.add(Item(it.query, it.title.ifBlank { it.query }, ItemType.HISTORY))
            }
        }
        suggestions.forEach {
            if (seenUrls.add(it)) {
                items.add(Item(it, it, ItemType.SUGGESTION))
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchSuggestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(
        private val binding: ItemSearchSuggestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item, position: Int) {
            val iconTint = ColorStateList.valueOf(
                MaterialColors.getColor(binding.suggestionIcon, R.attr.clintIconTint)
            )

            when (item.type) {
                ItemType.BOOKMARK -> {
                    binding.suggestionText.text = item.displayText
                    binding.suggestionUrl.text = formatUrl(item.query)
                    binding.suggestionUrl.visibility = View.VISIBLE
                    binding.suggestionIcon.setImageResource(R.drawable.ic_bookmark_filled_24)
                    ImageViewCompat.setImageTintList(binding.suggestionIcon, iconTint)
                    binding.suggestionIcon.alpha = 0.7f
                    binding.btnDelete.visibility = View.GONE
                    binding.btnDelete.setOnClickListener(null)

                    val faviconUrl = FaviconCache.faviconUrlFor(item.query)
                    if (faviconUrl.isNotEmpty()) {
                        FaviconCache.load(binding.root.context, faviconUrl, cacheOnly = false) { bmp ->
                            if (bindingAdapterPosition == position && bmp != null) {
                                binding.suggestionIcon.setImageBitmap(bmp)
                                ImageViewCompat.setImageTintList(binding.suggestionIcon, null)
                                binding.suggestionIcon.alpha = 1.0f
                            }
                        }
                    }
                }

                ItemType.HISTORY -> {
                    val isUrl = item.query.startsWith("http")
                    if (isUrl && item.displayText != item.query) {
                        binding.suggestionText.text = item.displayText
                        binding.suggestionUrl.text = formatUrl(item.query)
                        binding.suggestionUrl.visibility = View.VISIBLE
                    } else {
                        binding.suggestionText.text = item.displayText
                        binding.suggestionUrl.visibility = View.GONE
                    }
                    binding.suggestionIcon.setImageResource(R.drawable.ic_history_24)
                    ImageViewCompat.setImageTintList(binding.suggestionIcon, iconTint)
                    binding.suggestionIcon.alpha = 0.7f
                    binding.btnDelete.visibility = View.VISIBLE
                    binding.btnDelete.setOnClickListener { onHistoryDelete(item.query) }

                    if (isUrl) {
                        val faviconUrl = FaviconCache.faviconUrlFor(item.query)
                        if (faviconUrl.isNotEmpty()) {
                            FaviconCache.load(binding.root.context, faviconUrl, cacheOnly = false) { bmp ->
                                if (bindingAdapterPosition == position && bmp != null) {
                                    binding.suggestionIcon.setImageBitmap(bmp)
                                    ImageViewCompat.setImageTintList(binding.suggestionIcon, null)
                                    binding.suggestionIcon.alpha = 1.0f
                                }
                            }
                        }
                    }
                }

                ItemType.SUGGESTION -> {
                    binding.suggestionText.text = item.displayText
                    binding.suggestionUrl.visibility = View.GONE
                    binding.suggestionIcon.setImageResource(R.drawable.ic_search_24)
                    ImageViewCompat.setImageTintList(binding.suggestionIcon, iconTint)
                    binding.suggestionIcon.alpha = 0.7f
                    binding.btnDelete.visibility = View.GONE
                    binding.btnDelete.setOnClickListener(null)
                }
            }

            binding.root.setOnClickListener { onItemClick(item.query) }
            binding.btnFill.setOnClickListener { onItemFill(item.query) }
        }

        private fun formatUrl(url: String): String {
            return url.removePrefix("https://").removePrefix("http://")
        }
    }
}

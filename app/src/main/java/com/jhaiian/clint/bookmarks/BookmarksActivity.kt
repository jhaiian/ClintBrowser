package com.jhaiian.clint.bookmarks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity

class BookmarksActivity : ClintActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        val toolbar = findViewById<View>(R.id.bookmarks_toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBars.top, 0, 0)
            insets
        }

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        val emptyView = findViewById<TextView>(R.id.bookmarks_empty)
        val recycler = findViewById<RecyclerView>(R.id.bookmarks_recycler)

        val bookmarks = BookmarkManager.getAll(this)

        if (bookmarks.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recycler.visibility = View.VISIBLE

            val adapter = BookmarksAdapter(
                items = bookmarks,
                onOpen = { bookmark ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bookmark.url))
                    intent.setPackage(packageName)
                    startActivity(intent)
                    finish()
                },
                onDelete = { bookmark, position ->
                    BookmarkManager.remove(this, bookmark.url)
                    (recycler.adapter as BookmarksAdapter).removeAt(position)
                    if (bookmarks.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                        recycler.visibility = View.GONE
                    }
                }
            )
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = adapter
        }
    }
}

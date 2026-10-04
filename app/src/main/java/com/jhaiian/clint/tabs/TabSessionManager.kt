package com.jhaiian.clint.tabs

import android.content.ContentValues
import android.content.Context

data class SavedTab(
    val position: Int,
    val url: String,
    val title: String,
    val isActive: Boolean,
    val tabId: String,
    val shortcutId: String? = null,
    val lastActiveAt: Long = System.currentTimeMillis()
)

object TabSessionManager {

    @Volatile private var db: TabDatabase? = null

    private fun db(context: Context): TabDatabase {
        return db ?: synchronized(this) {
            db ?: TabDatabase(context.applicationContext).also { db = it }
        }
    }

    fun save(context: Context, tabs: List<SavedTab>) {
        val writable = db(context).writableDatabase
        writable.beginTransaction()
        try {
            writable.delete(TabDatabase.TABLE, null, null)
            tabs.forEach { tab ->
                val values = ContentValues().apply {
                    put(TabDatabase.COL_POSITION, tab.position)
                    put(TabDatabase.COL_URL, tab.url)
                    put(TabDatabase.COL_TITLE, tab.title)
                    put(TabDatabase.COL_ACTIVE, if (tab.isActive) 1 else 0)
                    put(TabDatabase.COL_TAB_ID, tab.tabId)
                    put(TabDatabase.COL_SHORTCUT_ID, tab.shortcutId)
                    put(TabDatabase.COL_LAST_ACTIVE, tab.lastActiveAt)
                }
                writable.insert(TabDatabase.TABLE, null, values)
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    fun load(context: Context): List<SavedTab> {
        val cursor = db(context).readableDatabase.query(
            TabDatabase.TABLE,
            arrayOf(
                TabDatabase.COL_POSITION,
                TabDatabase.COL_URL,
                TabDatabase.COL_TITLE,
                TabDatabase.COL_ACTIVE,
                TabDatabase.COL_TAB_ID,
                TabDatabase.COL_SHORTCUT_ID,
                TabDatabase.COL_LAST_ACTIVE
            ),
            null, null, null, null,
            "${TabDatabase.COL_POSITION} ASC"
        )
        val list = mutableListOf<SavedTab>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SavedTab(
                        position = it.getInt(0),
                        url = it.getString(1),
                        title = it.getString(2),
                        isActive = it.getInt(3) == 1,
                        tabId = it.getString(4) ?: java.util.UUID.randomUUID().toString(),
                        shortcutId = it.getString(5),
                        lastActiveAt = it.getLong(6)
                    )
                )
            }
        }
        return list
    }

    fun isEmpty(context: Context): Boolean {
        val cursor = db(context).readableDatabase.query(
            TabDatabase.TABLE,
            arrayOf(TabDatabase.COL_ID),
            null, null, null, null, null,
            "1"
        )
        return cursor.use { !it.moveToFirst() }
    }

    fun clear(context: Context) {
        db(context).writableDatabase.delete(TabDatabase.TABLE, null, null)
    }
}

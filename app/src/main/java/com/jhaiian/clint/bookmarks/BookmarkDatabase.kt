package com.jhaiian.clint.bookmarks

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class BookmarkDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE (
                $COL_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_URL         TEXT    NOT NULL UNIQUE,
                $COL_TITLE       TEXT    NOT NULL DEFAULT '',
                $COL_FAVICON_URL TEXT    NOT NULL DEFAULT '',
                $COL_ADDED_AT    INTEGER NOT NULL DEFAULT 0,
                $COL_LAST_VISIT  INTEGER NOT NULL DEFAULT 0
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_LAST_VISIT INTEGER NOT NULL DEFAULT 0")
        }
    }

    companion object {
        const val DB_NAME        = "clint_bookmarks.db"
        const val DB_VERSION     = 2
        const val TABLE          = "bookmarks"
        const val COL_ID         = "id"
        const val COL_URL        = "url"
        const val COL_TITLE      = "title"
        const val COL_FAVICON_URL = "favicon_url"
        const val COL_ADDED_AT   = "added_at"
        const val COL_LAST_VISIT = "last_visit"
    }
}

package com.jhaiian.clint.tabs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class TabDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE (
                $COL_ID       INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_POSITION INTEGER NOT NULL,
                $COL_URL      TEXT    NOT NULL,
                $COL_TITLE    TEXT    NOT NULL DEFAULT '',
                $COL_ACTIVE   INTEGER NOT NULL DEFAULT 0
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    companion object {
        const val DB_NAME    = "clint_tabs.db"
        const val DB_VERSION = 1
        const val TABLE      = "tabs"
        const val COL_ID       = "id"
        const val COL_POSITION = "position"
        const val COL_URL      = "url"
        const val COL_TITLE    = "title"
        const val COL_ACTIVE   = "active"
    }
}

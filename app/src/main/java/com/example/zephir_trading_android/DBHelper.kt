package com.example.zephir_trading_android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "trading.db"
        private const val DATABASE_VERSION = 1

        private const val SQL_CREATE_ENTRIES =
            """
            CREATE TABLE IF NOT EXISTS Messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                asset TEXT,
                timestamp TEXT,
                side TEXT,
                url TEXT
            )
            """

        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS Messages"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }
}
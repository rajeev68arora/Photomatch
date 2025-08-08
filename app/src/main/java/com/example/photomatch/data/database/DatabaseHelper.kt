package com.example.photomatch.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLiteOpenHelper for managing the PhotoMatch database
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(MatchContract.SQL_CREATE_TABLE)
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For now, just drop and recreate the table
        // In production, you'd want proper migration logic
        db.execSQL(MatchContract.SQL_DELETE_TABLE)
        onCreate(db)
    }
    
    companion object {
        private const val DATABASE_NAME = "photomatch.db"
        private const val DATABASE_VERSION = 1
    }
}
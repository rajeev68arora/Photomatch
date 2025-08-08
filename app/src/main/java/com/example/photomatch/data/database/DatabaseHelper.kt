package com.example.photomatch.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLiteOpenHelper for managing the PhotoMatch database with people and matches tables
 * 
 * DATABASE VERSIONING:
 * - Version 1: Original matches table only
 * - Version 2: Added people table with face embeddings and bitmaps
 * 
 * MIGRATION STRATEGY:
 * - Preserves existing match data during upgrades
 * - Creates new people table with proper indexes
 * - Maintains backward compatibility with existing matches
 * 
 * FEATURES:
 * - People table: Persistent face storage with embeddings
 * - Matches table: Enhanced with person_id foreign key
 * - Indexes: Optimized for name searches and usage patterns
 * - Foreign key constraints: Maintain data integrity
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    
    override fun onCreate(db: SQLiteDatabase) {
        // Create people table first (parent table)
        db.execSQL(MatchContract.SQL_CREATE_PEOPLE_TABLE)
        
        // Create matches table with foreign key to people
        db.execSQL(MatchContract.SQL_CREATE_MATCHES_TABLE)
        
        // Create indexes for optimal performance
        db.execSQL(MatchContract.SQL_CREATE_PEOPLE_NAME_INDEX)
        db.execSQL(MatchContract.SQL_CREATE_PEOPLE_USAGE_INDEX)
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> {
                // Migrate from version 1 to 2: Add people table and update matches
                migrateFromV1ToV2(db)
            }
            // Add more migration cases as needed for future versions
            else -> {
                // Fallback: Drop and recreate all tables
                // Only use this during development - in production preserve user data
                db.execSQL(MatchContract.SQL_DELETE_MATCHES_TABLE)
                db.execSQL(MatchContract.SQL_DELETE_PEOPLE_TABLE)
                onCreate(db)
            }
        }
    }
    
    /**
     * Migrate database from version 1 (matches only) to version 2 (people + matches)
     * 
     * MIGRATION STEPS:
     * 1. Create people table with indexes
     * 2. Add person_id column to existing matches table
     * 3. Preserve all existing match data
     * 4. Enable foreign key constraints
     */
    private fun migrateFromV1ToV2(db: SQLiteDatabase) {
        // Step 1: Create the new people table
        db.execSQL(MatchContract.SQL_CREATE_PEOPLE_TABLE)
        db.execSQL(MatchContract.SQL_CREATE_PEOPLE_NAME_INDEX)
        db.execSQL(MatchContract.SQL_CREATE_PEOPLE_USAGE_INDEX)
        
        // Step 2: Add person_id column to existing matches table
        db.execSQL("ALTER TABLE ${MatchContract.MatchEntry.TABLE_NAME} ADD COLUMN ${MatchContract.MatchEntry.COLUMN_PERSON_ID} INTEGER")
        
        // Note: Existing matches will have person_id = NULL, which is fine
        // They will continue to work using the legacy first_name/last_name columns
        // New matches will use the person_id foreign key approach
        
        // Step 3: Enable foreign key constraints for new data integrity
        db.execSQL("PRAGMA foreign_keys = ON")
    }
    
    companion object {
        private const val DATABASE_NAME = "photomatch.db"
        private const val DATABASE_VERSION = 2 // Updated to support people table
    }
}
package com.example.photomatch.data.database

import android.provider.BaseColumns

/**
 * Contract class for the Match database schema
 * 
 * DATABASE EVOLUTION HISTORY:
 * 1. Initially designed for Room ORM with kapt annotation processing
 * 2. Encountered build issues: "Plugin [id: 'com.google.devtools.ksp', version: '2.2.0-1.0.29'] was not found"
 * 3. User instructed: "upgrade ksp rather than downgrade kotlin" but plugin unavailable
 * 4. User decided: "go with kapt for now" but kapt compilation failed repeatedly
 * 5. User requested: "for the database, shift to sqlite instead of room db"
 * 6. Migrated to native SQLite with custom repository pattern
 * 7. User requested: "remove these rejected records" - removed REJECTED from enum
 * 
 * CURRENT SCHEMA:
 * - Stores only positive matches (AUTO_MATCH and CONFIRMED types)
 * - Includes person name (first/last) for match association
 * - Individual similarity scores per face detection result
 * - Photo URI for result display and navigation
 */
object MatchContract {
    
    /**
     * Database table structure for storing face matching results
     * 
     * COLUMN DESIGN RATIONALE:
     * - photo_uri: File path for result display and user navigation
     * - person_first_name/person_last_name: User-provided names from input dialog
     * - similarity_score: Individual face similarity (0.0-1.0) for detailed analysis
     * - match_type: AUTO_MATCH (≥60%) or CONFIRMED (40-60% user-approved)
     * - timestamp: Match detection time for chronological sorting
     */
    object MatchEntry : BaseColumns {
        const val TABLE_NAME = "matches"
        const val COLUMN_PHOTO_URI = "photo_uri"              // Photo file location
        const val COLUMN_PERSON_FIRST_NAME = "person_first_name" // From input dialog
        const val COLUMN_PERSON_LAST_NAME = "person_last_name"   // From input dialog
        const val COLUMN_SIMILARITY_SCORE = "similarity_score"   // Individual face score
        const val COLUMN_MATCH_TYPE = "match_type"            // AUTO_MATCH or CONFIRMED
        const val COLUMN_TIMESTAMP = "timestamp"              // Detection time
    }
    
    // SQL statement to create the table
    const val SQL_CREATE_TABLE = """
        CREATE TABLE ${MatchEntry.TABLE_NAME} (
            ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${MatchEntry.COLUMN_PHOTO_URI} TEXT NOT NULL,
            ${MatchEntry.COLUMN_PERSON_FIRST_NAME} TEXT NOT NULL,
            ${MatchEntry.COLUMN_PERSON_LAST_NAME} TEXT NOT NULL,
            ${MatchEntry.COLUMN_SIMILARITY_SCORE} REAL NOT NULL,
            ${MatchEntry.COLUMN_MATCH_TYPE} TEXT NOT NULL,
            ${MatchEntry.COLUMN_TIMESTAMP} INTEGER NOT NULL
        )
    """
    
    // SQL statement to drop the table
    const val SQL_DELETE_TABLE = "DROP TABLE IF EXISTS ${MatchEntry.TABLE_NAME}"
}

/**
 * Enum for match types in three-tier threshold system
 * 
 * EVOLUTION OF MATCH TYPES:
 * 1. Originally: AUTO_MATCH, CONFIRMED, REJECTED (stored all three types)
 * 2. User feedback: "remove these rejected records"
 * 3. Current: Only AUTO_MATCH and CONFIRMED (positive matches only)
 * 
 * STORAGE POLICY:
 * - AUTO_MATCH: High confidence (≥60%) matches saved automatically
 * - CONFIRMED: Medium confidence (40-60%) matches approved by user via Yes button
 * - REJECTED: Low confidence (≤40%) and user-rejected matches are NOT stored
 * 
 * BENEFITS:
 * - Cleaner database with only meaningful matches
 * - Faster queries without filtering out rejected records
 * - Reduced storage requirements
 * - Simplified summary reports
 */
enum class MatchType {
    AUTO_MATCH,     // ≥60% similarity - automatic match, saved immediately
    CONFIRMED       // 40-60% similarity - user confirmed via integrated Yes button
    // REJECTED type removed per user request: "remove these rejected records"
    // Rejected matches (≤40% or user No button) are not persisted to database
}
package com.example.photomatch.data.database

import android.provider.BaseColumns

/**
 * Contract class for the database schema supporting both matches and persistent people
 * 
 * DATABASE EVOLUTION HISTORY:
 * 1. Initially designed for Room ORM with kapt annotation processing
 * 2. Encountered build issues: "Plugin [id: 'com.google.devtools.ksp', version: '2.2.0-1.0.29'] was not found"
 * 3. User instructed: "upgrade ksp rather than downgrade kotlin" but plugin unavailable
 * 4. User decided: "go with kapt for now" but kapt compilation failed repeatedly
 * 5. User requested: "for the database, shift to sqlite instead of room db"
 * 6. Migrated to native SQLite with custom repository pattern
 * 7. User requested: "remove these rejected records" - removed REJECTED from enum
 * 8. NEW: Added persistent people storage with face embeddings and duplicate detection
 * 
 * CURRENT SCHEMA:
 * - matches table: Stores only positive matches (AUTO_MATCH and CONFIRMED types)
 * - people table: Persistent storage of individuals with face embeddings and bitmaps
 * - Relationship: matches.person_id references people._id for better organization
 * - Individual similarity scores per face detection result
 * - Photo URIs for result display and navigation
 */
object MatchContract {
    
    /**
     * People table for persistent face storage
     * 
     * DESIGN RATIONALE:
     * - Enables reusable face recognition across sessions
     * - Stores face embeddings as BLOB for fast similarity comparisons
     * - Face bitmaps stored as compressed BLOB for UI thumbnails
     * - Usage tracking for smart person suggestions
     * - Duplicate detection through embedding similarity
     */
    object PeopleEntry : BaseColumns {
        const val TABLE_NAME = "people"
        const val COLUMN_FIRST_NAME = "first_name"              // Person's first name
        const val COLUMN_LAST_NAME = "last_name"                // Person's last name
        const val COLUMN_REFERENCE_PHOTO_URI = "reference_photo_uri" // Original photo path
        const val COLUMN_FACE_EMBEDDING = "face_embedding"      // 128-dim float array as BLOB
        const val COLUMN_FACE_BITMAP = "face_bitmap"            // Cropped face image as BLOB
        const val COLUMN_SIMILARITY_THRESHOLD = "similarity_threshold" // Custom threshold per person
        const val COLUMN_CREATED_TIMESTAMP = "created_timestamp" // When person was added
        const val COLUMN_LAST_USED_TIMESTAMP = "last_used_timestamp" // Last usage for sorting
    }
    
    /**
     * Database table structure for storing face matching results
     * 
     * UPDATED COLUMN DESIGN:
     * - photo_uri: File path for result display and user navigation
     * - person_id: Foreign key reference to people table (NEW)
     * - person_first_name/person_last_name: Legacy names for backward compatibility
     * - similarity_score: Individual face similarity (0.0-1.0) for detailed analysis
     * - match_type: AUTO_MATCH (≥60%) or CONFIRMED (40-60% user-approved)
     * - timestamp: Match detection time for chronological sorting
     */
    object MatchEntry : BaseColumns {
        const val TABLE_NAME = "matches"
        const val COLUMN_PHOTO_URI = "photo_uri"              // Photo file location
        const val COLUMN_PERSON_ID = "person_id"              // Foreign key to people table (NEW)
        const val COLUMN_PERSON_FIRST_NAME = "person_first_name" // Legacy - for backward compatibility
        const val COLUMN_PERSON_LAST_NAME = "person_last_name"   // Legacy - for backward compatibility
        const val COLUMN_SIMILARITY_SCORE = "similarity_score"   // Individual face score
        const val COLUMN_MATCH_TYPE = "match_type"            // AUTO_MATCH or CONFIRMED
        const val COLUMN_TIMESTAMP = "timestamp"              // Detection time
    }
    
    // SQL statement to create the people table
    const val SQL_CREATE_PEOPLE_TABLE = """
        CREATE TABLE ${PeopleEntry.TABLE_NAME} (
            ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${PeopleEntry.COLUMN_FIRST_NAME} TEXT NOT NULL,
            ${PeopleEntry.COLUMN_LAST_NAME} TEXT NOT NULL,
            ${PeopleEntry.COLUMN_REFERENCE_PHOTO_URI} TEXT NOT NULL,
            ${PeopleEntry.COLUMN_FACE_EMBEDDING} BLOB NOT NULL,
            ${PeopleEntry.COLUMN_FACE_BITMAP} BLOB NOT NULL,
            ${PeopleEntry.COLUMN_SIMILARITY_THRESHOLD} REAL NOT NULL DEFAULT 0.6,
            ${PeopleEntry.COLUMN_CREATED_TIMESTAMP} INTEGER NOT NULL,
            ${PeopleEntry.COLUMN_LAST_USED_TIMESTAMP} INTEGER NOT NULL
        )
    """
    
    // SQL statement to create the matches table (updated with person_id)
    const val SQL_CREATE_MATCHES_TABLE = """
        CREATE TABLE ${MatchEntry.TABLE_NAME} (
            ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${MatchEntry.COLUMN_PHOTO_URI} TEXT NOT NULL,
            ${MatchEntry.COLUMN_PERSON_ID} INTEGER,
            ${MatchEntry.COLUMN_PERSON_FIRST_NAME} TEXT NOT NULL,
            ${MatchEntry.COLUMN_PERSON_LAST_NAME} TEXT NOT NULL,
            ${MatchEntry.COLUMN_SIMILARITY_SCORE} REAL NOT NULL,
            ${MatchEntry.COLUMN_MATCH_TYPE} TEXT NOT NULL,
            ${MatchEntry.COLUMN_TIMESTAMP} INTEGER NOT NULL,
            FOREIGN KEY(${MatchEntry.COLUMN_PERSON_ID}) REFERENCES ${PeopleEntry.TABLE_NAME}(${BaseColumns._ID})
        )
    """
    
    // SQL statements to drop tables
    const val SQL_DELETE_MATCHES_TABLE = "DROP TABLE IF EXISTS ${MatchEntry.TABLE_NAME}"
    const val SQL_DELETE_PEOPLE_TABLE = "DROP TABLE IF EXISTS ${PeopleEntry.TABLE_NAME}"
    
    // Index for faster person lookups by name
    const val SQL_CREATE_PEOPLE_NAME_INDEX = """
        CREATE INDEX idx_people_name ON ${PeopleEntry.TABLE_NAME}(
            ${PeopleEntry.COLUMN_FIRST_NAME}, 
            ${PeopleEntry.COLUMN_LAST_NAME}
        )
    """
    
    // Index for faster recent people queries
    const val SQL_CREATE_PEOPLE_USAGE_INDEX = """
        CREATE INDEX idx_people_usage ON ${PeopleEntry.TABLE_NAME}(
            ${PeopleEntry.COLUMN_LAST_USED_TIMESTAMP} DESC
        )
    """
}

/**
 * Enum for match types in three-tier threshold system
 * 
 * EVOLUTION OF MATCH TYPES:
 * 1. Originally: AUTO_MATCH, CONFIRMED, REJECTED (stored all three types)
 * 2. User feedback: "remove these rejected records"
 * 3. Current: Only AUTO_MATCH and CONFIRMED (positive matches only)
 * 4. NEW: Enhanced with persistent people support
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
 * - Better person-based organization with foreign key relationships
 */
enum class MatchType {
    AUTO_MATCH,     // ≥60% similarity - automatic match, saved immediately
    CONFIRMED       // 40-60% similarity - user confirmed via integrated Yes button
    // REJECTED type removed per user request: "remove these rejected records"
    // Rejected matches (≤40% or user No button) are not persisted to database
}
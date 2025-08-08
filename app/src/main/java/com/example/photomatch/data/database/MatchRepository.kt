package com.example.photomatch.data.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing Match database operations using native SQLite
 * 
 * MIGRATION FROM ROOM TO SQLITE:
 * 1. Initial implementation used Room ORM with @Entity, @Dao annotations
 * 2. Encountered persistent kapt/ksp build failures
 * 3. User decision: "for the database, shift to sqlite instead of room db"
 * 4. Migrated to native Android SQLite with custom repository pattern
 * 5. Benefits: Eliminated annotation processing issues, simpler build process
 * 
 * STORAGE POLICY CHANGE:
 * - Originally stored all match types (AUTO_MATCH, CONFIRMED, REJECTED)
 * - User requested: "remove these rejected records"
 * - Now only stores positive matches (AUTO_MATCH and CONFIRMED)
 * - Rejected matches are discarded without database persistence
 * 
 * QUERY PATTERNS:
 * - getConfirmedMatchesForPerson(): Returns only positive matches for summary
 * - insertMatch(): Saves AUTO_MATCH and user CONFIRMED matches
 * - getAllMatches(): Administrative query for all stored matches
 */
class MatchRepository(context: Context) {
    
    private val dbHelper = DatabaseHelper(context)
    
    /**
     * Insert a new match into the database with proper foreign key relationship
     */
    suspend fun insertMatch(match: Match): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        
        val values = ContentValues().apply {
            put(MatchContract.MatchEntry.COLUMN_PHOTO_URI, match.photoUri)
            
            // Primary: Use person_id foreign key if available
            if (match.personId != null) {
                put(MatchContract.MatchEntry.COLUMN_PERSON_ID, match.personId)
            }
            
            // Legacy: Keep name fields for backward compatibility
            put(MatchContract.MatchEntry.COLUMN_PERSON_FIRST_NAME, match.personFirstName)
            put(MatchContract.MatchEntry.COLUMN_PERSON_LAST_NAME, match.personLastName)
            
            put(MatchContract.MatchEntry.COLUMN_SIMILARITY_SCORE, match.similarityScore)
            put(MatchContract.MatchEntry.COLUMN_MATCH_TYPE, match.matchType.name)
            put(MatchContract.MatchEntry.COLUMN_TIMESTAMP, match.timestamp)
        }
        
        db.insert(MatchContract.MatchEntry.TABLE_NAME, null, values)
    }
    
    /**
     * Get all confirmed matches for a specific person
     */
    suspend fun getConfirmedMatchesForPerson(firstName: String, lastName: String): List<Match> = 
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            
            val selection = "${MatchContract.MatchEntry.COLUMN_PERSON_FIRST_NAME} = ? AND " +
                    "${MatchContract.MatchEntry.COLUMN_PERSON_LAST_NAME} = ? AND " +
                    "${MatchContract.MatchEntry.COLUMN_MATCH_TYPE} IN (?, ?)"
            
            val selectionArgs = arrayOf(firstName, lastName, MatchType.AUTO_MATCH.name, MatchType.CONFIRMED.name)
            
            val sortOrder = "${MatchContract.MatchEntry.COLUMN_TIMESTAMP} DESC"
            
            val cursor = db.query(
                MatchContract.MatchEntry.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
            )
            
            cursor.use { 
                parseMatches(it)
            }
        }
    
    /**
     * Get all matches for a specific person (including rejected)
     */
    suspend fun getAllMatchesForPerson(firstName: String, lastName: String): List<Match> = 
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            
            val selection = "${MatchContract.MatchEntry.COLUMN_PERSON_FIRST_NAME} = ? AND " +
                    "${MatchContract.MatchEntry.COLUMN_PERSON_LAST_NAME} = ?"
            
            val selectionArgs = arrayOf(firstName, lastName)
            val sortOrder = "${MatchContract.MatchEntry.COLUMN_TIMESTAMP} DESC"
            
            val cursor = db.query(
                MatchContract.MatchEntry.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
            )
            
            cursor.use { 
                parseMatches(it)
            }
        }
    
    /**
     * Get count of confirmed matches for a person
     */
    suspend fun getConfirmedMatchCount(firstName: String, lastName: String): Int = 
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            
            val selection = "${MatchContract.MatchEntry.COLUMN_PERSON_FIRST_NAME} = ? AND " +
                    "${MatchContract.MatchEntry.COLUMN_PERSON_LAST_NAME} = ? AND " +
                    "${MatchContract.MatchEntry.COLUMN_MATCH_TYPE} IN (?, ?)"
            
            val selectionArgs = arrayOf(firstName, lastName, MatchType.AUTO_MATCH.name, MatchType.CONFIRMED.name)
            
            val cursor = db.query(
                MatchContract.MatchEntry.TABLE_NAME,
                arrayOf("COUNT(*)"),
                selection,
                selectionArgs,
                null,
                null,
                null
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    it.getInt(0)
                } else {
                    0
                }
            }
        }
    
    /**
     * Delete all matches for a specific person
     */
    suspend fun deleteMatchesForPerson(firstName: String, lastName: String): Int = 
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            
            val selection = "${MatchContract.MatchEntry.COLUMN_PERSON_FIRST_NAME} = ? AND " +
                    "${MatchContract.MatchEntry.COLUMN_PERSON_LAST_NAME} = ?"
            
            val selectionArgs = arrayOf(firstName, lastName)
            
            db.delete(MatchContract.MatchEntry.TABLE_NAME, selection, selectionArgs)
        }
    
    /**
     * Delete all matches from the database
     */
    suspend fun deleteAllMatches(): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(MatchContract.MatchEntry.TABLE_NAME, null, null)
    }
    
    /**
     * NEW: Get all confirmed matches for a person by person_id (preferred method)
     */
    suspend fun getConfirmedMatchesForPersonId(personId: Long): List<Match> = 
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            
            val cursor = db.query(
                MatchContract.MatchEntry.TABLE_NAME,
                null, // Get all columns
                "${MatchContract.MatchEntry.COLUMN_PERSON_ID} = ? AND ${MatchContract.MatchEntry.COLUMN_MATCH_TYPE} IN (?,?)",
                arrayOf(personId.toString(), MatchType.AUTO_MATCH.name, MatchType.CONFIRMED.name),
                null, null,
                "${MatchContract.MatchEntry.COLUMN_TIMESTAMP} DESC"
            )
            
            cursor.use { parseMatches(it) }
        }
    
    /**
     * NEW: Get all matches for a person by person_id (preferred method)  
     */
    suspend fun getAllMatchesForPersonId(personId: Long): List<Match> = 
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            
            val cursor = db.query(
                MatchContract.MatchEntry.TABLE_NAME,
                null, // Get all columns
                "${MatchContract.MatchEntry.COLUMN_PERSON_ID} = ?",
                arrayOf(personId.toString()),
                null, null,
                "${MatchContract.MatchEntry.COLUMN_TIMESTAMP} DESC"
            )
            
            cursor.use { parseMatches(it) }
        }
    
    /**
     * NEW: Get confirmed match count for a person by person_id (preferred method)
     */
    suspend fun getConfirmedMatchCountForPersonId(personId: Long): Int =
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            
            val cursor = db.query(
                MatchContract.MatchEntry.TABLE_NAME,
                arrayOf("COUNT(*)"),
                "${MatchContract.MatchEntry.COLUMN_PERSON_ID} = ? AND ${MatchContract.MatchEntry.COLUMN_MATCH_TYPE} IN (?,?)",
                arrayOf(personId.toString(), MatchType.AUTO_MATCH.name, MatchType.CONFIRMED.name),
                null, null, null
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    it.getInt(0)
                } else {
                    0
                }
            }
        }
    
    /**
     * NEW: Delete all matches for a person by person_id (preferred method)
     */
    suspend fun deleteMatchesForPersonId(personId: Long): Int = 
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.delete(
                MatchContract.MatchEntry.TABLE_NAME, 
                "${MatchContract.MatchEntry.COLUMN_PERSON_ID} = ?", 
                arrayOf(personId.toString())
            )
        }

    /**
     * Parse cursor results into Match objects (updated for person_id support)
     */
    private fun parseMatches(cursor: Cursor): List<Match> {
        val matches = mutableListOf<Match>()
        
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(BaseColumns._ID))
                val photoUri = getString(getColumnIndexOrThrow(MatchContract.MatchEntry.COLUMN_PHOTO_URI))
                
                // Handle both new person_id and legacy name fields
                val personIdColumnIndex = getColumnIndex(MatchContract.MatchEntry.COLUMN_PERSON_ID)
                val personId = if (personIdColumnIndex >= 0 && !isNull(personIdColumnIndex)) {
                    getLong(personIdColumnIndex)
                } else null
                
                val firstName = getString(getColumnIndexOrThrow(MatchContract.MatchEntry.COLUMN_PERSON_FIRST_NAME))
                val lastName = getString(getColumnIndexOrThrow(MatchContract.MatchEntry.COLUMN_PERSON_LAST_NAME))
                val similarity = getFloat(getColumnIndexOrThrow(MatchContract.MatchEntry.COLUMN_SIMILARITY_SCORE))
                val matchTypeString = getString(getColumnIndexOrThrow(MatchContract.MatchEntry.COLUMN_MATCH_TYPE))
                val timestamp = getLong(getColumnIndexOrThrow(MatchContract.MatchEntry.COLUMN_TIMESTAMP))
                
                // Parse match type with fallback for data integrity
                val matchType = try {
                    MatchType.valueOf(matchTypeString)
                } catch (e: IllegalArgumentException) {
                    // Fallback to AUTO_MATCH if invalid type found
                    // (Previously defaulted to REJECTED but that type no longer exists per user request)
                    MatchType.AUTO_MATCH
                }
                
                matches.add(
                    Match(
                        id = id,
                        photoUri = photoUri,
                        personId = personId,
                        personFirstName = firstName,
                        personLastName = lastName,
                        similarityScore = similarity,
                        matchType = matchType,
                        timestamp = timestamp
                    )
                )
            }
        }
        
        return matches
    }
    
    /**
     * Close the database helper
     */
    fun close() {
        dbHelper.close()
    }
}
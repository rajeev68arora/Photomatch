package com.example.photomatch.data.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.provider.BaseColumns
import com.example.photomatch.util.SerializationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing persistent people storage with face embeddings and bitmaps
 * 
 * PURPOSE:
 * - Enable reusable face recognition across multiple search sessions
 * - Store face embeddings for fast similarity comparisons
 * - Provide duplicate detection through embedding analysis
 * - Manage person lifecycle (create, read, update, delete)
 * - Track usage patterns for smart suggestions
 * 
 * FEATURES:
 * - Asynchronous operations using Kotlin coroutines
 * - Automatic serialization/deserialization of embeddings and bitmaps
 * - Duplicate detection based on face similarity
 * - Usage tracking for recently used person sorting
 * - Efficient queries with proper indexing
 * 
 * PERFORMANCE CONSIDERATIONS:
 * - All database operations on background threads
 * - Lazy loading of bitmaps to reduce memory usage
 * - Indexed queries for fast name and usage-based lookups
 * - Compressed bitmap storage for space efficiency
 */
class PeopleRepository(context: Context) {
    
    private val dbHelper = DatabaseHelper(context)
    
    companion object {
        private const val DUPLICATE_SIMILARITY_THRESHOLD = 0.85f // High threshold for duplicate detection
    }
    
    /**
     * Insert a new person into the database
     * 
     * @param person Person object with face data
     * @return Database row ID of the inserted person, or -1 if failed
     */
    suspend fun insertPerson(person: Person): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        
        val values = ContentValues().apply {
            put(MatchContract.PeopleEntry.COLUMN_FIRST_NAME, person.firstName)
            put(MatchContract.PeopleEntry.COLUMN_LAST_NAME, person.lastName)
            put(MatchContract.PeopleEntry.COLUMN_REFERENCE_PHOTO_URI, person.referencePhotoUri)
            put(MatchContract.PeopleEntry.COLUMN_FACE_EMBEDDING, SerializationUtils.embeddingToByteArray(person.faceEmbedding))
            put(MatchContract.PeopleEntry.COLUMN_FACE_BITMAP, SerializationUtils.bitmapToByteArray(person.faceBitmap))
            put(MatchContract.PeopleEntry.COLUMN_SIMILARITY_THRESHOLD, person.similarityThreshold)
            put(MatchContract.PeopleEntry.COLUMN_CREATED_TIMESTAMP, person.createdTimestamp)
            put(MatchContract.PeopleEntry.COLUMN_LAST_USED_TIMESTAMP, person.lastUsedTimestamp)
        }
        
        try {
            db.insert(MatchContract.PeopleEntry.TABLE_NAME, null, values)
        } catch (e: Exception) {
            -1L // Return -1 to indicate failure
        }
    }
    
    /**
     * Get all people from the database, ordered by last used (most recent first)
     * 
     * @return List of Person objects sorted by usage recency
     */
    suspend fun getAllPeople(): List<Person> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            MatchContract.PeopleEntry.TABLE_NAME,
            null, // Select all columns
            null, // No WHERE clause
            null, // No WHERE args
            null, // No GROUP BY
            null, // No HAVING
            "${MatchContract.PeopleEntry.COLUMN_LAST_USED_TIMESTAMP} DESC" // Order by most recent
        )
        
        return@withContext cursor.use { parsePeople(it) }
    }
    
    /**
     * Get people matching a name search query
     * 
     * @param query Search query for first or last name (case-insensitive)
     * @return List of matching Person objects
     */
    suspend fun searchPeopleByName(query: String): List<Person> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val searchQuery = "%$query%"
        
        val cursor = db.query(
            MatchContract.PeopleEntry.TABLE_NAME,
            null,
            "${MatchContract.PeopleEntry.COLUMN_FIRST_NAME} LIKE ? OR ${MatchContract.PeopleEntry.COLUMN_LAST_NAME} LIKE ?",
            arrayOf(searchQuery, searchQuery),
            null,
            null,
            "${MatchContract.PeopleEntry.COLUMN_LAST_USED_TIMESTAMP} DESC"
        )
        
        return@withContext cursor.use { parsePeople(it) }
    }
    
    /**
     * Find a person by their database ID
     * 
     * @param personId Database ID of the person
     * @return Person object if found, null otherwise
     */
    suspend fun getPersonById(personId: Long): Person? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            MatchContract.PeopleEntry.TABLE_NAME,
            null,
            "${BaseColumns._ID} = ?",
            arrayOf(personId.toString()),
            null,
            null,
            null
        )
        
        return@withContext cursor.use { 
            if (it.moveToFirst()) parsePerson(it) else null 
        }
    }
    
    /**
     * Find similar people based on face embedding similarity
     * Used for duplicate detection before adding new people
     * 
     * @param faceEmbedding Face embedding to compare against existing people
     * @param similarityThreshold Minimum similarity to consider a match (default: 0.85)
     * @return List of similar Person objects with high similarity scores
     */
    suspend fun findSimilarPeople(
        faceEmbedding: FloatArray, 
        similarityThreshold: Float = DUPLICATE_SIMILARITY_THRESHOLD
    ): List<Pair<Person, Float>> = withContext(Dispatchers.IO) {
        val allPeople = getAllPeople()
        val similarPeople = mutableListOf<Pair<Person, Float>>()
        
        for (person in allPeople) {
            val similarity = calculateCosineSimilarity(faceEmbedding, person.faceEmbedding)
            if (similarity >= similarityThreshold) {
                similarPeople.add(person to similarity)
            }
        }
        
        // Sort by similarity (highest first)
        return@withContext similarPeople.sortedByDescending { it.second }
    }
    
    /**
     * Update the last used timestamp for a person
     * Used to track usage patterns for smart suggestions
     * 
     * @param personId Database ID of the person
     * @return Number of rows updated (should be 1 if successful)
     */
    suspend fun updateLastUsed(personId: Long): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        
        val values = ContentValues().apply {
            put(MatchContract.PeopleEntry.COLUMN_LAST_USED_TIMESTAMP, System.currentTimeMillis())
        }
        
        db.update(
            MatchContract.PeopleEntry.TABLE_NAME,
            values,
            "${BaseColumns._ID} = ?",
            arrayOf(personId.toString())
        )
    }
    
    /**
     * Delete a person from the database
     * Note: This will also affect related matches due to foreign key constraints
     * 
     * @param personId Database ID of the person to delete
     * @return Number of rows deleted (should be 1 if successful)
     */
    suspend fun deletePerson(personId: Long): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        
        db.delete(
            MatchContract.PeopleEntry.TABLE_NAME,
            "${BaseColumns._ID} = ?",
            arrayOf(personId.toString())
        )
    }
    
    /**
     * Get the most recently used people (for quick access suggestions)
     * 
     * @param limit Maximum number of recent people to return
     * @return List of recently used Person objects
     */
    suspend fun getRecentPeople(limit: Int = 5): List<Person> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            MatchContract.PeopleEntry.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "${MatchContract.PeopleEntry.COLUMN_LAST_USED_TIMESTAMP} DESC",
            limit.toString()
        )
        
        return@withContext cursor.use { parsePeople(it) }
    }
    
    /**
     * Parse multiple people from a database cursor
     */
    private fun parsePeople(cursor: Cursor): List<Person> {
        val people = mutableListOf<Person>()
        
        with(cursor) {
            while (moveToNext()) {
                val person = parsePerson(this)
                if (person != null) {
                    people.add(person)
                }
            }
        }
        
        return people
    }
    
    /**
     * Parse a single person from a database cursor
     */
    private fun parsePerson(cursor: Cursor): Person? {
        return try {
            with(cursor) {
                val id = getLong(getColumnIndexOrThrow(BaseColumns._ID))
                val firstName = getString(getColumnIndexOrThrow(MatchContract.PeopleEntry.COLUMN_FIRST_NAME))
                val lastName = getString(getColumnIndexOrThrow(MatchContract.PeopleEntry.COLUMN_LAST_NAME))
                val referencePhotoUri = getString(getColumnIndexOrThrow(MatchContract.PeopleEntry.COLUMN_REFERENCE_PHOTO_URI))
                val embeddingBytes = getBlob(getColumnIndexOrThrow(MatchContract.PeopleEntry.COLUMN_FACE_EMBEDDING))
                val bitmapBytes = getBlob(getColumnIndexOrThrow(MatchContract.PeopleEntry.COLUMN_FACE_BITMAP))
                val similarityThreshold = getFloat(getColumnIndexOrThrow(MatchContract.PeopleEntry.COLUMN_SIMILARITY_THRESHOLD))
                val createdTimestamp = getLong(getColumnIndexOrThrow(MatchContract.PeopleEntry.COLUMN_CREATED_TIMESTAMP))
                val lastUsedTimestamp = getLong(getColumnIndexOrThrow(MatchContract.PeopleEntry.COLUMN_LAST_USED_TIMESTAMP))
                
                // Deserialize embedding and bitmap
                val faceEmbedding = SerializationUtils.byteArrayToEmbedding(embeddingBytes)
                val faceBitmap = SerializationUtils.byteArrayToBitmap(bitmapBytes)
                
                if (faceBitmap != null) {
                    Person(
                        id = id,
                        firstName = firstName,
                        lastName = lastName,
                        referencePhotoUri = referencePhotoUri,
                        faceEmbedding = faceEmbedding,
                        faceBitmap = faceBitmap,
                        similarityThreshold = similarityThreshold,
                        createdTimestamp = createdTimestamp,
                        lastUsedTimestamp = lastUsedTimestamp
                    )
                } else {
                    null // Skip if bitmap decoding failed
                }
            }
        } catch (e: Exception) {
            null // Skip if parsing failed
        }
    }
    
    /**
     * Calculate cosine similarity between two face embeddings
     * Used for duplicate detection and similarity comparison
     * 
     * @param embedding1 First face embedding
     * @param embedding2 Second face embedding
     * @return Similarity score between 0.0 and 1.0 (higher = more similar)
     */
    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            return 0.0f
        }
        
        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        
        return if (denominator > 0.0f) {
            dotProduct / denominator
        } else {
            0.0f
        }
    }
    
    /**
     * Close the database helper
     */
    fun close() {
        dbHelper.close()
    }
}
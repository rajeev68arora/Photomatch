package com.example.photomatch.data.database

import android.graphics.Bitmap

/**
 * Data class representing a person in the persistent people database
 * 
 * PURPOSE:
 * - Enable reusable face recognition across multiple search sessions
 * - Store face embeddings for fast similarity comparisons
 * - Maintain face thumbnails for user-friendly person selection
 * - Track usage patterns for smart suggestions
 * 
 * STORAGE STRATEGY:
 * - Face embeddings stored as BLOB (512 bytes for 128 floats)
 * - Face bitmaps stored as compressed BLOB for thumbnails
 * - Person names for identification and organization
 * - Timestamps for usage tracking and sorting
 * 
 * DUPLICATE PREVENTION:
 * - Face embeddings used for similarity comparison before insertion
 * - High similarity threshold (0.85+) triggers duplicate confirmation
 * - Users can choose to use existing person or add as new
 */
data class Person(
    val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val referencePhotoUri: String,
    val faceEmbedding: FloatArray,
    val faceBitmap: Bitmap,
    val similarityThreshold: Float = 0.6f,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastUsedTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Full display name for UI presentation
     */
    val fullName: String
        get() = "$firstName $lastName"
    
    /**
     * Custom equality check excluding bitmap and timestamp fields for performance
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as Person
        
        if (id != other.id) return false
        if (firstName != other.firstName) return false
        if (lastName != other.lastName) return false
        if (referencePhotoUri != other.referencePhotoUri) return false
        if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false
        if (similarityThreshold != other.similarityThreshold) return false
        
        return true
    }
    
    /**
     * Custom hash code excluding bitmap for performance
     */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + referencePhotoUri.hashCode()
        result = 31 * result + faceEmbedding.contentHashCode()
        result = 31 * result + similarityThreshold.hashCode()
        return result
    }
    
    /**
     * Create a copy with updated last used timestamp
     * Useful for tracking person usage patterns
     */
    fun withUpdatedLastUsed(): Person = copy(
        lastUsedTimestamp = System.currentTimeMillis()
    )
}

package com.example.photomatch.data.database

/**
 * Simple data class for storing face match results
 */
data class Match(
    val id: Long = 0,
    val photoUri: String,
    val personFirstName: String,
    val personLastName: String,
    val similarityScore: Float,
    val matchType: MatchType,
    val timestamp: Long = System.currentTimeMillis()
)
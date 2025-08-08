package com.example.photomatch.data.database

/**
 * Data class for storing face match results with proper foreign key relationships
 * 
 * DATABASE RELATIONSHIP:
 * - Uses person_id as foreign key to people table
 * - Legacy name fields kept for backward compatibility during migration
 * - All new matches should use person_id, names are fallback only
 */
data class Match(
    val id: Long = 0,
    val photoUri: String,
    val personId: Long? = null,                    // Primary: Foreign key to people table
    val personFirstName: String? = null,           // Legacy: For backward compatibility  
    val personLastName: String? = null,            // Legacy: For backward compatibility
    val similarityScore: Float,
    val matchType: MatchType,
    val timestamp: Long = System.currentTimeMillis()
) {
    init {
        // Ensure at least one identification method is provided
        require(personId != null || (!personFirstName.isNullOrBlank() && !personLastName.isNullOrBlank())) {
            "Match must have either personId or both personFirstName and personLastName"
        }
    }
}
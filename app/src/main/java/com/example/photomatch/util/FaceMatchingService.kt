package com.example.photomatch.util

import android.content.Context
import android.graphics.Bitmap
import com.example.photomatch.data.database.Person
import com.example.photomatch.data.database.PeopleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for face matching, similarity comparison, and duplicate detection
 * 
 * PURPOSE:
 * - Coordinate face recognition operations between UI and repository layers
 * - Provide intelligent duplicate detection before adding new people
 * - Calculate face similarities using optimized algorithms
 * - Manage face embedding generation and comparison workflows
 * - Handle edge cases in face recognition pipeline
 * 
 * FEATURES:
 * - Asynchronous similarity comparisons using coroutines
 * - Smart duplicate detection with configurable thresholds
 * - Integration with FaceNetHelper for embedding generation
 * - Batch similarity operations for efficient processing
 * - Error handling and graceful fallbacks
 * 
 * DUPLICATE DETECTION STRATEGY:
 * - Extract face embedding from new photo
 * - Compare against all existing people in database
 * - Use high similarity threshold (0.85+) to identify potential duplicates
 * - Return candidates sorted by similarity score for user confirmation
 */
class FaceMatchingService(
    private val context: Context,
    private val peopleRepository: PeopleRepository
) {
    
    companion object {
        // Threshold for considering faces as potential duplicates
        private const val DUPLICATE_DETECTION_THRESHOLD = 0.85f
        
        // Threshold for high confidence duplicate (automatic suggestion)
        private const val HIGH_CONFIDENCE_DUPLICATE_THRESHOLD = 0.95f
        
        // Minimum embedding quality score to proceed with comparison
        private const val MIN_EMBEDDING_QUALITY = 0.1f
    }
    
    /**
     * Data class representing a duplicate detection result
     */
    data class DuplicateCandidate(
        val person: Person,
        val similarity: Float,
        val confidence: DuplicateConfidence
    )
    
    /**
     * Confidence levels for duplicate detection
     */
    enum class DuplicateConfidence {
        HIGH,      // >95% similarity - very likely the same person
        MEDIUM,    // 85-95% similarity - possibly the same person
        LOW        // <85% similarity - different person (not returned)
    }
    
    /**
     * Result of duplicate detection analysis
     */
    data class DuplicateDetectionResult(
        val hasDuplicates: Boolean,
        val candidates: List<DuplicateCandidate>,
        val recommendedAction: RecommendedAction
    )
    
    /**
     * Recommended actions based on duplicate detection results
     */
    enum class RecommendedAction {
        PROCEED_ADD_NEW,           // No duplicates found, safe to add
        CONFIRM_DUPLICATES,        // Potential duplicates found, ask user
        SUGGEST_EXISTING          // High confidence duplicate, suggest using existing
    }
    
    /**
     * Analyze a new face photo for potential duplicates in the database
     * 
     * WORKFLOW:
     * 1. Extract face embedding from the provided photo/bitmap
     * 2. Compare against all existing people in database
     * 3. Identify candidates above similarity thresholds
     * 4. Classify confidence levels and provide recommendations
     * 
     * @param photoBitmap Bitmap containing the face to analyze
     * @return DuplicateDetectionResult with analysis and recommendations
     */
    suspend fun analyzePotentialDuplicates(photoBitmap: Bitmap): DuplicateDetectionResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Extract face embedding from the new photo
            val faceEmbedding = FaceNetHelper.getFaceEmbeddings(photoBitmap, context)
            
            if (faceEmbedding == null || !isValidEmbedding(faceEmbedding)) {
                // If we can't extract a valid embedding, assume no duplicates
                return@withContext DuplicateDetectionResult(
                    hasDuplicates = false,
                    candidates = emptyList(),
                    recommendedAction = RecommendedAction.PROCEED_ADD_NEW
                )
            }
            
            // Step 2: Find similar people in database
            val similarPeople = peopleRepository.findSimilarPeople(
                faceEmbedding = faceEmbedding,
                similarityThreshold = DUPLICATE_DETECTION_THRESHOLD
            )
            
            // Step 3: Classify candidates by confidence level
            val candidates = similarPeople.map { (person, similarity) ->
                val confidence = when {
                    similarity >= HIGH_CONFIDENCE_DUPLICATE_THRESHOLD -> DuplicateConfidence.HIGH
                    similarity >= DUPLICATE_DETECTION_THRESHOLD -> DuplicateConfidence.MEDIUM
                    else -> DuplicateConfidence.LOW // Should not happen due to threshold filter
                }
                
                DuplicateCandidate(person, similarity, confidence)
            }
            
            // Step 4: Determine recommended action
            val recommendedAction = when {
                candidates.isEmpty() -> RecommendedAction.PROCEED_ADD_NEW
                candidates.any { it.confidence == DuplicateConfidence.HIGH } -> RecommendedAction.SUGGEST_EXISTING
                else -> RecommendedAction.CONFIRM_DUPLICATES
            }
            
            return@withContext DuplicateDetectionResult(
                hasDuplicates = candidates.isNotEmpty(),
                candidates = candidates,
                recommendedAction = recommendedAction
            )
            
        } catch (e: Exception) {
            // On error, assume no duplicates to avoid blocking user workflow
            return@withContext DuplicateDetectionResult(
                hasDuplicates = false,
                candidates = emptyList(),
                recommendedAction = RecommendedAction.PROCEED_ADD_NEW
            )
        }
    }
    
    /**
     * Calculate similarity between two face embeddings
     * 
     * @param embedding1 First face embedding
     * @param embedding2 Second face embedding
     * @return Similarity score between 0.0 and 1.0
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return calculateCosineSimilarity(embedding1, embedding2)
    }
    
    /**
     * Batch calculate similarities between one embedding and multiple people
     * Useful for finding the best matches from a set of candidates
     * 
     * @param targetEmbedding Face embedding to compare against
     * @param people List of people to compare with
     * @return List of pairs (Person, similarity) sorted by similarity (highest first)
     */
    suspend fun calculateBatchSimilarities(
        targetEmbedding: FloatArray,
        people: List<Person>
    ): List<Pair<Person, Float>> = withContext(Dispatchers.IO) {
        
        val similarities = people.map { person ->
            val similarity = calculateCosineSimilarity(targetEmbedding, person.faceEmbedding)
            person to similarity
        }
        
        // Sort by similarity (highest first)
        return@withContext similarities.sortedByDescending { it.second }
    }
    
    /**
     * Find the best matching person for a given face embedding
     * 
     * @param faceEmbedding Face embedding to find matches for
     * @param minimumSimilarity Minimum similarity threshold (default: 0.6)
     * @return Best matching Person and similarity, or null if no good matches
     */
    suspend fun findBestMatch(
        faceEmbedding: FloatArray,
        minimumSimilarity: Float = 0.6f
    ): Pair<Person, Float>? = withContext(Dispatchers.IO) {
        
        val allPeople = peopleRepository.getAllPeople()
        if (allPeople.isEmpty()) return@withContext null
        
        val similarities = calculateBatchSimilarities(faceEmbedding, allPeople)
        val bestMatch = similarities.firstOrNull()
        
        return@withContext if (bestMatch != null && bestMatch.second >= minimumSimilarity) {
            bestMatch
        } else {
            null
        }
    }
    
    /**
     * Validate that a face embedding is usable for comparisons
     * 
     * @param embedding Face embedding to validate
     * @return true if embedding appears valid, false otherwise
     */
    private fun isValidEmbedding(embedding: FloatArray): Boolean {
        // Check for reasonable embedding size (FaceNet uses 128 dimensions)
        if (embedding.size != 128) return false
        
        // Check for non-zero values (zero embedding indicates extraction failure)
        val magnitude = embedding.map { it * it }.sum()
        if (magnitude < MIN_EMBEDDING_QUALITY) return false
        
        // Check for reasonable value ranges (embeddings should be normalized)
        val maxValue = embedding.maxOrNull() ?: 0f
        val minValue = embedding.minOrNull() ?: 0f
        if (maxValue > 10f || minValue < -10f) return false
        
        return true
    }
    
    /**
     * Calculate cosine similarity between two face embeddings
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
}
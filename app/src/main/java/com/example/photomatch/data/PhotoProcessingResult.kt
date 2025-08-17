package com.example.photomatch.data

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri

/**
 * Data class representing the result of processing a single photo for face matching
 */
data class PhotoProcessingResult(
    val photoUri: Uri,
    val detectedFaces: List<FaceDetectionResult>,
    val similarityScore: Float,
    val processingStatus: ProcessingStatus,
    val isMatch: Boolean = false
)

/**
 * Data class representing a single detected face within a photo
 */
data class FaceDetectionResult(
    val boundingBox: Rect,
    val embedding: FloatArray,
    val croppedFaceBitmap: Bitmap,
    val confidence: Float = 0f,
    val similarityToReference: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceDetectionResult

        if (boundingBox != other.boundingBox) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (confidence != other.confidence) return false

        return true
    }

    override fun hashCode(): Int {
        var result = boundingBox.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + confidence.hashCode()
        return result
    }
}

/**
 * Enum representing the current processing status of a photo
 */
enum class ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    NO_FACE_DETECTED,
    ERROR
}

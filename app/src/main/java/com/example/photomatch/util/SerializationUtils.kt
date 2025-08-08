package com.example.photomatch.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility functions for serializing and deserializing face embeddings and bitmaps
 * 
 * PURPOSE:
 * - Convert FloatArray embeddings to/from BLOB format for SQLite storage
 * - Convert Bitmap face thumbnails to/from compressed BLOB format
 * - Maintain data integrity during database operations
 * - Optimize storage space through compression
 * 
 * TECHNICAL DETAILS:
 * - Face embeddings: 128 floats * 4 bytes = 512 bytes per embedding
 * - Face bitmaps: JPEG compression for space efficiency
 * - Byte order: Little endian for consistency across platforms
 */
object SerializationUtils {
    
    /**
     * Convert FloatArray face embedding to ByteArray for database storage
     * 
     * @param embedding Face embedding as FloatArray (typically 128 dimensions)
     * @return ByteArray suitable for BLOB storage in SQLite
     */
    fun embeddingToByteArray(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4) // 4 bytes per float
        buffer.order(ByteOrder.LITTLE_ENDIAN) // Consistent byte order
        
        for (value in embedding) {
            buffer.putFloat(value)
        }
        
        return buffer.array()
    }
    
    /**
     * Convert ByteArray back to FloatArray face embedding
     * 
     * @param bytes ByteArray from database BLOB
     * @return FloatArray face embedding ready for similarity calculations
     */
    fun byteArrayToEmbedding(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN) // Match serialization order
        
        val embedding = FloatArray(bytes.size / 4) // 4 bytes per float
        
        for (i in embedding.indices) {
            embedding[i] = buffer.getFloat()
        }
        
        return embedding
    }
    
    /**
     * Convert Bitmap to compressed ByteArray for database storage
     * 
     * COMPRESSION STRATEGY:
     * - JPEG compression for face thumbnails (good quality/size ratio)
     * - 80% quality balances file size with visual quality
     * - Face thumbnails are typically small (80x80 to 120x120 pixels)
     * 
     * @param bitmap Face bitmap to compress and store
     * @return ByteArray suitable for BLOB storage
     */
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        
        // Compress as JPEG with 80% quality for good balance of size/quality
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        
        return outputStream.toByteArray()
    }
    
    /**
     * Convert ByteArray back to Bitmap face thumbnail
     * 
     * @param bytes ByteArray from database BLOB
     * @return Bitmap ready for UI display, or null if decoding fails
     */
    fun byteArrayToBitmap(bytes: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            // Return null if bitmap decoding fails (corrupted data, etc.)
            null
        }
    }
    
    /**
     * Create a thumbnail version of a bitmap for efficient storage and display
     * 
     * THUMBNAIL STRATEGY:
     * - Scale to maximum 120x120 pixels for UI display
     * - Maintains aspect ratio while reducing storage size
     * - Bilinear filtering for smooth scaling
     * 
     * @param original Original bitmap to create thumbnail from
     * @param maxSize Maximum width/height in pixels (default 120)
     * @return Scaled thumbnail bitmap
     */
    fun createThumbnail(original: Bitmap, maxSize: Int = 120): Bitmap {
        val width = original.width
        val height = original.height
        
        // Calculate scale factor to fit within maxSize while maintaining aspect ratio
        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
    }
    
    /**
     * Estimate storage size for a person record
     * Useful for monitoring database growth and optimization
     * 
     * @param embedding Face embedding FloatArray
     * @param bitmap Face bitmap
     * @return Estimated storage size in bytes
     */
    fun estimatePersonStorageSize(embedding: FloatArray, bitmap: Bitmap): Long {
        val embeddingSize = embedding.size * 4 // 4 bytes per float
        val bitmapSize = bitmapToByteArray(bitmap).size
        val metadataSize = 200 // Rough estimate for strings, timestamps, etc.
        
        return (embeddingSize + bitmapSize + metadataSize).toLong()
    }
}
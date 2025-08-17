package com.example.photomatch.util
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.example.photomatch.data.FaceDetectionResult
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

// FaceNetHelper: Enhanced for full resolution processing with step-by-step face detection
object FaceNetHelper {
    private const val MODEL_FILE = "facenet.tflite"
    private const val IMAGE_SIZE = 160
    private const val EMBEDDING_SIZE = 128
    private const val MAX_IMAGE_DIMENSION = 1024  // Maximum dimension for processing

    private var interpreter: Interpreter? = null

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)  // Changed to FAST mode
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
    // Rajeev - we can set landmark mode to all to get face landmarks and use them for aligning the face
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    private fun initializeInterpreter(context: Context) {
        if (interpreter == null) {
            val options = Interpreter.Options().apply {
                numThreads = 4
                useXNNPACK = true  // Enable hardware acceleration if available
            }
            interpreter = Interpreter(loadModelFile(context), options)
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val modelPath = MODEL_FILE
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    suspend fun getFaceEmbeddings(originalBitmap: Bitmap, context: Context): FloatArray {
        try {
            // Use full resolution image for better face detection
            Log.d("FaceNetHelper", "Processing full resolution image: ${originalBitmap.width}x${originalBitmap.height}")

            // Initialize interpreter
            initializeInterpreter(context)

            // Detect face on full resolution image
            val face = detectFace(originalBitmap) ?: throw IllegalStateException("No face detected")
            Log.d("FaceNetHelper", "Face detected with bounds: ${face.boundingBox}")

            // Process face
            val faceBitmap = cropFace(originalBitmap, face.boundingBox)
            val byteBuffer = bitmapToByteBuffer(faceBitmap) // Input for model

            // Generate embedding
            val outputArray = Array(1) { FloatArray(EMBEDDING_SIZE) }
            interpreter?.run(byteBuffer, outputArray)

            // Normalize embedding
            val embedding = outputArray[0]
            val norm = sqrt(embedding.map { it * it }.sum())
            for (i in embedding.indices) {
                embedding[i] /= norm
            }

            return embedding

        } catch (e: Exception) {
            Log.e("FaceNetHelper", "Error during inference: ${e.message}")
            throw e
        }
    }

    /**
     * Enhanced method that returns detailed face detection results with embeddings
     */
    suspend fun getFaceDetectionResults(originalBitmap: Bitmap, context: Context): List<FaceDetectionResult> {
        try {
            Log.d("FaceNetHelper", "Processing full resolution image: ${originalBitmap.width}x${originalBitmap.height}")
            
            // Initialize interpreter
            initializeInterpreter(context)
            
            // Detect all faces in the image
            val faces = detectAllFaces(originalBitmap)
            
            if (faces.isEmpty()) {
                Log.w("FaceNetHelper", "No faces detected in image")
                return emptyList()
            }
            
            val results = mutableListOf<FaceDetectionResult>()
            
            for (face in faces) {
                try {
                    // Process each face
                    val faceBitmap = cropFace(originalBitmap, face.boundingBox)
                    val byteBuffer = bitmapToByteBuffer(faceBitmap)
                    
                    // Generate embedding
                    val outputArray = Array(1) { FloatArray(EMBEDDING_SIZE) }
                    interpreter?.run(byteBuffer, outputArray)
                    
                    // Normalize embedding
                    val embedding = outputArray[0]
                    val norm = sqrt(embedding.map { it * it }.sum())
                    for (i in embedding.indices) {
                        embedding[i] /= norm
                    }
                    
                    results.add(
                        FaceDetectionResult(
                            boundingBox = face.boundingBox,
                            embedding = embedding,
                            croppedFaceBitmap = faceBitmap,
                            confidence = 1.0f // ML Kit doesn't provide confidence score directly
                        )
                    )
                    
                    Log.d("FaceNetHelper", "Processed face with bounds: ${face.boundingBox}")
                    
                } catch (e: Exception) {
                    Log.e("FaceNetHelper", "Error processing individual face: ${e.message}")
                    continue
                }
            }
            
            return results
            
        } catch (e: Exception) {
            Log.e("FaceNetHelper", "Error during face detection: ${e.message}")
            throw e
        }
    }
// Rajeev - why is the image being scaled before detecting faces
    // what is the maximum size of image that can be processed
    private fun scaleDownBitmap(bitmap: Bitmap): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // If image is already small enough, return original
        if (originalWidth <= MAX_IMAGE_DIMENSION && originalHeight <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }

        // Calculate new dimensions
        val ratio = min(
            MAX_IMAGE_DIMENSION.toFloat() / originalWidth,
            MAX_IMAGE_DIMENSION.toFloat() / originalHeight
        )

        val newWidth = (originalWidth * ratio).toInt()
        val newHeight = (originalHeight * ratio).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        
        // Recycle original bitmap if it was scaled down to prevent memory leaks
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }
        
        return scaledBitmap
    }

    private suspend fun detectFace(bitmap: Bitmap) = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    continuation.resume(null)
                } else {
                    val largestFace = faces.maxByOrNull {
                        it.boundingBox.width() * it.boundingBox.height()
                        // Rajeev - what to do if we want to take more faces into consideration?
                    }
                    continuation.resume(largestFace)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceNetHelper", "Face detection failed: ${e.message}")
                continuation.resume(null)
            }
    }

    /**
     * Detects all faces in the given bitmap
     */
    private suspend fun detectAllFaces(bitmap: Bitmap): List<Face> = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                continuation.resume(faces)
            }
            .addOnFailureListener { e ->
                Log.e("FaceNetHelper", "Face detection failed: ${e.message}")
                continuation.resume(emptyList())
            }
    }

    private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        val padding = (max(boundingBox.width(), boundingBox.height()) * 0.2f).toInt()

        val left = max(0, boundingBox.left - padding)
        val top = max(0, boundingBox.top - padding)
        val right = min(bitmap.width, boundingBox.right + padding)
        val bottom = min(bitmap.height, boundingBox.bottom + padding)

        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )

        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        
        // Recycle the intermediate cropped bitmap to prevent memory leaks
        if (scaledBitmap != croppedBitmap) {
            croppedBitmap.recycle()
        }
        
        return scaledBitmap
    }

    private fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val inputArray = Array(1) { Array(IMAGE_SIZE) { Array(IMAGE_SIZE) { FloatArray(3) } } }

        for (x in 0 until IMAGE_SIZE) {
            for (y in 0 until IMAGE_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                inputArray[0][x][y][0] = (Color.red(pixel) - 127.5f) / 127.5f
                inputArray[0][x][y][1] = (Color.green(pixel) - 127.5f) / 127.5f
                inputArray[0][x][y][2] = (Color.blue(pixel) - 127.5f) / 127.5f
            }
        }

        return inputArray
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixelIndex = 0
        for (i in 0 until IMAGE_SIZE) {
            for (j in 0 until IMAGE_SIZE) {
                val pixelValue = intValues[pixelIndex++]
                byteBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((pixelValue and 0xFF) / 255.0f)
            }
        }

        return byteBuffer
    }

    private fun sqrt(value: Float): Float = kotlin.math.sqrt(value)

    /**
     * Calculate cosine similarity between two embeddings
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        return if (norm1 == 0f || norm2 == 0f) {
            0f
        } else {
            dotProduct / (sqrt(norm1) * sqrt(norm2))
        }
    }
}
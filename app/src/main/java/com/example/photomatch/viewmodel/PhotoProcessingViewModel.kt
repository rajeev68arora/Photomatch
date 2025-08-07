package com.example.photomatch.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photomatch.data.FaceDetectionResult
import com.example.photomatch.data.PhotoProcessingResult
import com.example.photomatch.data.ProcessingStatus
import com.example.photomatch.util.FaceNetHelper
import com.example.photomatch.util.GalleryHelper
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * ViewModel for individual photo processing workflow
 */
class PhotoProcessingViewModel : ViewModel() {

    companion object {
        private const val TAG = "PhotoProcessingVM"
        private const val MATCH_THRESHOLD = 0.55f
    }

    // Reference face embedding from the selected photo
    private var referenceFaceEmbedding: FloatArray? = null
    
    // List of all photos to process
    private val allPhotos = mutableListOf<Uri>()
    
    // List of processing results for each photo
    private val processingResults = mutableListOf<PhotoProcessingResult>()
    
    // Current photo index
    private var currentPhotoIndex = 0

    // LiveData for UI observations
    private val _currentPhoto = MutableLiveData<Uri>()
    val currentPhoto: LiveData<Uri> = _currentPhoto

    private val _currentPhotoResult = MutableLiveData<PhotoProcessingResult>()
    val currentPhotoResult: LiveData<PhotoProcessingResult> = _currentPhotoResult

    private val _navigationState = MutableLiveData<NavigationState>()
    val navigationState: LiveData<NavigationState> = _navigationState

    private val _processingProgress = MutableLiveData<ProcessingProgress>()
    val processingProgress: LiveData<ProcessingProgress> = _processingProgress

    private val _allMatches = MutableLiveData<List<Uri>>()
    val allMatches: LiveData<List<Uri>> = _allMatches

    /**
     * Initialize the processing workflow with a reference photo
     */
    fun initializeWithReference(referencePhotoUri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Initializing with reference photo")
                
                // Load reference photo bitmap
                val bitmap = loadBitmapFromUri(referencePhotoUri, context)
                
                // Extract face embedding from reference photo
                referenceFaceEmbedding = FaceNetHelper.getFaceEmbeddings(bitmap, context)
                
                // Load all gallery photos
                val galleryPhotos = GalleryHelper.getGalleryImages(context)
                allPhotos.clear()
                allPhotos.addAll(galleryPhotos)
                
                // Initialize processing results list
                processingResults.clear()
                repeat(allPhotos.size) {
                    processingResults.add(
                        PhotoProcessingResult(
                            photoUri = allPhotos[it],
                            detectedFaces = emptyList(),
                            similarityScore = 0f,
                            processingStatus = ProcessingStatus.PENDING
                        )
                    )
                }
                
                // Start with first photo
                currentPhotoIndex = 0
                updateCurrentPhoto()
                
                Log.d(TAG, "Initialized with ${allPhotos.size} photos to process")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing processing: ${e.message}")
            }
        }
    }

    /**
     * Process the current photo for face detection and matching
     */
    fun processCurrentPhoto(context: Context) {
        if (currentPhotoIndex >= allPhotos.size) return
        
        viewModelScope.launch {
            try {
                val currentUri = allPhotos[currentPhotoIndex]
                Log.d(TAG, "Processing photo $currentPhotoIndex: $currentUri")
                
                // Update status to processing
                updateProcessingResult(ProcessingStatus.PROCESSING)
                
                // Load bitmap
                val bitmap = loadBitmapFromUri(currentUri, context)
                
                // Detect faces and generate embeddings
                val detectedFaces = FaceNetHelper.getFaceDetectionResults(bitmap, context)
                
                if (detectedFaces.isEmpty()) {
                    // No faces detected
                    updateProcessingResult(
                        ProcessingStatus.NO_FACE_DETECTED,
                        detectedFaces = emptyList(),
                        similarity = 0f
                    )
                    return@launch
                }
                
                // Calculate similarity with reference face
                val referenceFace = referenceFaceEmbedding
                if (referenceFace == null) {
                    Log.e(TAG, "Reference face embedding is null")
                    updateProcessingResult(ProcessingStatus.ERROR)
                    return@launch
                }
                
                // Calculate individual similarities for each detected face
                val facesWithSimilarity = mutableListOf<FaceDetectionResult>()
                var bestSimilarity = 0f
                
                for (face in detectedFaces) {
                    val similarity = FaceNetHelper.calculateSimilarity(referenceFace, face.embedding)
                    bestSimilarity = max(bestSimilarity, similarity)
                    
                    // Create new FaceDetectionResult with individual similarity
                    val faceWithSimilarity = FaceDetectionResult(
                        boundingBox = face.boundingBox,
                        embedding = face.embedding,
                        croppedFaceBitmap = face.croppedFaceBitmap,
                        confidence = face.confidence,
                        similarityToReference = similarity
                    )
                    facesWithSimilarity.add(faceWithSimilarity)
                }
                
                val isMatch = bestSimilarity > MATCH_THRESHOLD
                
                Log.d(TAG, "Processed ${facesWithSimilarity.size} faces, similarities: ${facesWithSimilarity.map { (it.similarityToReference * 100).toInt() }}%")
                
                // Update processing result
                updateProcessingResult(
                    ProcessingStatus.COMPLETED,
                    detectedFaces = facesWithSimilarity,
                    similarity = bestSimilarity,
                    isMatch = isMatch
                )
                
                Log.d(TAG, "Processed photo with ${detectedFaces.size} faces, best similarity: $bestSimilarity")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing photo: ${e.message}")
                updateProcessingResult(ProcessingStatus.ERROR)
            }
        }
    }

    /**
     * Navigate to the next photo
     */
    fun navigateToNext(): Boolean {
        if (currentPhotoIndex < allPhotos.size - 1) {
            currentPhotoIndex++
            updateCurrentPhoto()
            return true
        }
        return false
    }

    /**
     * Navigate to the previous photo
     */
    fun navigateToPrevious(): Boolean {
        if (currentPhotoIndex > 0) {
            currentPhotoIndex--
            updateCurrentPhoto()
            return true
        }
        return false
    }

    /**
     * Get summary of all matches
     */
    fun getMatchingSummary(): List<Uri> {
        return processingResults
            .filter { it.isMatch && it.processingStatus == ProcessingStatus.COMPLETED }
            .map { it.photoUri }
    }

    /**
     * Check if all photos have been processed
     */
    fun isProcessingComplete(): Boolean {
        return processingResults.all { 
            it.processingStatus == ProcessingStatus.COMPLETED || 
            it.processingStatus == ProcessingStatus.NO_FACE_DETECTED ||
            it.processingStatus == ProcessingStatus.ERROR
        }
    }

    private fun updateCurrentPhoto() {
        if (currentPhotoIndex < allPhotos.size) {
            _currentPhoto.value = allPhotos[currentPhotoIndex]
            _currentPhotoResult.value = processingResults[currentPhotoIndex]
            
            _navigationState.value = NavigationState(
                currentIndex = currentPhotoIndex,
                totalCount = allPhotos.size,
                canGoNext = currentPhotoIndex < allPhotos.size - 1,
                canGoPrevious = currentPhotoIndex > 0,
                isLastPhoto = currentPhotoIndex == allPhotos.size - 1
            )
            
            _processingProgress.value = ProcessingProgress(
                currentIndex = currentPhotoIndex + 1,
                totalCount = allPhotos.size,
                percentComplete = ((currentPhotoIndex + 1) * 100) / allPhotos.size
            )
        }
    }

    private fun updateProcessingResult(
        status: ProcessingStatus,
        detectedFaces: List<FaceDetectionResult> = emptyList(),
        similarity: Float = 0f,
        isMatch: Boolean = false
    ) {
        if (currentPhotoIndex < processingResults.size) {
            val currentUri = allPhotos[currentPhotoIndex]
            processingResults[currentPhotoIndex] = PhotoProcessingResult(
                photoUri = currentUri,
                detectedFaces = detectedFaces,
                similarityScore = similarity,
                processingStatus = status,
                isMatch = isMatch
            )
            
            _currentPhotoResult.value = processingResults[currentPhotoIndex]
            
            // Update matches summary
            _allMatches.value = getMatchingSummary()
        }
    }

    private fun loadBitmapFromUri(uri: Uri, context: Context): Bitmap {
        val inputStream = context.contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(inputStream)
    }

    /**
     * Data class for navigation state
     */
    data class NavigationState(
        val currentIndex: Int,
        val totalCount: Int,
        val canGoNext: Boolean,
        val canGoPrevious: Boolean,
        val isLastPhoto: Boolean
    )

    /**
     * Data class for processing progress
     */
    data class ProcessingProgress(
        val currentIndex: Int,
        val totalCount: Int,
        val percentComplete: Int
    )
}
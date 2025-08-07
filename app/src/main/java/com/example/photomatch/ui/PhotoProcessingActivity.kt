package com.example.photomatch.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.photomatch.R
import com.example.photomatch.adapter.DetectedFaceAdapter
import com.example.photomatch.data.ProcessingStatus
import com.example.photomatch.databinding.ActivityPhotoProcessingBinding
import com.example.photomatch.util.FaceNetHelper
import com.example.photomatch.viewmodel.PhotoProcessingViewModel

/**
 * Activity for step-by-step photo processing with face detection visualization
 */
class PhotoProcessingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REFERENCE_PHOTO_URI = "reference_photo_uri"
        private const val TAG = "PhotoProcessingActivity"
    }

    private val viewModel: PhotoProcessingViewModel by viewModels()
    private lateinit var detectedFaceAdapter: DetectedFaceAdapter
    private lateinit var binding: ActivityPhotoProcessingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoProcessingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupObservers()
        initializeProcessing()
    }

    private fun setupViews() {
        // Setup RecyclerView for detected faces
        detectedFaceAdapter = DetectedFaceAdapter { _, _ ->
            // Handle face click - could show enlarged view
        }
        
        binding.rvDetectedFaces.apply {
            layoutManager = LinearLayoutManager(this@PhotoProcessingActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = detectedFaceAdapter
        }

        // Setup button listeners
        binding.btnNext.setOnClickListener {
            if (viewModel.navigateToNext()) {
                processCurrentPhotoIfNeeded()
            } else {
                // Reached end, show summary
                showSummaryScreen()
            }
        }

        binding.btnPrevious.setOnClickListener {
            viewModel.navigateToPrevious()
        }

        binding.btnShowSummary.setOnClickListener {
            showSummaryScreen()
        }
    }

    private fun setupObservers() {
        // Observe current photo changes
        viewModel.currentPhoto.observe(this) { photoUri ->
            displayCurrentPhoto(photoUri)
        }

        // Observe processing results
        viewModel.currentPhotoResult.observe(this) { result ->
            updateUIWithProcessingResult(result)
        }

        // Observe navigation state
        viewModel.navigationState.observe(this) { navState ->
            updateNavigationButtons(navState)
            updateProgressDisplay(navState)
        }

        // Observe processing progress
        viewModel.processingProgress.observe(this) { progress ->
            updateProgressBar(progress)
        }
    }

    private fun initializeProcessing() {
        val referencePhotoUri = intent.getStringExtra(EXTRA_REFERENCE_PHOTO_URI)
        if (referencePhotoUri != null) {
            viewModel.initializeWithReference(Uri.parse(referencePhotoUri), this)
            processCurrentPhotoIfNeeded()
        } else {
            finish() // Invalid state, return to previous screen
        }
    }

    private fun processCurrentPhotoIfNeeded() {
        viewModel.currentPhotoResult.value?.let { result ->
            if (result.processingStatus == ProcessingStatus.PENDING) {
                viewModel.processCurrentPhoto(this)
            }
        }
    }

    private fun displayCurrentPhoto(photoUri: Uri) {
        Glide.with(this)
            .load(photoUri)
            .centerCrop()
            .into(binding.ivCurrentPhoto)
    }

    private fun updateUIWithProcessingResult(result: com.example.photomatch.data.PhotoProcessingResult) {
        when (result.processingStatus) {
            ProcessingStatus.PENDING -> {
                showProcessingStatus("Ready to process...")
                hideResults()
            }
            
            ProcessingStatus.PROCESSING -> {
                showProcessingStatus("Processing photo...")
                hideResults()
            }
            
            ProcessingStatus.COMPLETED -> {
                hideProcessingStatus()
                showResults(result)
            }
            
            ProcessingStatus.NO_FACE_DETECTED -> {
                hideProcessingStatus()
                showNoFaceDetected()
            }
            
            ProcessingStatus.ERROR -> {
                hideProcessingStatus()
                showError()
            }
        }
    }

    private fun showResults(result: com.example.photomatch.data.PhotoProcessingResult) {
        // Show detected faces
        if (result.detectedFaces.isNotEmpty()) {
            binding.tvDetectedFacesTitle.visibility = View.VISIBLE
            binding.rvDetectedFaces.visibility = View.VISIBLE
            
            // Create pairs of (face, similarity) for adapter using individual similarities
            val facesWithSimilarity = result.detectedFaces.map { face ->
                Pair(face, face.similarityToReference) // Use individual face similarity
            }
            
            detectedFaceAdapter.submitList(facesWithSimilarity)
        }

        // Show match results
        binding.tvMatchResultsTitle.visibility = View.VISIBLE
        binding.llMatchResults.visibility = View.VISIBLE
        
        // Create match result text
        val matchText = if (result.isMatch) {
            "✓ MATCH FOUND - Similarity: ${(result.similarityScore * 100).toInt()}%"
        } else {
            "✗ NO MATCH - Similarity: ${(result.similarityScore * 100).toInt()}%"
        }
        
        // Add result text view programmatically
        binding.llMatchResults.removeAllViews()
        val textView = TextView(this).apply {
            text = matchText
            textSize = 16f
            setTextColor(if (result.isMatch) 
                getColor(android.R.color.holo_green_dark) 
            else 
                getColor(android.R.color.holo_red_dark))
        }
        binding.llMatchResults.addView(textView)
    }

    private fun showNoFaceDetected() {
        binding.tvDetectedFacesTitle.visibility = View.VISIBLE
        binding.tvDetectedFacesTitle.text = "No faces detected in this photo"
        binding.rvDetectedFaces.visibility = View.GONE
        
        binding.tvMatchResultsTitle.visibility = View.VISIBLE
        binding.tvMatchResultsTitle.text = "Cannot process - no faces found"
        binding.llMatchResults.visibility = View.GONE
    }

    private fun showError() {
        binding.tvDetectedFacesTitle.visibility = View.VISIBLE
        binding.tvDetectedFacesTitle.text = "Error processing photo"
        binding.rvDetectedFaces.visibility = View.GONE
        
        binding.tvMatchResultsTitle.visibility = View.GONE
        binding.llMatchResults.visibility = View.GONE
    }

    private fun hideResults() {
        binding.tvDetectedFacesTitle.visibility = View.GONE
        binding.rvDetectedFaces.visibility = View.GONE
        binding.tvMatchResultsTitle.visibility = View.GONE
        binding.llMatchResults.visibility = View.GONE
    }

    private fun showProcessingStatus(message: String) {
        binding.tvProcessingStatus.text = message
        binding.tvProcessingStatus.visibility = View.VISIBLE
    }

    private fun hideProcessingStatus() {
        binding.tvProcessingStatus.visibility = View.GONE
    }

    private fun updateNavigationButtons(navState: PhotoProcessingViewModel.NavigationState) {
        binding.btnPrevious.isEnabled = navState.canGoPrevious
        binding.btnNext.isEnabled = navState.canGoNext || navState.isLastPhoto
        
        if (navState.isLastPhoto) {
            binding.btnNext.text = "Finish"
            binding.btnShowSummary.visibility = View.VISIBLE
        } else {
            binding.btnNext.text = "Next"
            binding.btnShowSummary.visibility = View.GONE
        }
    }

    private fun updateProgressDisplay(navState: PhotoProcessingViewModel.NavigationState) {
        binding.tvPhotoCounter.text = "Photo ${navState.currentIndex + 1} of ${navState.totalCount}"
    }

    private fun updateProgressBar(progress: PhotoProcessingViewModel.ProcessingProgress) {
        binding.progressBar.progress = progress.percentComplete
    }

    private fun showSummaryScreen() {
        val matchingPhotos = viewModel.getMatchingSummary()
        
        // Create intent to show summary with original MainActivity logic
        val intent = Intent(this, MainActivity::class.java).apply {
            putStringArrayListExtra("matching_photos", ArrayList(matchingPhotos.map { it.toString() }))
            putExtra("show_results", true)
        }
        
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        FaceNetHelper.release()
    }
}
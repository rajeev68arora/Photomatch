package com.example.photomatch.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.photomatch.R
import com.example.photomatch.adapter.DetectedFaceAdapter
import com.example.photomatch.adapter.DetectedFaceItem
import com.example.photomatch.data.ProcessingStatus
import com.example.photomatch.databinding.ActivityPhotoProcessingBinding
import com.example.photomatch.util.FaceNetHelper
import com.example.photomatch.viewmodel.PhotoProcessingViewModel
import kotlinx.coroutines.launch

/**
 * Activity for step-by-step photo processing with integrated face detection and confirmation
 * 
 * UI EVOLUTION HISTORY:
 * 1. Initially: Separate RecyclerViews for detected faces and confirmation
 * 2. Used ConfirmationFaceAdapter in dedicated confirmation section
 * 3. User feedback: "the check box has to appear diretly under the faces detected and not as a separate frame"
 * 4. User requested: "put two tick boxes, yes and no, toggle so that only one can be ticked"
 * 5. Refactored: Integrated Yes/No buttons directly under each face in single RecyclerView
 * 6. Removed: Separate confirmation UI section and ConfirmationFaceAdapter
 * 
 * CURRENT ARCHITECTURE:
 * - Single DetectedFaceAdapter handles both display and confirmation
 * - Three-tier system: AUTO_MATCH (green), AUTO_REJECT (red), PENDING (orange with buttons)
 * - Integrated Yes/No toggle buttons appear inline under faces needing confirmation
 * - Real-time database operations based on user confirmations
 * 
 * DATABASE INTEGRATION:
 * - AUTO_MATCH faces: Saved automatically to database
 * - User confirmed faces: Saved when Yes button clicked
 * - Rejected faces: Not saved to database (per user request)
 */
class PhotoProcessingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REFERENCE_PHOTO_URI = "reference_photo_uri"
        const val EXTRA_PERSON_FIRST_NAME = "person_first_name"
        const val EXTRA_PERSON_LAST_NAME = "person_last_name"
        private const val TAG = "PhotoProcessingActivity"
        
        // Threshold constants
        const val AUTO_MATCH_THRESHOLD = 0.60f  // ≥60% automatic match
        const val REJECT_THRESHOLD = 0.40f      // ≤40% automatic rejection
    }

    private val viewModel: PhotoProcessingViewModel by viewModels()
    private lateinit var detectedFaceAdapter: DetectedFaceAdapter
    private lateinit var binding: ActivityPhotoProcessingBinding
    
    private var personFirstName: String = ""
    private var personLastName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoProcessingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupObservers()
        initializeProcessing()
    }

    /**
     * Setup UI components with integrated confirmation system
     * 
     * INTEGRATION APPROACH:
     * - Single RecyclerView replaces previous dual-adapter system
     * - Confirmation callback handles user decisions inline
     * - Eliminates separate confirmation UI section
     */
    private fun setupViews() {
        // Setup unified RecyclerView for faces with integrated confirmation controls
        detectedFaceAdapter = DetectedFaceAdapter { face, confirmationState ->
            when (confirmationState) {
                DetectedFaceAdapter.ConfirmationState.USER_YES -> {
                    // User clicked Yes button - save match to database as CONFIRMED type
                    viewModel.confirmSelectedFaces(listOf(face))
                }
                DetectedFaceAdapter.ConfirmationState.USER_NO -> {
                    // User clicked No button - no database action (rejected matches not stored)
                    // The adapter handles UI state change to show No selection
                }
                else -> {
                    // AUTO_MATCH, AUTO_REJECT, PENDING states don't trigger additional actions
                }
            }
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


        // Setup STOP button listener
        binding.btnStop.setOnClickListener {
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
        personFirstName = intent.getStringExtra(EXTRA_PERSON_FIRST_NAME) ?: ""
        personLastName = intent.getStringExtra(EXTRA_PERSON_LAST_NAME) ?: ""
        
        if (referencePhotoUri != null && personFirstName.isNotEmpty() && personLastName.isNotEmpty()) {
            // Update UI to show person name
            title = "Finding $personFirstName $personLastName"
            
            viewModel.initializeWithReference(Uri.parse(referencePhotoUri), this, personFirstName, personLastName)
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

    /**
     * Display processing results with integrated confirmation system
     * 
     * UNIFIED DISPLAY APPROACH:
     * - All faces shown in single RecyclerView with appropriate states
     * - Three-tier classification determines UI appearance
     * - PENDING faces automatically show Yes/No toggle buttons
     * 
     * REPLACES: Previous system with separate detected and confirmation sections
     */
    private fun showResults(result: com.example.photomatch.data.PhotoProcessingResult) {
        // Show all detected faces with integrated confirmation controls
        if (result.detectedFaces.isNotEmpty()) {
            binding.tvDetectedFacesTitle.visibility = View.VISIBLE
            binding.rvDetectedFaces.visibility = View.VISIBLE
            
            // Create unified face items with three-tier states
            val faceItems = result.detectedFaces.map { face ->
                val similarity = face.similarityToReference
                
                // Determine state based on three-tier thresholds
                val confirmationState = when {
                    similarity >= AUTO_MATCH_THRESHOLD -> DetectedFaceAdapter.ConfirmationState.AUTO_MATCH   // Green, auto-saved
                    similarity <= REJECT_THRESHOLD -> DetectedFaceAdapter.ConfirmationState.AUTO_REJECT     // Red, not saved
                    else -> DetectedFaceAdapter.ConfirmationState.PENDING                                   // Orange, shows Yes/No buttons
                }
                
                DetectedFaceItem(
                    face = face,
                    similarity = similarity,
                    confirmationState = confirmationState
                )
            }
            
            // Single adapter handles all face types with integrated controls
            detectedFaceAdapter.submitList(faceItems)
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
        lifecycleScope.launch {
            val matchingPhotos = viewModel.getMatchingSummary()
            
            // Create intent to show summary with original MainActivity logic
            val intent = Intent(this@PhotoProcessingActivity, MainActivity::class.java).apply {
                putStringArrayListExtra("matching_photos", ArrayList(matchingPhotos.map { it.toString() }))
                putExtra("show_results", true)
            }
            
            startActivity(intent)
            finish()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        FaceNetHelper.release()
    }
}
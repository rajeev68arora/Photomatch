package com.example.photomatch.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.photomatch.R
import com.example.photomatch.adapter.PhotoAdapter
import com.example.photomatch.data.database.MatchRepository
import com.example.photomatch.data.database.Person
import com.example.photomatch.data.database.PeopleRepository
import com.example.photomatch.databinding.ActivityMainBinding
import com.example.photomatch.util.FaceMatchingService
import com.example.photomatch.util.FaceNetHelper
import com.example.photomatch.viewmodel.PhotoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity(), PersonNameDialog.PersonNameListener, PersonSelectionDialog.PersonSelectionListener, DuplicateFaceDialog.DuplicateFaceDialogListener {
    var TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private val photoViewModel: PhotoViewModel by viewModel()
    private lateinit var photoAdapter: PhotoAdapter

    private var capturedImageUri: Uri? = null
    private var pendingBitmap: Bitmap? = null
    private var personFirstName: String = ""
    private var personLastName: String = ""
    private var selectedPerson: Person? = null
    
    // Database and matching services - centrally managed
    private lateinit var peopleRepository: PeopleRepository
    private lateinit var matchRepository: MatchRepository
    private lateinit var faceMatchingService: FaceMatchingService

    // Register for activity result to take a picture
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        capturedImageUri?.let { uri ->
            if (success) {
                try {
                    val capturedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                    processImage(capturedBitmap)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading captured image: ${e.message}")
                    showToast("Error loading captured image")
                }
            } else {
                showToast("Photo capture failed.")
            }
        }
    }

    private val getImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                // Convert URI to Bitmap
                val bitmap = getBitmapFromUri(it)
                if (bitmap != null) {
                    processImage(bitmap)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize database and matching services with shared repositories
        peopleRepository = PeopleRepository(this)
        matchRepository = MatchRepository(this)
        faceMatchingService = FaceMatchingService(this, peopleRepository)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        checkAndRequestPermissions()
        
        // Handle results from PhotoProcessingActivity
        handleIncomingResults()
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter()
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity,2)
            adapter = photoAdapter
        }
    }

    private fun setupObservers() {
        photoViewModel.matchingPhotos.observe(this) { matches ->
            hideProgress()
            if (matches.isNotEmpty()) {
                Log.d(TAG, "setupObservers: ${matches.toList()}")
                FaceNetHelper.release()
                photoAdapter.submitList(matches)
            } else {
                showToast("No matching photos found")
            }
        }

        photoViewModel.processingProgress.observe(this) { progress ->
            updateProgress(progress)
        }
    }

    private fun setupClickListeners() {
        binding.captureButton.setOnClickListener {
            startCameraCapture()

            // Launch the image picker
//            getImageLauncher.launch("image/*")
        }

        binding.uploadButton.setOnClickListener {
            // Check storage/media permission before launching file picker
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                getImageLauncher.launch("image/*")
            } else {
                // Request permission
                requestPermissions(arrayOf(permission), PERMISSION_REQUEST_CODE)
            }
        }

        binding.btnSelectPerson.setOnClickListener {
            // Open PersonSelectionDialog to choose from saved people
            val personSelectionDialog = PersonSelectionDialog()
            personSelectionDialog.show(supportFragmentManager, "PersonSelectionDialog")
        }
    }

    /**
     * Check and request necessary permissions on app startup
     */
    private fun checkAndRequestPermissions() {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        // Check storage/media permission for upload functionality
        if (checkSelfPermission(storagePermission) != PackageManager.PERMISSION_GRANTED) {
            binding.uploadButton.isEnabled = false
            // Don't request on startup - only when user tries to upload
        }
        
        // Check camera permission for capture functionality  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            binding.captureButton.isEnabled = false
            // Don't request on startup - only when user tries to capture
        }
    }
    
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Storage/Media permission granted - enable upload functionality
                    showToast("Permission granted. You can now upload photos.")
                    binding.uploadButton.isEnabled = true
                } else {
                    // Storage/Media permission denied - show explanation and disable functionality
                    handlePermissionDenied()
                }
            }
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Camera permission granted - proceed with capture
                    showToast("Camera permission granted.")
                    startCameraCapture() // Retry camera capture
                } else {
                    // Camera permission denied
                    handleCameraPermissionDenied()
                }
            }
        }
    }
    
    /**
     * Handle permission denial with user-friendly explanation
     */
    private fun handlePermissionDenied() {
        // Disable upload functionality
        binding.uploadButton.isEnabled = false
        
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "media files"
        } else {
            "storage"
        }
        
        // Check if we should show rationale
        val shouldShowRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        if (shouldShowRationale) {
            // Show explanation dialog
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Photomatch needs access to $permission to upload photos from your device. Without this permission, you can only use the camera to take new photos.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    checkAndRequestPermissions() // Request again
                }
                .setNegativeButton("Continue without Upload", null)
                .show()
        } else {
            // Permission permanently denied - guide to settings
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permission Denied")
                .setMessage("Photo upload permission was denied. To enable photo upload, please go to Settings > Apps > Photomatch > Permissions and enable $permission access.")
                .setPositiveButton("Open Settings") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Continue without Upload", null)
                .show()
        }
    }
    
    /**
     * Handle camera permission denial with user-friendly explanation
     */
    private fun handleCameraPermissionDenied() {
        // Disable camera functionality
        binding.captureButton.isEnabled = false
        
        // Check if we should show rationale
        val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        
        if (shouldShowRationale) {
            // Show explanation dialog
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Camera Permission Required")
                .setMessage("Photomatch needs camera access to take photos. Without this permission, you can only upload existing photos from your device.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("Continue without Camera", null)
                .show()
        } else {
            // Permission permanently denied - guide to settings
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Camera Permission Denied")
                .setMessage("Camera permission was denied. To enable photo capture, please go to Settings > Apps > Photomatch > Permissions and enable camera access.")
                .setPositiveButton("Open Settings") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Continue without Camera", null)
                .show()
        }
    }
    
    /**
     * Open app settings for manual permission management
     */
    private fun openAppSettings() {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            showToast("Could not open settings. Please enable permissions manually.")
            Log.e(TAG, "Error opening app settings: ${e.message}")
        }
    }

    private fun startCameraCapture() {
        // Check camera permission for Android API 23+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
                return
            }
        }
        
        val imageFile = File.createTempFile("captured_image_", ".jpg", cacheDir)
        capturedImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )

        capturedImageUri?.let {
            takePictureLauncher.launch(it)
        } ?: showToast("Error creating file for photo.")
    }


    private fun processImage(bitmap: Bitmap) {
        // Store bitmap and show name dialog first
        pendingBitmap = bitmap
        
        // Show person name dialog
        val nameDialog = PersonNameDialog.newInstance()
        nameDialog.show(supportFragmentManager, "PersonNameDialog")
    }
    
    private fun proceedWithProcessing() {
        pendingBitmap?.let { bitmap ->
            lifecycleScope.launch {
                try {
                    // Extract face embedding from the uploaded/captured photo
                    val faceEmbedding = FaceNetHelper.getFaceEmbeddings(bitmap, this@MainActivity)
                    if (faceEmbedding == null) {
                        showToast("No face detected in the image. Please try another photo.")
                        pendingBitmap = null
                        return@launch
                    }

                    // Check for duplicate faces in the database
                    val duplicateResult = faceMatchingService.analyzePotentialDuplicates(bitmap)
                    val duplicateCandidates = duplicateResult.candidates
                    
                    if (duplicateCandidates.isNotEmpty()) {
                        // Show duplicate confirmation dialog
                        val duplicateDialog = DuplicateFaceDialog.newInstance(duplicateCandidates)
                        duplicateDialog.show(supportFragmentManager, "DuplicateFaceDialog")
                    } else {
                        // No duplicates found - proceed with creating new person
                        createNewPersonAndContinue(bitmap, faceEmbedding)
                    }
                    
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error processing image: ${e.message}")
                    showToast("Error: ${e.message}")
                    pendingBitmap = null
                }
            }
        }
    }
    
    private suspend fun createNewPersonAndContinue(bitmap: Bitmap, faceEmbedding: FloatArray) {
        try {
            // Save reference bitmap to permanent file
            val referenceFile = File(filesDir, "person_reference_${System.currentTimeMillis()}.jpg")
            val fileOutputStream = referenceFile.outputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
            fileOutputStream.close()
            
            val referenceUri = FileProvider.getUriForFile(
                this@MainActivity,
                "${packageName}.fileprovider",
                referenceFile
            )
            
            // Create new Person object
            val newPerson = Person(
                id = 0, // Will be assigned by database
                firstName = personFirstName,
                lastName = personLastName,
                referencePhotoUri = referenceUri.toString(),
                faceEmbedding = faceEmbedding,
                faceBitmap = bitmap,
                createdTimestamp = System.currentTimeMillis(),
                lastUsedTimestamp = System.currentTimeMillis()
            )
            
            // Insert into database
            val personId = peopleRepository.insertPerson(newPerson)
            Log.d("MainActivity", "Inserted new person: $personFirstName $personLastName (ID: $personId)")
            
            // Continue with photo processing using the new person's data
            continueWithPhotoProcessing(bitmap, personId)
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating new person: ${e.message}")
            showToast("Error saving person: ${e.message}")
            pendingBitmap = null
        }
    }
    
    private suspend fun continueWithPhotoProcessing(referenceBitmap: Bitmap, personId: Long? = null) {
        try {
            // Save the reference image temporarily
            val tempFile = File(cacheDir, "reference_image_${System.currentTimeMillis()}.jpg")
            val fileOutputStream = tempFile.outputStream()
            referenceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
            fileOutputStream.close()
            
            val referenceUri = FileProvider.getUriForFile(
                this@MainActivity,
                "${packageName}.fileprovider",
                tempFile
            )

            // Launch PhotoProcessingActivity with person data
            val intent = Intent(this@MainActivity, PhotoProcessingActivity::class.java).apply {
                putExtra(PhotoProcessingActivity.EXTRA_REFERENCE_PHOTO_URI, referenceUri.toString())
                
                // NEW: Pass person_id if available
                if (personId != null) {
                    putExtra(PhotoProcessingActivity.EXTRA_PERSON_ID, personId)
                }
                
                // Legacy: Keep name fields for backward compatibility
                putExtra(PhotoProcessingActivity.EXTRA_PERSON_FIRST_NAME, personFirstName)
                putExtra(PhotoProcessingActivity.EXTRA_PERSON_LAST_NAME, personLastName)
            }
            startActivity(intent)

            Log.d("MainActivity", "Launching PhotoProcessingActivity with reference image for $personFirstName $personLastName")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error launching photo processing: ${e.message}")
            showToast("Error: ${e.message}")
        } finally {
            // Clear pending bitmap and person data
            pendingBitmap = null
            personFirstName = ""
            personLastName = ""
        }
    }


    private fun hideProgress() {
        binding.progressLayout.visibility = View.GONE
        binding.captureButton.isEnabled = true
        binding.uploadButton.isEnabled = true
    }

    private fun updateProgress(progress: Int) {
        binding.progressBar.progress = progress
        binding.progressText.text = "Processing: $progress%"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }



    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
    
    private fun handleIncomingResults() {
        // Check if this activity was launched with results from PhotoProcessingActivity
        if (intent.getBooleanExtra("show_results", false)) {
            val matchingPhotos = intent.getStringArrayListExtra("matching_photos")
            if (!matchingPhotos.isNullOrEmpty()) {
                val uris = matchingPhotos.map { Uri.parse(it) }
                photoAdapter.submitList(uris)
                
                // Hide the input buttons since we're showing results
                binding.captureButton.visibility = View.GONE
                binding.uploadButton.visibility = View.GONE
                
                showToast("Found ${uris.size} matching photos")
            }
        }
    }

    // PersonNameDialog.PersonNameListener implementation
    override fun onPersonNameEntered(firstName: String, lastName: String) {
        personFirstName = firstName
        personLastName = lastName
        proceedWithProcessing()
    }
    
    override fun onPersonNameCanceled() {
        // Clear pending bitmap
        pendingBitmap = null
        showToast("Photo processing canceled")
    }
    
    // PersonSelectionDialog.PersonSelectionListener implementation
    override fun onPersonSelected(person: Person) {
        selectedPerson = person
        proceedWithPersonSelection()
    }
    
    override fun onAddNewPersonRequested() {
        // Guide user to capture/upload photo first, then get name
        showAddNewPersonPhotoDialog()
    }
    
    private fun showAddNewPersonPhotoDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add New Person")
            .setMessage("To add a new person, please first take or upload their photo.")
            .setPositiveButton("Take Photo") { _, _ ->
                startCameraCapture()
            }
            .setNeutralButton("Upload Photo") { _, _ ->
                // Check storage/media permission before launching file picker
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                
                if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                    getImageLauncher.launch("image/*")
                } else {
                    // Request permission
                    requestPermissions(arrayOf(permission), PERMISSION_REQUEST_CODE)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun getPeopleRepository(): PeopleRepository {
        return peopleRepository
    }
    
    /**
     * Get shared MatchRepository instance for dependency injection
     */
    fun getMatchRepository(): MatchRepository {
        return matchRepository
    }
    
    // DuplicateFaceDialog.DuplicateFaceDialogListener implementation
    override fun onUseExistingPerson(person: Person) {
        // User chose to use existing person instead of creating new one
        selectedPerson = person
        
        // Update person names to match the existing person
        personFirstName = person.firstName
        personLastName = person.lastName
        
        // Proceed with photo processing using existing person's face
        lifecycleScope.launch {
            continueWithPhotoProcessing(person.faceBitmap, person.id)
        }
        
        Log.d("MainActivity", "Using existing person: ${person.fullName}")
    }
    
    override fun onAddAsNewPerson() {
        // User confirmed to add as new person despite duplicates
        pendingBitmap?.let { bitmap ->
            lifecycleScope.launch {
                try {
                    val faceEmbedding = FaceNetHelper.getFaceEmbeddings(bitmap, this@MainActivity)
                    if (faceEmbedding != null) {
                        createNewPersonAndContinue(bitmap, faceEmbedding)
                    } else {
                        showToast("Error extracting face data")
                        pendingBitmap = null
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error adding new person: ${e.message}")
                    showToast("Error: ${e.message}")
                    pendingBitmap = null
                }
            }
        }
        
        Log.d("MainActivity", "Adding new person despite duplicates: $personFirstName $personLastName")
    }
    
    override fun onCancel() {
        // User cancelled the duplicate confirmation - clear pending data
        pendingBitmap = null
        personFirstName = ""
        personLastName = ""
        showToast("Photo processing cancelled")
        
        Log.d("MainActivity", "User cancelled duplicate confirmation")
    }
    
    private fun proceedWithPersonSelection() {
        selectedPerson?.let { person ->
            lifecycleScope.launch {
                try {
                    // Save the person's face bitmap as reference image temporarily
                    val tempFile = File(cacheDir, "reference_image_${System.currentTimeMillis()}.jpg")
                    val fileOutputStream = tempFile.outputStream()
                    person.faceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
                    fileOutputStream.close()
                    
                    val referenceUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        tempFile
                    )

                    // Launch PhotoProcessingActivity with selected person details
                    val intent = Intent(this@MainActivity, PhotoProcessingActivity::class.java).apply {
                        putExtra(PhotoProcessingActivity.EXTRA_REFERENCE_PHOTO_URI, referenceUri.toString())
                        
                        // NEW: Pass person_id 
                        putExtra(PhotoProcessingActivity.EXTRA_PERSON_ID, person.id)
                        
                        // Legacy: Keep name fields for backward compatibility
                        putExtra(PhotoProcessingActivity.EXTRA_PERSON_FIRST_NAME, person.firstName)
                        putExtra(PhotoProcessingActivity.EXTRA_PERSON_LAST_NAME, person.lastName)
                    }
                    startActivity(intent)

                    Log.d("MainActivity", "Launching PhotoProcessingActivity with selected person: ${person.fullName}")
                    
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error processing selected person: ${e.message}")
                    showToast("Error: ${e.message}")
                } finally {
                    // Clear selected person
                    selectedPerson = null
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Close shared repositories
        try {
            if (::peopleRepository.isInitialized) {
                peopleRepository.close()
            }
            if (::matchRepository.isInitialized) {
                matchRepository.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing repositories: ${e.message}")
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1002
    }
}

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
import com.example.photomatch.databinding.ActivityMainBinding
import com.example.photomatch.util.FaceNetHelper
import com.example.photomatch.viewmodel.PhotoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    var TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private val photoViewModel: PhotoViewModel by viewModel()
    private lateinit var photoAdapter: PhotoAdapter

    private var capturedImageUri: Uri? = null

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

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        checkPermissions()
        
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
            getImageLauncher.launch("image/*")
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), PERMISSION_REQUEST_CODE)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun startCameraCapture() {
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
        lifecycleScope.launch {
            try {
                // Save the reference image temporarily
                val tempFile = File(cacheDir, "reference_image_${System.currentTimeMillis()}.jpg")
                val fileOutputStream = tempFile.outputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
                fileOutputStream.close()
                
                val referenceUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    tempFile
                )

                // Launch PhotoProcessingActivity
                val intent = Intent(this@MainActivity, PhotoProcessingActivity::class.java).apply {
                    putExtra(PhotoProcessingActivity.EXTRA_REFERENCE_PHOTO_URI, referenceUri.toString())
                }
                startActivity(intent)

                Log.d("MainActivity", "Launching PhotoProcessingActivity with reference image")
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing image: ${e.message}")
                showToast("Error: ${e.message}")
            }
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

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}

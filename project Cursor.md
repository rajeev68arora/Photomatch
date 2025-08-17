# PhotoMatch Android App - Project Documentation

## Project Overview
PhotoMatch is an Android application that performs face recognition and matching using TensorFlow Lite and ML Kit. The app allows users to capture or upload photos, extract face embeddings, and find matching faces in their photo gallery.

## File Structure and Purpose

### Application Entry Point
- **PhotoMatchApp.kt**: Main application class that initializes Koin dependency injection framework and sets up the app context.

### UI Layer (ui/)
- **MainActivity.kt**: Primary activity handling camera capture, photo upload, person management, and navigation to photo processing. Manages permissions, face detection, and duplicate prevention.
- **PhotoProcessingActivity.kt**: Step-by-step photo processing activity with integrated face detection and confirmation system. Shows detected faces with Yes/No toggle buttons for user confirmation.
- **PersonNameDialog.kt**: Dialog for entering first and last names when adding new people to the database.
- **PersonSelectionDialog.kt**: Dialog for selecting existing people from the database for face matching.
- **DuplicateFaceDialog.kt**: Dialog for confirming potential duplicate faces when adding new people.

### Data Layer (data/)
- **PhotoProcessingResult.kt**: Data classes for face detection results, processing status, and match information.
- **database/Person.kt**: Data class representing a person with face embeddings, bitmaps, and metadata for persistent storage.
- **database/Match.kt**: Data class for storing face match results with foreign key relationships to people table.
- **database/DatabaseHelper.kt**: SQLite database helper managing people and matches tables with migration support.
- **database/MatchContract.kt**: Database schema definitions and SQL statements for people and matches tables.
- **database/PeopleRepository.kt**: Repository for managing persistent people storage with face embeddings and duplicate detection.
- **database/MatchRepository.kt**: Repository for managing face match results and database operations.

### ViewModels (viewmodel/)
- **PhotoViewModel.kt**: ViewModel for finding matching faces in gallery using face embeddings and similarity calculations.
- **PhotoProcessingViewModel.kt**: ViewModel for managing photo processing workflow, navigation, and face detection results.

### Adapters (adapter/)
- **PhotoAdapter.kt**: RecyclerView adapter for displaying matched photos in grid layout.
- **DetectedFaceAdapter.kt**: Adapter for displaying detected faces with integrated three-tier confirmation system (auto-match, auto-reject, pending).
- **PersonSelectionAdapter.kt**: Adapter for displaying people in selection dialogs.
- **ConfirmationFaceAdapter.kt**: Legacy adapter for face confirmation (replaced by integrated system).

### Utilities (util/)
- **FaceNetHelper.kt**: Core face recognition utility using TensorFlow Lite model for extracting face embeddings and calculating similarities.
- **FaceMatchingService.kt**: Service for face matching, similarity comparison, and duplicate detection with intelligent thresholds.
- **GalleryHelper.kt**: Utility for accessing device gallery images and retrieving photo URIs.
- **SerializationUtils.kt**: Utility for serializing/deserializing face embeddings and bitmaps for database storage.

### Dependency Injection (di/)
- **AppModule.kt**: Koin module configuration for dependency injection of ViewModels and repositories.

### Resources (res/)
- **layout/**: XML layout files for activities, dialogs, and RecyclerView items.
- **drawable/**: Vector drawables and shape definitions for UI elements.
- **values/**: Colors, strings, and themes configuration.
- **xml/**: File provider paths and backup rules configuration.

### Assets
- **facenet.tflite**: TensorFlow Lite model for face embedding extraction.

## Data Objects

### Person
Represents a person in the database with face data. Stores first/last name, face embedding (128-dim float array), face bitmap, reference photo URI, and usage timestamps. Enables reusable face recognition across sessions.

### Match
Represents a face match result with photo URI, person ID (foreign key), similarity score, match type (AUTO_MATCH or CONFIRMED), and timestamp. Links to people table for better organization.

### PhotoProcessingResult
Contains processing results for a single photo including detected faces, similarity scores, processing status, and match information. Used for step-by-step photo analysis.

### FaceDetectionResult
Represents a single detected face with bounding box, embedding, cropped bitmap, confidence score, and similarity to reference face. Used for detailed face analysis.

### DetectedFaceItem
Combines face detection result with similarity score and confirmation state for unified display in RecyclerView. Supports three-tier confirmation system.

## Classes and Their Purposes

### MainActivity
Primary UI controller handling camera capture, photo upload, permission management, and navigation. Manages face detection workflow and duplicate prevention before creating new people.

### PhotoProcessingActivity
Step-by-step photo processing with integrated face detection and confirmation. Shows detected faces with Yes/No toggle buttons for user decisions. Manages navigation between photos and displays results.

### FaceNetHelper
Core face recognition engine using TensorFlow Lite. Extracts 128-dimensional face embeddings, detects faces using ML Kit, and calculates cosine similarity between embeddings. Handles image preprocessing and model inference.

### FaceMatchingService
Intelligent face matching service with duplicate detection. Analyzes potential duplicates before adding new people, calculates batch similarities, and provides confidence-based recommendations. Uses configurable thresholds for different scenarios.

### PeopleRepository
Database repository for persistent people storage. Manages CRUD operations for people with face embeddings, provides duplicate detection through similarity analysis, and tracks usage patterns for smart suggestions.

### MatchRepository
Repository for face match results. Stores positive matches (AUTO_MATCH and CONFIRMED types only), manages foreign key relationships to people table, and provides query capabilities for match history.

### PhotoViewModel
ViewModel for gallery face matching. Processes all gallery images to find faces matching a reference embedding, calculates similarities, and updates UI with progress and results.

### PhotoProcessingViewModel
ViewModel for photo processing workflow. Manages current photo state, navigation between photos, face detection results, and database operations for confirmed matches.

### DetectedFaceAdapter
RecyclerView adapter with integrated confirmation system. Displays detected faces with three-tier states: auto-match (green), auto-reject (red), and pending (orange with Yes/No buttons). Handles user confirmation callbacks.

### DatabaseHelper
SQLite database manager with migration support. Creates people and matches tables with proper indexes, handles database versioning, and provides foreign key constraints for data integrity.

### GalleryHelper
Utility for accessing device photo gallery. Queries MediaStore for JPEG images, retrieves URIs sorted by date, and provides async access to gallery content for face matching.

### SerializationUtils
Utility for converting face embeddings and bitmaps to/from byte arrays for database storage. Handles compression and decompression of bitmap data and float array serialization.

## Key Features

1. **Face Recognition**: Uses TensorFlow Lite FaceNet model for accurate face embedding extraction
2. **Duplicate Prevention**: Intelligent duplicate detection before adding new people to database
3. **Three-Tier Confirmation**: Auto-match (≥60%), auto-reject (≤40%), and user confirmation (40-60%)
4. **Persistent Storage**: SQLite database with people and matches tables for reusable face recognition
5. **Gallery Integration**: Scans device gallery for face matching with progress tracking
6. **Permission Management**: Handles camera and storage permissions with user-friendly dialogs
7. **Modern UI**: Material Design with RecyclerViews, dialogs, and integrated confirmation controls
8. **Performance Optimization**: Background processing, lazy loading, and efficient database queries

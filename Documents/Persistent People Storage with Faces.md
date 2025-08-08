Plan: Persistent People Storage with Face Recognition                                  │ │
│ │                                                                                        │ │
│ │ 🎯 Overview                                                                            │ │
│ │                                                                                        │ │
│ │ Add a persistent "People Database" that stores names, face embeddings, and cropped     │ │
│ │ face bitmaps. Include intelligent duplicate detection using face similarity and        │ │
│ │ provide a dropdown selection option on the main screen.                                │ │
│ │                                                                                        │ │
│ │ 📊 Database Schema Changes                                                             │ │
│ │                                                                                        │ │
│ │ New Table: people                                                                      │ │
│ │                                                                                        │ │
│ │ CREATE TABLE people (                                                                  │ │
│ │     _id INTEGER PRIMARY KEY AUTOINCREMENT,                                             │ │
│ │     first_name TEXT NOT NULL,                                                          │ │
│ │     last_name TEXT NOT NULL,                                                           │ │
│ │     reference_photo_uri TEXT NOT NULL,                                                 │ │
│ │     face_embedding BLOB NOT NULL,        -- 128-dim float array as bytes               │ │
│ │     face_bitmap BLOB NOT NULL,           -- Cropped face image as bytes                │ │
│ │     similarity_threshold REAL DEFAULT 0.6, -- Custom threshold per person              │ │
│ │     created_timestamp INTEGER NOT NULL,                                                │ │
│ │     last_used_timestamp INTEGER NOT NULL                                               │ │
│ │ )                                                                                      │ │
│ │                                                                                        │ │
│ │ Updated Matches Table                                                                  │ │
│ │                                                                                        │ │
│ │ ALTER TABLE matches ADD COLUMN person_id INTEGER REFERENCES people(_id);               │ │
│ │ -- Keep existing name columns for backward compatibility                               │ │
│ │                                                                                        │ │
│ │ 🔧 Core Implementation Components                                                      │ │
│ │                                                                                        │ │
│ │ 1. PeopleRepository (New)                                                              │ │
│ │                                                                                        │ │
│ │ - insertPerson(person: Person): Long                                                   │ │
│ │ - getAllPeople(): List<Person>                                                         │ │
│ │ - findSimilarPerson(embedding: FloatArray): Person?                                    │ │
│ │ - updateLastUsed(personId: Long)                                                       │ │
│ │ - deletePerson(personId: Long)                                                         │ │
│ │                                                                                        │ │
│ │ 2. Person Data Class (New)                                                             │ │
│ │                                                                                        │ │
│ │ data class Person(                                                                     │ │
│ │     val id: Long = 0,                                                                  │ │
│ │     val firstName: String,                                                             │ │
│ │     val lastName: String,                                                              │ │
│ │     val referencePhotoUri: String,                                                     │ │
│ │     val faceEmbedding: FloatArray,                                                     │ │
│ │     val faceBitmap: Bitmap,                                                            │ │
│ │     val similarityThreshold: Float = 0.6f,                                             │ │
│ │     val createdTimestamp: Long,                                                        │ │
│ │     val lastUsedTimestamp: Long                                                        │ │
│ │ )                                                                                      │ │
│ │                                                                                        │ │
│ │ 3. Duplicate Detection Logic                                                           │ │
│ │                                                                                        │ │
│ │ - Calculate embedding for new face                                                     │ │
│ │ - Compare against all existing people embeddings                                       │ │
│ │ - If similarity > 0.85 (high threshold), prompt user for confirmation                  │ │
│ │ - Options: "Use Existing", "Add as New", "Cancel"                                      │ │
│ │                                                                                        │ │
│ │ 4. MainActivity UI Updates                                                             │ │
│ │                                                                                        │ │
│ │ - Add spinner/dropdown showing saved people                                            │ │
│ │ - Three selection modes:                                                               │ │
│ │   a. Take Photo (existing)                                                             │ │
│ │   b. Upload Photo (existing)                                                           │ │
│ │   c. Select Saved Person (new dropdown)                                                │ │
│ │                                                                                        │ │
│ │ 5. PersonSelectionDialog (New)                                                         │ │
│ │                                                                                        │ │
│ │ - Shows saved people with face thumbnails                                              │ │
│ │ - Search/filter by name                                                                │ │
│ │ - "Add New Person" option                                                              │ │
│ │ - Delete person option (with confirmation)                                             │ │
│ │                                                                                        │ │
│ │ 🔄 User Workflow Changes                                                               │ │
│ │                                                                                        │ │
│ │ Adding New Person:                                                                     │ │
│ │                                                                                        │ │
│ │ 1. User takes/uploads photo                                                            │ │
│ │ 2. System extracts face embedding                                                      │ │
│ │ 3. Check for similar faces in database                                                 │ │
│ │ 4. If similar face found (>85% similarity):                                            │ │
│ │   - Show confirmation dialog with existing person's info                               │ │
│ │   - Options: "Use [Name]", "Add as New Person", "Cancel"                               │ │
│ │ 5. If no similar face or user chooses "Add New":                                       │ │
│ │   - Show name input dialog                                                             │ │
│ │   - Save person to database                                                            │ │
│ │ 6. Proceed with gallery matching                                                       │ │
│ │                                                                                        │ │
│ │ Using Saved Person:                                                                    │ │
│ │                                                                                        │ │
│ │ 1. User selects dropdown option                                                        │ │
│ │ 2. PersonSelectionDialog opens with thumbnails                                         │ │
│ │ 3. User selects person or adds new                                                     │ │
│ │ 4. Skip name input, proceed directly to gallery matching                               │ │
│ │                                                                                        │ │
│ │ 🛠️ Technical Implementation Step                                                      │ │
│ │                                                                                        │ │
│ │ Phase 1: Database Layer                                                                │ │
│ │                                                                                        │ │
│ │ 1. Create Person data class                                                            │ │
│ │ 2. Update MatchContract with people table schema                                       │ │
│ │ 3. Create PeopleRepository class                                                       │ │
│ │ 4. Add embedding/bitmap serialization utilities                                        │ │
│ │ 5. Update DatabaseHelper with migration logic                                          │ │
│ │                                                                                        │ │
│ │ Phase 2: Duplicate Detection                                                           │ │
│ │                                                                                        │ │
│ │ 1. Create FaceMatchingService for similarity comparison                                │ │
│ │ 2. Implement duplicate detection logic in ViewModel                                    │ │
│ │ 3. Create confirmation dialog for similar faces                                        │ │
│ │ 4. Add embedding comparison utilities                                                  │ │
│ │                                                                                        │ │
│ │ Phase 3: UI Updates                                                                    │ │
│ │                                                                                        │ │
│ │ 1. Add dropdown/spinner to MainActivity                                                │ │
│ │ 2. Create PersonSelectionDialog with RecyclerView                                      │ │
│ │ 3. Create PersonSelectionAdapter with thumbnails                                       │ │
│ │ 4. Update MainActivity flow logic                                                      │ │
│ │ 5. Add person management (delete/edit)                                                 │ │
│ │                                                                                        │ │
│ │ Phase 4: Integration                                                                   │ │
│ │                                                                                        │ │
│ │ 1. Update PhotoProcessingViewModel to work with Person objects                         │ │
│ │ 2. Modify match storage to reference person_id                                         │ │
│ │ 3. Update summary queries to use person relationships                                  │ │
│ │ 4. Add backward compatibility for existing matches                                     │ │
│ │                                                                                        │ │
│ │ Phase 5: Testing & Polish                                                              │ │
│ │                                                                                        │ │
│ │ 1. Test duplicate detection accuracy                                                   │ │
│ │ 2. Performance testing with large people database                                      │ │
│ │ 3. UI/UX refinements                                                                   │ │
│ │ 4. Error handling and edge cases                                                       │ │
│ │                                                                                        │ │
│ │ 📱 UI Mock-ups                                                                         │ │
│ │                                                                                        │ │
│ │ MainActivity Updates:                                                                  │ │
│ │                                                                                        │ │
│ │ [Take Photo] [Upload Photo] [▼ Select Person]                                          │ │
│ │                                  ├─ John Doe                                           │ │
│ │                                  ├─ Jane Smith                                         │ │
│ │                                  ├─ Mike Johnson                                       │ │
│ │                                  └─ + Add New Person                                   │ │
│ │                                                                                        │ │
│ │ PersonSelectionDialog:                                                                 │ │
│ │                                                                                        │ │
│ │ ┌─────────────────────────────┐                                                        │ │
│ │ │ Select Person               │                                                        │ │
│ │ ├─────────────────────────────┤                                                        │ │
│ │ │ [🔍 Search...]              │                                                        │ │
│ │ ├─────────────────────────────┤                                                        │ │
│ │ │ [👤] John Doe               │                                                        │ │
│ │ │ [👤] Jane Smith             │                                                        │ │
│ │ │ [👤] Mike Johnson           │                                                        │ │
│ │ ├─────────────────────────────┤                                                        │ │
│ │ │ [+ Add New Person]          │                                                        │ │
│ │ └─────────────────────────────┘                                                        │ │
│ │                                                                                        │ │
│ │ ⚡ Performance Considerations                                                           │ │
│ │                                                                                        │ │
│ │ Embedding Storage:                                                                     │ │
│ │                                                                                        │ │
│ │ - Store as BLOB (512 bytes for 128 floats)                                             │ │
│ │ - Index on created_timestamp for recent people                                         │ │
│ │ - Lazy loading for large datasets                                                      │ │
│ │                                                                                        │ │
│ │ Similarity Comparison:                                                                 │ │
│ │                                                                                        │ │
│ │ - Parallel processing for multiple comparisons                                         │ │
│ │ - Early termination when high similarity found                                         │ │
│ │ - Cache recent comparisons                                                             │ │
│ │                                                                                        │ │
│ │ Memory Management:                                                                     │ │
│ │                                                                                        │ │
│ │ - Load bitmaps on-demand                                                               │ │
│ │ - Compress face thumbnails for UI                                                      │ │
│ │ - Clear unused embeddings from memory                                                  │ │
│ │                                                                                        │ │
│ │ 🔒 Data Management                                                                     │ │
│ │                                                                                        │ │
│ │ Migration Strategy:                                                                    │ │
│ │                                                                                        │ │
│ │ - Backward compatibility with existing matches                                         │ │
│ │ - Gradual migration of old data                                                        │ │
│ │ - Fallback to name-based matching for legacy data                                      │ │
│ │                                                                                        │ │
│ │ Privacy Considerations:                                                                │ │
│ │                                                                                        │ │
│ │ - Local storage only (no cloud sync initially)                                         │ │
│ │ - User control over data deletion                                                      │ │
│ │ - Clear data consent in UI                                                             │ │
│ │                                                                                        │ │
│ │ 🎨 User Experience Enhancements                                                        │ │
│ │                                                                                        │ │
│ │ Smart Suggestions:                                                                     │ │
│ │                                                                                        │ │
│ │ - Recently used people at top of dropdown                                              │ │
│ │ - Frequency-based sorting                                                              │ │
│ │ - Quick search functionality                                                           │ │
│ │                                                                                        │ │
│ │ Visual Feedback:                                                                       │ │
│ │                                                                                        │ │
│ │ - Face thumbnails in selection dialog                                                  │ │
│ │ - Similarity confidence indicators                                                     │ │
│ │ - Processing progress for large comparisons                                            │ │
│ │                                                                                        │ │
│ │ This plan creates a robust, user-friendly people management system while maintaining   │ │
│ │ backward compatibility and providing intelligent duplicate detection.                  
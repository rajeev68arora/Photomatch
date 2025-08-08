package com.example.photomatch.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.photomatch.R
import com.example.photomatch.data.FaceDetectionResult
import com.google.android.material.button.MaterialButton

/**
 * Adapter for displaying detected faces with integrated three-tier confirmation system
 * 
 * IMPLEMENTATION HISTORY:
 * 1. Initially used separate confirmation UI with checkboxes in a dedicated section
 * 2. User requested: "remove these rejected records. also, teh check box has to appear 
 *    diretly under the faces detected and not as a separate frame. please put two tick 
 *    boxes, yes and no, toggle so that only one can be ticked by the user."
 * 3. Refactored to integrate Yes/No toggle buttons directly under each face
 * 4. Removed separate ConfirmationFaceAdapter and integrated all logic here
 * 
 * THREE-TIER SYSTEM:
 * - AUTO_MATCH (≥60%): Green status, automatically saved to database, no user action needed
 * - AUTO_REJECT (≤40%): Red status, not saved to database, no user action needed
 * - PENDING (40-60%): Orange status with Yes/No toggle buttons for user confirmation
 * 
 * USER INTERACTION:
 * - Yes button: Saves match to database as CONFIRMED type
 * - No button: Does not save to database (rejected matches are not stored per user request)
 * - Toggle behavior: Only one button can be selected at a time
 * 
 * DATABASE STORAGE CHANGE:
 * - Previously stored all three types (AUTO_MATCH, CONFIRMED, REJECTED)
 * - Now only stores positive matches (AUTO_MATCH, CONFIRMED)
 * - REJECTED matches are no longer persisted to reduce database clutter
 */
class DetectedFaceAdapter(
    private val onConfirmationChange: (FaceDetectionResult, ConfirmationState) -> Unit = { _, _ -> }
) : ListAdapter<DetectedFaceItem, DetectedFaceAdapter.FaceViewHolder>(FaceDiffCallback()) {

    companion object {
        const val AUTO_MATCH_THRESHOLD = 0.60f  // ≥60% automatic match
        const val REJECT_THRESHOLD = 0.40f      // ≤40% automatic rejection
    }
    
    /**
     * Confirmation states for the three-tier matching system
     * 
     * DESIGN RATIONALE:
     * - Replaces previous separate confirmation UI with integrated approach
     * - USER_YES/USER_NO states track user decisions within the main face list
     * - PENDING state triggers display of Yes/No toggle buttons
     */
    enum class ConfirmationState {
        AUTO_MATCH,    // ≥60% similarity - automatic match, shown in green
        AUTO_REJECT,   // ≤40% similarity - automatic rejection, shown in red  
        USER_YES,      // 40-60% similarity - user confirmed match, saves to database
        USER_NO,       // 40-60% similarity - user rejected match, not saved
        PENDING        // 40-60% similarity - awaiting user decision, shows toggle buttons
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detected_face, parent, false)
        return FaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: FaceViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onConfirmationChange)
    }

    class FaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivDetectedFace: ImageView = itemView.findViewById(R.id.ivDetectedFace)
        private val tvSimilarityScore: TextView = itemView.findViewById(R.id.tvSimilarityScore)
        private val tvMatchStatus: TextView = itemView.findViewById(R.id.tvMatchStatus)
        private val llConfirmationButtons: ViewGroup = itemView.findViewById(R.id.llConfirmationButtons)
        private val btnYes: MaterialButton = itemView.findViewById(R.id.btnYes)
        private val btnNo: MaterialButton = itemView.findViewById(R.id.btnNo)

        /**
         * Bind face data to view with integrated confirmation controls
         * 
         * INTEGRATION APPROACH:
         * - Single adapter now handles both face display and confirmation
         * - Yes/No buttons appear inline under faces requiring confirmation
         * - Eliminates need for separate confirmation UI section
         * 
         * @param item Contains face data, similarity score, and current confirmation state
         * @param onConfirmationChange Callback for user confirmation decisions
         */
        fun bind(
            item: DetectedFaceItem,
            onConfirmationChange: (FaceDetectionResult, ConfirmationState) -> Unit
        ) {
            val face = item.face
            val similarity = item.similarity
            val state = item.confirmationState
            
            // Display the cropped face bitmap
            ivDetectedFace.setImageBitmap(face.croppedFaceBitmap)

            // Format and display similarity score as percentage
            val percentage = (similarity * 100).toInt()
            tvSimilarityScore.text = "$percentage%"

            // Handle UI based on confirmation state - replaces previous separate confirmation flow
            when (state) {
                ConfirmationState.AUTO_MATCH -> {
                    tvMatchStatus.text = "AUTO MATCH"
                    tvMatchStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
                    tvSimilarityScore.setTextColor(Color.parseColor("#4CAF50"))
                    llConfirmationButtons.visibility = View.GONE
                }
                ConfirmationState.AUTO_REJECT -> {
                    tvMatchStatus.text = "AUTO REJECT"
                    tvMatchStatus.setTextColor(Color.parseColor("#F44336")) // Red
                    tvSimilarityScore.setTextColor(Color.parseColor("#F44336"))
                    llConfirmationButtons.visibility = View.GONE
                }
                ConfirmationState.PENDING -> {
                    // Show confirmation UI inline - this replaces the previous separate confirmation section
                    tvMatchStatus.text = "CONFIRM?"
                    tvMatchStatus.setTextColor(Color.parseColor("#FF9800")) // Orange
                    tvSimilarityScore.setTextColor(Color.parseColor("#FF9800"))
                    llConfirmationButtons.visibility = View.VISIBLE
                    resetButtons() // Reset to neutral state for user interaction
                }
                ConfirmationState.USER_YES -> {
                    tvMatchStatus.text = "USER CONFIRMED"
                    tvMatchStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
                    tvSimilarityScore.setTextColor(Color.parseColor("#4CAF50"))
                    llConfirmationButtons.visibility = View.VISIBLE
                    setButtonState(true)
                }
                ConfirmationState.USER_NO -> {
                    tvMatchStatus.text = "USER REJECTED"
                    tvMatchStatus.setTextColor(Color.parseColor("#F44336")) // Red
                    tvSimilarityScore.setTextColor(Color.parseColor("#F44336"))
                    llConfirmationButtons.visibility = View.VISIBLE
                    setButtonState(false)
                }
            }

            // Set button click listeners - implements toggle behavior as requested
            btnYes.setOnClickListener {
                // User confirmed match - will be saved to database as CONFIRMED type
                onConfirmationChange(face, ConfirmationState.USER_YES)
                setButtonState(true) // Update UI to show Yes selected
            }

            btnNo.setOnClickListener {
                // User rejected match - will NOT be saved to database per user request
                onConfirmationChange(face, ConfirmationState.USER_NO)
                setButtonState(false) // Update UI to show No selected
            }
        }

        /**
         * Reset buttons to neutral state for pending confirmation
         */
        private fun resetButtons() {
            btnYes.backgroundTintList = itemView.context.getColorStateList(android.R.color.holo_blue_light)
            btnNo.backgroundTintList = itemView.context.getColorStateList(android.R.color.darker_gray)
        }

        /**
         * Set button visual state to reflect user's toggle selection
         * Implements toggle behavior where only one button can be active
         * 
         * @param yesSelected true if Yes button is selected, false if No button is selected
         */
        private fun setButtonState(yesSelected: Boolean) {
            if (yesSelected) {
                // Highlight Yes button, dim No button
                btnYes.backgroundTintList = itemView.context.getColorStateList(android.R.color.holo_green_dark)
                btnNo.backgroundTintList = itemView.context.getColorStateList(android.R.color.darker_gray)
            } else {
                // Highlight No button, reset Yes button
                btnYes.backgroundTintList = itemView.context.getColorStateList(android.R.color.holo_blue_light)
                btnNo.backgroundTintList = itemView.context.getColorStateList(android.R.color.holo_red_dark)
            }
        }
    }

    private class FaceDiffCallback : DiffUtil.ItemCallback<DetectedFaceItem>() {
        override fun areItemsTheSame(
            oldItem: DetectedFaceItem,
            newItem: DetectedFaceItem
        ): Boolean {
            return oldItem.face.boundingBox == newItem.face.boundingBox
        }

        override fun areContentsTheSame(
            oldItem: DetectedFaceItem,
            newItem: DetectedFaceItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Data class for detected face items with integrated confirmation state
 * 
 * REPLACES: Previous system where faces and confirmations were handled separately
 * - Eliminates need for separate ConfirmationFaceItem class
 * - Consolidates face data and confirmation state in single item
 * - Enables unified display and interaction within single RecyclerView
 * 
 * @param face Face detection result with embedding and cropped bitmap
 * @param similarity Individual similarity score (0.0 to 1.0) compared to reference face
 * @param confirmationState Current state in three-tier system (AUTO_MATCH, AUTO_REJECT, PENDING, USER_YES, USER_NO)
 */
data class DetectedFaceItem(
    val face: FaceDetectionResult,
    val similarity: Float,
    val confirmationState: DetectedFaceAdapter.ConfirmationState
)
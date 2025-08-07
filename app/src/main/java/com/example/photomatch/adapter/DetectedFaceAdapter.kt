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

/**
 * Adapter for displaying detected faces with their similarity scores
 */
class DetectedFaceAdapter(
    private val onFaceClick: (FaceDetectionResult, Float) -> Unit = { _, _ -> }
) : ListAdapter<Pair<FaceDetectionResult, Float>, DetectedFaceAdapter.FaceViewHolder>(FaceDiffCallback()) {

    companion object {
        private const val MATCH_THRESHOLD = 0.55f
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detected_face, parent, false)
        return FaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: FaceViewHolder, position: Int) {
        val (face, similarity) = getItem(position)
        holder.bind(face, similarity, onFaceClick)
    }

    class FaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivDetectedFace: ImageView = itemView.findViewById(R.id.ivDetectedFace)
        private val tvSimilarityScore: TextView = itemView.findViewById(R.id.tvSimilarityScore)
        private val tvMatchStatus: TextView = itemView.findViewById(R.id.tvMatchStatus)

        fun bind(
            face: FaceDetectionResult,
            similarity: Float,
            onFaceClick: (FaceDetectionResult, Float) -> Unit
        ) {
            // Display the cropped face bitmap
            ivDetectedFace.setImageBitmap(face.croppedFaceBitmap)

            // Format and display similarity score as percentage
            val percentage = (similarity * 100).toInt()
            tvSimilarityScore.text = "$percentage%"

            // Determine match status and color
            val isMatch = similarity > MATCH_THRESHOLD
            if (isMatch) {
                tvMatchStatus.text = "MATCH"
                tvMatchStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
                tvSimilarityScore.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                tvMatchStatus.text = "NO MATCH"
                tvMatchStatus.setTextColor(Color.parseColor("#F44336")) // Red
                tvSimilarityScore.setTextColor(Color.parseColor("#F44336"))
            }

            // Set click listener
            itemView.setOnClickListener {
                onFaceClick(face, similarity)
            }
        }
    }

    private class FaceDiffCallback : DiffUtil.ItemCallback<Pair<FaceDetectionResult, Float>>() {
        override fun areItemsTheSame(
            oldItem: Pair<FaceDetectionResult, Float>,
            newItem: Pair<FaceDetectionResult, Float>
        ): Boolean {
            return oldItem.first.boundingBox == newItem.first.boundingBox
        }

        override fun areContentsTheSame(
            oldItem: Pair<FaceDetectionResult, Float>,
            newItem: Pair<FaceDetectionResult, Float>
        ): Boolean {
            return oldItem.first == newItem.first && oldItem.second == newItem.second
        }
    }
}
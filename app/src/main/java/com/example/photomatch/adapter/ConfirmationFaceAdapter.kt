package com.example.photomatch.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.photomatch.data.FaceDetectionResult
import com.example.photomatch.databinding.ItemConfirmationFaceBinding

/**
 * Adapter for displaying faces requiring user confirmation (40-60% similarity)
 */
class ConfirmationFaceAdapter : ListAdapter<ConfirmationFaceItem, ConfirmationFaceAdapter.ConfirmationFaceViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<ConfirmationFaceItem>() {
        override fun areItemsTheSame(oldItem: ConfirmationFaceItem, newItem: ConfirmationFaceItem): Boolean {
            return oldItem.face.boundingBox == newItem.face.boundingBox
        }

        override fun areContentsTheSame(oldItem: ConfirmationFaceItem, newItem: ConfirmationFaceItem): Boolean {
            return oldItem == newItem
        }
    }

    inner class ConfirmationFaceViewHolder(private val binding: ItemConfirmationFaceBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: ConfirmationFaceItem) {
            val face = item.face
            val personName = item.personName
            
            // Load face image
            Glide.with(binding.root.context)
                .load(face.croppedFaceBitmap)
                .centerCrop()
                .into(binding.ivFace)
            
            // Set similarity percentage
            val percentage = (face.similarityToReference * 100).toInt()
            binding.tvSimilarity.text = "$percentage%"
            
            // Set checkbox text
            binding.cbConfirmFace.text = "Is this $personName?"
            
            // Set checkbox state
            binding.cbConfirmFace.isChecked = item.isConfirmed
            
            // Handle checkbox changes
            binding.cbConfirmFace.setOnCheckedChangeListener { _, isChecked ->
                item.isConfirmed = isChecked
            }
            
            // Handle card click to toggle checkbox
            binding.root.setOnClickListener {
                binding.cbConfirmFace.isChecked = !binding.cbConfirmFace.isChecked
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfirmationFaceViewHolder {
        val binding = ItemConfirmationFaceBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ConfirmationFaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConfirmationFaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    fun getConfirmedFaces(): List<FaceDetectionResult> {
        return currentList.filter { it.isConfirmed }.map { it.face }
    }
}

/**
 * Data class for confirmation face items
 */
data class ConfirmationFaceItem(
    val face: FaceDetectionResult,
    val personName: String,
    var isConfirmed: Boolean = false
)
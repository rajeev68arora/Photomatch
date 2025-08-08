package com.example.photomatch.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.photomatch.R
import com.example.photomatch.data.database.Person
import com.example.photomatch.util.FaceMatchingService
import com.google.android.material.button.MaterialButton

/**
 * Dialog for confirming potential duplicate faces before adding new person
 * 
 * PURPOSE:
 * - Show user potentially similar faces found in database
 * - Allow user to choose between using existing person or adding new one
 * - Display similarity percentages for informed decision making
 * - Prevent accidental duplicate entries in people database
 * 
 * USER WORKFLOW:
 * 1. User attempts to add new person
 * 2. System detects potential duplicates (>85% similarity)
 * 3. Dialog shows similar faces with similarity percentages
 * 4. User chooses: "Use [Existing Name]", "Add as New Person", or "Cancel"
 * 
 * UI DESIGN:
 * - Material Design dialog with clean layout
 * - Horizontal RecyclerView showing similar face thumbnails
 * - Clear action buttons for user decision
 * - Similarity percentages for transparency
 */
class DuplicateFaceDialog : DialogFragment() {
    
    interface DuplicateFaceDialogListener {
        fun onUseExistingPerson(person: Person)
        fun onAddAsNewPerson()
        fun onCancel()
    }
    
    private var listener: DuplicateFaceDialogListener? = null
    private var duplicateCandidates: List<FaceMatchingService.DuplicateCandidate> = emptyList()
    
    companion object {
        private const val ARG_CANDIDATES = "candidates"
        
        /**
         * Create new dialog instance with duplicate candidates
         * 
         * @param candidates List of potential duplicate candidates
         * @return Configured dialog instance
         */
        fun newInstance(candidates: List<FaceMatchingService.DuplicateCandidate>): DuplicateFaceDialog {
            val dialog = DuplicateFaceDialog()
            val args = Bundle().apply {
                // Convert candidates to a serializable format for Bundle
                putSerializable(ARG_CANDIDATES, ArrayList(candidates))
            }
            dialog.arguments = args
            return dialog
        }
    }
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? DuplicateFaceDialogListener
            ?: throw IllegalArgumentException("Activity must implement DuplicateFaceDialogListener")
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @Suppress("UNCHECKED_CAST")
        duplicateCandidates = arguments?.getSerializable(ARG_CANDIDATES) as? List<FaceMatchingService.DuplicateCandidate> ?: emptyList()
        
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_duplicate_face, null)
        
        setupViews(view)
        
        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false) // Force user to make a decision
            .create()
    }
    
    private fun setupViews(view: View) {
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val rvSimilarFaces = view.findViewById<RecyclerView>(R.id.rvSimilarFaces)
        val btnUseExisting = view.findViewById<MaterialButton>(R.id.btnUseExisting)
        val btnAddNew = view.findViewById<MaterialButton>(R.id.btnAddNew)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        
        // Set title and message based on confidence level
        val highConfidenceCandidate = duplicateCandidates.firstOrNull { 
            it.confidence == FaceMatchingService.DuplicateConfidence.HIGH 
        }\n        \n        if (highConfidenceCandidate != null) {\n            tvTitle.text = \"Person Already Exists\"\n            tvMessage.text = \"We found a very similar face (${(highConfidenceCandidate.similarity * 100).toInt()}% match) in your database. This might be the same person.\"\n            btnUseExisting.text = \"Use ${highConfidenceCandidate.person.fullName}\"\n        } else {\n            tvTitle.text = \"Similar Faces Found\"\n            tvMessage.text = \"We found ${duplicateCandidates.size} similar face(s) in your database. Please confirm if this is a new person or if you want to use an existing one.\"\n            \n            // If multiple candidates, use generic text\n            if (duplicateCandidates.size == 1) {\n                btnUseExisting.text = \"Use ${duplicateCandidates.first().person.fullName}\"\n            } else {\n                btnUseExisting.text = \"Select Existing\"\n            }\n        }\n        \n        // Setup RecyclerView with similar faces\n        val adapter = DuplicateFaceAdapter(duplicateCandidates) { selectedPerson ->\n            listener?.onUseExistingPerson(selectedPerson)\n            dismiss()\n        }\n        \n        rvSimilarFaces.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)\n        rvSimilarFaces.adapter = adapter\n        \n        // Setup button click listeners\n        btnUseExisting.setOnClickListener {\n            if (duplicateCandidates.size == 1) {\n                // Single candidate - use it directly\n                listener?.onUseExistingPerson(duplicateCandidates.first().person)\n                dismiss()\n            } else {\n                // Multiple candidates - let user select from RecyclerView\n                // (Selection is handled by adapter click listener above)\n            }\n        }\n        \n        btnAddNew.setOnClickListener {\n            listener?.onAddAsNewPerson()\n            dismiss()\n        }\n        \n        btnCancel.setOnClickListener {\n            listener?.onCancel()\n            dismiss()\n        }\n    }\n}\n\n/**\n * RecyclerView adapter for displaying duplicate face candidates\n */\nclass DuplicateFaceAdapter(\n    private val candidates: List<FaceMatchingService.DuplicateCandidate>,\n    private val onPersonSelected: (Person) -> Unit\n) : RecyclerView.Adapter<DuplicateFaceAdapter.ViewHolder>() {\n    \n    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {\n        val ivFace: ImageView = itemView.findViewById(R.id.ivFace)\n        val tvName: TextView = itemView.findViewById(R.id.tvName)\n        val tvSimilarity: TextView = itemView.findViewById(R.id.tvSimilarity)\n    }\n    \n    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {\n        val view = LayoutInflater.from(parent.context)\n            .inflate(R.layout.item_duplicate_face, parent, false)\n        return ViewHolder(view)\n    }\n    \n    override fun onBindViewHolder(holder: ViewHolder, position: Int) {\n        val candidate = candidates[position]\n        val person = candidate.person\n        \n        // Display face thumbnail\n        holder.ivFace.setImageBitmap(person.faceBitmap)\n        \n        // Display person name\n        holder.tvName.text = person.fullName\n        \n        // Display similarity percentage\n        val similarityPercent = (candidate.similarity * 100).toInt()\n        holder.tvSimilarity.text = \"$similarityPercent% match\"\n        \n        // Set click listener\n        holder.itemView.setOnClickListener {\n            onPersonSelected(person)\n        }\n        \n        // Highlight high confidence matches\n        if (candidate.confidence == FaceMatchingService.DuplicateConfidence.HIGH) {\n            holder.itemView.setBackgroundResource(R.drawable.face_border) // Highlight border\n        }\n    }\n    \n    override fun getItemCount(): Int = candidates.size\n}"
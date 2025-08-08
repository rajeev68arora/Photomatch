package com.example.photomatch.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.photomatch.R
import com.example.photomatch.data.database.Person
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for displaying saved people in a grid layout
 * 
 * PURPOSE:
 * - Display people with face thumbnails and names in grid format
 * - Handle person selection and deletion interactions
 * - Show usage information (last used date)
 * - Provide visual feedback for user interactions
 * 
 * FEATURES:
 * - Grid-friendly card-based layout
 * - Face thumbnail display with fallback handling
 * - Person name and last used date
 * - Click to select, long press to delete
 * - Recently used indicators
 * 
 * INTERACTION PATTERNS:
 * - Single tap: Select person for face matching
 * - Long press: Show delete confirmation
 * - Visual feedback on touch events
 */
class PersonSelectionAdapter(
    private var people: List<Person>,
    private val onPersonSelected: (Person) -> Unit,
    private val onPersonDeleted: (Person) -> Unit
) : RecyclerView.Adapter<PersonSelectionAdapter.PersonViewHolder>() {
    
    companion object {
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }
    
    class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivFace: ImageView = itemView.findViewById(R.id.ivFace)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvLastUsed: TextView = itemView.findViewById(R.id.tvLastUsed)
        val viewRecentIndicator: View = itemView.findViewById(R.id.viewRecentIndicator)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person_selection, parent, false)
        return PersonViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        val person = people[position]
        
        // Display face thumbnail
        holder.ivFace.setImageBitmap(person.faceBitmap)
        
        // Display person name
        holder.tvName.text = person.fullName
        
        // Display last used date
        val lastUsedDate = Date(person.lastUsedTimestamp)
        holder.tvLastUsed.text = "Last used: ${dateFormat.format(lastUsedDate)}"
        
        // Show recent indicator for people used within last 7 days
        val isRecent = System.currentTimeMillis() - person.lastUsedTimestamp < 7 * 24 * 60 * 60 * 1000L
        holder.viewRecentIndicator.visibility = if (isRecent) View.VISIBLE else View.GONE
        
        // Set click listeners
        holder.itemView.setOnClickListener {
            onPersonSelected(person)
        }
        
        holder.itemView.setOnLongClickListener {
            onPersonDeleted(person)
            true
        }
    }
    
    override fun getItemCount(): Int = people.size
    
    /**
     * Update the list of people and refresh the RecyclerView
     * 
     * @param newPeople Updated list of people
     */
    fun updatePeople(newPeople: List<Person>) {
        people = newPeople
        notifyDataSetChanged()
    }
}
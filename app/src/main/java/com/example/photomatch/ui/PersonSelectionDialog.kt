package com.example.photomatch.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.photomatch.R
import com.example.photomatch.data.database.Person
import com.example.photomatch.data.database.PeopleRepository
import com.example.photomatch.adapter.PersonSelectionAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

/**
 * Dialog for selecting a person from the saved people database
 * 
 * PURPOSE:
 * - Show all saved people with face thumbnails in a grid layout
 * - Allow searching/filtering by name
 * - Provide option to add new person
 * - Enable person management (delete with confirmation)
 * 
 * FEATURES:
 * - Grid layout with face thumbnails and names
 * - Real-time search filtering
 * - Recently used people shown first
 * - Add new person option
 * - Long press to delete person (with confirmation)
 * 
 * USER WORKFLOW:
 * 1. Dialog opens showing all saved people
 * 2. User can search by typing in search field
 * 3. User selects existing person or chooses to add new
 * 4. Dialog returns selected person to calling activity
 */
class PersonSelectionDialog : DialogFragment() {
    
    interface PersonSelectionListener {
        fun onPersonSelected(person: Person)
        fun onAddNewPersonRequested()
        fun getPeopleRepository(): PeopleRepository  // NEW: Access to shared repository
    }
    
    private var listener: PersonSelectionListener? = null
    private lateinit var peopleRepository: PeopleRepository
    private lateinit var adapter: PersonSelectionAdapter
    private lateinit var rvPeople: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var tvEmptyState: TextView
    
    private var allPeople: List<Person> = emptyList()
    private var filteredPeople: List<Person> = emptyList()
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? PersonSelectionListener
            ?: throw IllegalArgumentException("Activity must implement PersonSelectionListener")
        
        // NEW: Use shared repository instance from parent activity
        peopleRepository = listener!!.getPeopleRepository()
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_person_selection, null)
        
        setupViews(view)
        loadPeople()
        
        return AlertDialog.Builder(requireContext())
            .setTitle("Select Person")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .create()
    }
    
    private fun setupViews(view: View) {
        etSearch = view.findViewById(R.id.etSearch)
        rvPeople = view.findViewById(R.id.rvPeople)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        val fabAddNew = view.findViewById<FloatingActionButton>(R.id.fabAddNew)
        
        // Setup RecyclerView with grid layout
        adapter = PersonSelectionAdapter(
            people = filteredPeople,
            onPersonSelected = { person ->
                // Update last used timestamp
                lifecycleScope.launch {
                    peopleRepository.updateLastUsed(person.id)
                }
                listener?.onPersonSelected(person)
                dismiss()
            },
            onPersonDeleted = { person ->
                showDeleteConfirmation(person)
            }
        )
        
        rvPeople.layoutManager = GridLayoutManager(context, 2) // 2 columns
        rvPeople.adapter = adapter
        
        // Setup search functionality
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                filterPeople(s.toString())
            }
        })
        
        // Setup add new person button
        fabAddNew.setOnClickListener {
            listener?.onAddNewPersonRequested()
            dismiss()
        }
    }
    
    private fun loadPeople() {
        lifecycleScope.launch {
            try {
                allPeople = peopleRepository.getAllPeople()
                filteredPeople = allPeople
                adapter.updatePeople(filteredPeople)
                updateEmptyState()
            } catch (e: Exception) {
                // Handle error - show empty state
                allPeople = emptyList()
                filteredPeople = emptyList()
                adapter.updatePeople(filteredPeople)
                updateEmptyState()
            }
        }
    }
    
    private fun filterPeople(query: String) {
        filteredPeople = if (query.isBlank()) {
            allPeople
        } else {
            allPeople.filter { person ->
                person.firstName.contains(query, ignoreCase = true) ||
                person.lastName.contains(query, ignoreCase = true) ||
                person.fullName.contains(query, ignoreCase = true)
            }
        }
        
        adapter.updatePeople(filteredPeople)
        updateEmptyState()
    }
    
    private fun updateEmptyState() {
        if (filteredPeople.isEmpty()) {
            rvPeople.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
            
            tvEmptyState.text = if (allPeople.isEmpty()) {
                "No saved people found.\nTap + to add your first person!"
            } else {
                "No people match your search."
            }
        } else {
            rvPeople.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }
    }
    
    private fun showDeleteConfirmation(person: Person) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Person")
            .setMessage("Are you sure you want to delete ${person.fullName}? This will also remove all their match records.")
            .setPositiveButton("Delete") { _, _ ->
                deletePerson(person)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deletePerson(person: Person) {
        lifecycleScope.launch {
            try {
                peopleRepository.deletePerson(person.id)
                // Reload the list
                loadPeople()
            } catch (e: Exception) {
                // Handle error - could show a toast or snackbar
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // NEW: Don't close shared repository - parent activity manages lifecycle
    }
}
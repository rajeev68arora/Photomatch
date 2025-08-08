package com.example.photomatch.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.photomatch.R

/**
 * Dialog for entering person's first and last name
 */
class PersonNameDialog : DialogFragment() {
    
    interface PersonNameListener {
        fun onPersonNameEntered(firstName: String, lastName: String)
        fun onPersonNameCanceled()
    }
    
    private var listener: PersonNameListener? = null
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as PersonNameListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement PersonNameListener")
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_person_name, null)
        
        val etFirstName = dialogView.findViewById<EditText>(R.id.etFirstName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etLastName)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Enter Person's Name")
            .setMessage("Please enter the first and last name of the person you want to find in your photos")
            .setView(dialogView)
            .create()
        
        btnConfirm.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            
            if (firstName.isBlank() || lastName.isBlank()) {
                Toast.makeText(context, "Please enter both first and last name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            listener?.onPersonNameEntered(firstName, lastName)
            dismiss()
        }
        
        btnCancel.setOnClickListener {
            listener?.onPersonNameCanceled()
            dismiss()
        }
        
        // Set focus on first name field
        etFirstName.requestFocus()
        
        return dialog
    }
    
    companion object {
        fun newInstance(): PersonNameDialog {
            return PersonNameDialog()
        }
    }
}
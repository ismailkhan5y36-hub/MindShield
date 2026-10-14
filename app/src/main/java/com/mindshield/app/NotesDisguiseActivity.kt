package com.mindshield.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class NotesDisguiseActivity : AppCompatActivity() {

    private lateinit var camouflageManager: CamouflageManager
    private var titleTapCount = 0
    private var lastTapTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes_disguise)

        camouflageManager = CamouflageManager.getInstance(this)

        val etNotePad = findViewById<EditText>(R.id.etNotePad)
        val tvNotesAppTitle = findViewById<TextView>(R.id.tvNotesAppTitle)
        val btnSecretUnlock = findViewById<ImageButton>(R.id.btnSecretUnlock)
        val tvNotesSavedStatus = findViewById<TextView>(R.id.tvNotesSavedStatus)

        etNotePad.setText(camouflageManager.savedNotes)

        etNotePad.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                camouflageManager.savedNotes = s?.toString() ?: ""
                tvNotesSavedStatus.text = "Saved locally"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSecretUnlock.setOnClickListener {
            promptForPin()
        }

        // Tapping title 3 times also prompts for PIN
        tvNotesAppTitle.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < 800) {
                titleTapCount++
                if (titleTapCount >= 3) {
                    titleTapCount = 0
                    promptForPin()
                }
            } else {
                titleTapCount = 1
            }
            lastTapTime = now
        }
    }

    private fun promptForPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 4-digit PIN"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Discipline Shield")
            .setMessage("Enter security PIN to access MindShield:")
            .setView(input)
            .setPositiveButton("Unlock") { dialog, _ ->
                val entered = input.text.toString().trim()
                if (camouflageManager.verifyPin(entered)) {
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(mainIntent)
                    finish()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}

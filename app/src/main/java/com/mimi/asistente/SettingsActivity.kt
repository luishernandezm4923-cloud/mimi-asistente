package com.mimi.asistente

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var contactsRepository: ContactsRepository
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        contactsRepository = ContactsRepository(applicationContext)
        listContainer = findViewById(R.id.contactsListContainer)

        val nameInput = findViewById<EditText>(R.id.nameInput)
        val phoneInput = findViewById<EditText>(R.id.phoneInput)
        val emergencyCheck = findViewById<CheckBox>(R.id.emergencyCheck)
        val addButton = findViewById<Button>(R.id.addButton)
        val backButton = findViewById<Button>(R.id.backButton)

        addButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Complete el nombre y el número", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!phone.startsWith("+")) {
                Toast.makeText(this, "Use el número con código de país, ej. +51987654321", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            contactsRepository.add(name, phone, emergencyCheck.isChecked)
            nameInput.text.clear()
            phoneInput.text.clear()
            emergencyCheck.isChecked = false
            refreshList()
            Toast.makeText(this, "Contacto agregado", Toast.LENGTH_SHORT).show()
        }

        backButton.setOnClickListener { finish() }

        refreshList()
    }

    private fun refreshList() {
        listContainer.removeAllViews()
        val contacts = contactsRepository.getAll()

        if (contacts.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Todavía no hay contactos guardados."
                textSize = 15f
                setTextColor(0xFF5C6E68.toInt())
                setPadding(0, 8, 0, 8)
            }
            listContainer.addView(empty)
            return
        }

        contacts.forEach { contact ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val info = TextView(this).apply {
                text = buildString {
                    append(contact.display)
                    append(" — ")
                    append(contact.phone)
                    if (contact.isEmergency) append("  🚨 emergencia")
                }
                textSize = 16f
                setTextColor(0xFF12312B.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val deleteButton = Button(this).apply {
                text = "Borrar"
                textSize = 13f
                setOnClickListener {
                    contactsRepository.remove(contact.display)
                    refreshList()
                }
            }

            row.addView(info)
            row.addView(deleteButton)
            listContainer.addView(row)
        }
    }
}

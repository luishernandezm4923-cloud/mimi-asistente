package com.mimi.asistente

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Contact(
    val name: String,       // en minúsculas, sin tildes, para comparar con la transcripción de voz
    val display: String,
    val phone: String,
    val isEmergency: Boolean = false
)

/**
 * Guarda los contactos directamente en el celular del adulto mayor (SharedPreferences),
 * para que un familiar los pueda añadir desde la pantalla de Configuración sin
 * tener que editar código ni recompilar la app. Un mismo .apk sirve para todos.
 */
class ContactsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("mimi_contacts", Context.MODE_PRIVATE)

    fun getAll(): List<Contact> {
        val raw = prefs.getString("contacts_json", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Contact(
                name = o.getString("name"),
                display = o.getString("display"),
                phone = o.getString("phone"),
                isEmergency = o.optBoolean("isEmergency", false)
            )
        }
    }

    fun add(display: String, phone: String, isEmergency: Boolean) {
        val current = getAll().toMutableList()
        // Solo puede haber un contacto de emergencia principal a la vez.
        val updated = if (isEmergency) current.map { it.copy(isEmergency = false) } else current
        val newContact = Contact(
            name = normalizeName(display),
            display = display,
            phone = phone,
            isEmergency = isEmergency
        )
        save(updated + newContact)
    }

    fun remove(display: String) {
        save(getAll().filterNot { it.display == display })
    }

    fun findByName(text: String): Contact? = getAll().find { text.contains(it.name) }

    fun emergencyContact(): Contact? = getAll().find { it.isEmergency } ?: getAll().firstOrNull()

    private fun save(contacts: List<Contact>) {
        val arr = JSONArray()
        contacts.forEach { c ->
            val o = JSONObject()
            o.put("name", c.name)
            o.put("display", c.display)
            o.put("phone", c.phone)
            o.put("isEmergency", c.isEmergency)
            arr.put(o)
        }
        prefs.edit().putString("contacts_json", arr.toString()).apply()
    }

    // Quita tildes/espacios para comparar mejor contra la transcripción de voz.
    private fun normalizeName(display: String): String =
        display.trim().lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u")
}

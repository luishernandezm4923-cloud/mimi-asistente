package com.mimi.asistente

/**
 * Interpreta el texto ya transcrito (todo en minúsculas, sin la palabra de activación "mimi")
 * y decide qué acción ejecutar. Separado de Android para poder probarlo con JUnit sin emulador.
 */
class CommandProcessor(
    private val actions: AssistantActions,
    private val contactsRepository: ContactsRepository
) {

    fun process(text: String) {
        val t = text.trim()

        if (Regex("(me cai|me caí|caida|caída|ayuda|emergencia|auxilio)").containsMatchIn(t)) {
            val contact = contactsRepository.emergencyContact()
            if (contact == null) {
                actions.speak("No hay ningún contacto de emergencia guardado. Pida a un familiar que lo configure en la app.")
                return
            }
            actions.speak("Aviso de emergencia. Llamando a ${contact.display} ahora mismo.") {
                actions.callContact(contact)
            }
            return
        }

        if (Regex("(medicina|pastilla|remedio)").containsMatchIn(t)) {
            actions.speak("Preparando su pedido de medicina habitual. Un familiar recibirá la confirmación.")
            actions.requestMedicineOrder()
            return
        }

        if (Regex("(pon|reproduce|reproducir).*(musica|música|cancion|canción)").containsMatchIn(t)) {
            actions.speak("Reproduciendo su música.")
            actions.playMedia()
            return
        }

        if (Regex("(pausa|detener|para).*(musica|música)").containsMatchIn(t)) {
            actions.speak("Pausando la música.")
            actions.pauseMedia()
            return
        }

        if (Regex("(llama|llamar)").containsMatchIn(t)) {
            val contact = contactsRepository.findByName(t)
            if (contact != null) {
                actions.speak("Llamando a ${contact.display}.") {
                    actions.callContact(contact)
                }
            } else {
                actions.speak("No reconocí a esa persona entre sus contactos guardados.")
            }
            return
        }

        actions.speak("No entendí el pedido. Puede decir: pedir medicina, llamar a un familiar, poner música, o me caí.")
    }
}

interface AssistantActions {
    fun speak(text: String, onDone: (() -> Unit)? = null)
    fun callContact(contact: Contact)
    fun requestMedicineOrder()
    fun playMedia()
    fun pauseMedia()
}

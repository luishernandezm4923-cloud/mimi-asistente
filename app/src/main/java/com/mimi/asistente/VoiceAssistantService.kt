package com.mimi.asistente

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.util.Locale

/**
 * Servicio que mantiene a MIMI escuchando en segundo plano y con la pantalla bloqueada.
 * Usa Vosk (offline, sin internet) para detectar la palabra de activación "mimi"
 * y despachar el resto de la frase a CommandProcessor.
 */
class VoiceAssistantService : Service(), AssistantActions {

    companion object {
        const val CHANNEL_ID = "mimi_channel"
        const val NOTIF_ID = 1
        const val TAG = "MimiVoiceService"
    }

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private lateinit var tts: TextToSpeech
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var contactsRepository: ContactsRepository
    private lateinit var audioManager: AudioManager

    override fun onCreate() {
        super.onCreate()
        contactsRepository = ContactsRepository(applicationContext)
        commandProcessor = CommandProcessor(this, contactsRepository)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts.language = Locale("es", "PE")
        }
        startForegroundWithNotification()
        loadModel()
    }

    private fun startForegroundWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "MIMI activo", NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MIMI está escuchando")
            .setContentText("Diga \"Oye MIMI\" seguido de su pedido")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    /** El modelo de Vosk en español ("model-es") debe copiarse a app/src/main/assets/model-es
     *  Descarga: https://alphacephei.com/vosk/models (elegir "vosk-model-small-es-0.42" para un
     *  tamaño liviano, ~40 MB, adecuado para un piloto). Vosk lo descomprime una sola vez. */
    private fun loadModel() {
        StorageService.unpack(this, "model-es", "model",
            { unpackedModel ->
                model = unpackedModel
                startListening()
            },
            { exception ->
                Log.e(TAG, "No se pudo cargar el modelo de voz: ${exception.message}")
            }
        )
    }

    private fun startListening() {
        val m = model ?: return
        try {
            val rec = Recognizer(m, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(recognitionListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando escucha: ${e.message}")
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) { /* no-op: solo actuamos sobre resultados finales */ }

        override fun onResult(hypothesis: String?) {
            val text = extractText(hypothesis).lowercase(Locale("es"))
            if (text.isBlank()) return
            Log.d(TAG, "Reconocido: $text")

            if (text.contains("mimi")) {
                val afterWake = text.substringAfter("mimi").trim()
                if (afterWake.length > 2) {
                    commandProcessor.process(afterWake)
                } else {
                    speak("Dígame.")
                }
            }
        }

        override fun onFinalResult(hypothesis: String?) { /* no-op, usamos onResult */ }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Error de reconocimiento: ${exception?.message}")
            restartListening()
        }

        override fun onTimeout() {
            restartListening()
        }
    }

    private fun extractText(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return try { JSONObject(json).optString("text", "") } catch (e: Exception) { "" }
    }

    private fun restartListening() {
        speechService?.stop()
        startListening()
    }

    // ---------- AssistantActions ----------

    override fun speak(text: String, onDone: (() -> Unit)?) {
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            override fun onError(utteranceId: String?) {}
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mimi_utterance")
    }

    override fun callContact(contact: Contact) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.phone}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: SecurityException) {
            Log.e(TAG, "Falta permiso CALL_PHONE: ${e.message}")
        }
    }

    override fun requestMedicineOrder() {
        // TODO fase 2: conectar con el backend/Google Sheets de NutriCheck-style para registrar el pedido real.
        Log.d(TAG, "Pedido de medicina registrado (simulado en el piloto).")
    }

    override fun playMedia() {
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    override fun pauseMedia() {
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    /** Envía una señal genérica de reproducción/pausa a la app de música activa en el sistema,
     *  sin necesidad de saber cuál está instalada (Samsung Music, Spotify, etc.). */
    private fun sendMediaKey(keyCode: Int) {
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    // ---------- Ciclo de vida del servicio ----------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        tts.shutdown()
        super.onDestroy()
    }
}

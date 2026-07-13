package com.mimi.asistente

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

        // Difusiones locales para que MainActivity muestre en pantalla lo que MIMI va escuchando.
        const val ACTION_STATUS = "com.mimi.asistente.STATUS"
        const val ACTION_TRANSCRIPT = "com.mimi.asistente.TRANSCRIPT"
        const val EXTRA_TEXT = "text"
    }

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private lateinit var tts: TextToSpeech
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var contactsRepository: ContactsRepository
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        contactsRepository = ContactsRepository(applicationContext)
        commandProcessor = CommandProcessor(this, contactsRepository)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale("es", "PE"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    broadcastStatus("⚠️ Falta el paquete de voz en español. Vaya a Ajustes del celular > Accesibilidad > Salida de texto a voz, e instale los datos de voz en español.")
                }
            } else {
                broadcastStatus("⚠️ No se pudo iniciar la síntesis de voz (TTS) del celular.")
            }
        }

        startForegroundWithNotification("Cargando el modelo de voz…")
        broadcastStatus("Cargando el modelo de voz…")
        loadModel()
    }

    private fun startForegroundWithNotification(text: String) {
        val channel = NotificationChannel(
            CHANNEL_ID, "MIMI activo", NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
        val notification = buildNotification(text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MIMI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    /** Actualiza la notificación (visible incluso con la app cerrada) con lo último que MIMI escuchó,
     *  para poder verificar que sí está funcionando sin necesidad de tener la app abierta. */
    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun broadcastStatus(text: String) {
        Log.d(TAG, "STATUS: $text")
        updateNotification(text)
        val intent = Intent(ACTION_STATUS).putExtra(EXTRA_TEXT, text)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastTranscript(text: String) {
        val intent = Intent(ACTION_TRANSCRIPT).putExtra(EXTRA_TEXT, text)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /** El modelo de Vosk en español ("model-es") debe estar en app/src/main/assets/model-es
     *  (el workflow de GitHub Actions lo descarga automáticamente en cada compilación). */
    private fun loadModel() {
        StorageService.unpack(this, "model-es", "model",
            { unpackedModel ->
                model = unpackedModel
                broadcastStatus("Modelo cargado. Escuchando… diga \"Oye MIMI\"")
                startListening()
            },
            { exception ->
                Log.e(TAG, "No se pudo cargar el modelo de voz: ${exception.message}", exception)
                broadcastStatus("❌ Error al cargar el modelo de voz: ${exception.message}")
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
            Log.e(TAG, "Error iniciando escucha: ${e.message}", e)
            broadcastStatus("❌ Error iniciando el micrófono: ${e.message}")
        }
    }

    private val recognitionListener = object : RecognitionListener {

        // Se dispara mientras la persona sigue hablando, antes de que termine la frase.
        // Lo mostramos en pantalla en vivo para confirmar que el micrófono SÍ está captando audio.
        override fun onPartialResult(hypothesis: String?) {
            val text = extractText(hypothesis)
            if (text.isNotBlank()) broadcastTranscript(text)
        }

        override fun onResult(hypothesis: String?) {
            val text = extractText(hypothesis).lowercase(Locale("es"))
            if (text.isBlank()) return
            Log.d(TAG, "Reconocido (final): $text")
            broadcastTranscript(text)
            updateNotification("Escuché: \"$text\"")

            // Tolerante a variaciones comunes con las que Vosk puede transcribir "mimi"
            // (mimí, emi, mimy) para reducir falsos negativos con distintos acentos.
            val wakeWordRegex = Regex("\\b(mimi|mimí|emi|mimy)\\b")
            val match = wakeWordRegex.find(text)
            if (match != null) {
                val afterWake = text.substring(match.range.last + 1).trim()
                if (afterWake.length > 2) {
                    commandProcessor.process(afterWake)
                } else {
                    speak("Dígame.")
                }
            }
        }

        override fun onFinalResult(hypothesis: String?) { /* no-op, usamos onResult */ }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Error de reconocimiento: ${exception?.message}", exception)
            broadcastStatus("⚠️ Error de reconocimiento, reiniciando…")
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
        broadcastStatus("🔊 $text")
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
            broadcastStatus("❌ Falta el permiso de llamadas.")
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

    /** Envía una señal genérica de reproducción/pausa a la app de música activa en el sistema.
     *  Si ninguna app de música tiene una pista cargada, no hay nada que responda a esta señal:
     *  MIMI no incluye un reproductor propio, solo retoma lo que ya esté sonando/pausado. */
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

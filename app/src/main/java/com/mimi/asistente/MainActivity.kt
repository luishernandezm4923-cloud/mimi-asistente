package com.mimi.asistente

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }.toTypedArray()

    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView

    // Recibe en vivo lo que el servicio de voz va escuchando y su estado (cargando, error, etc.)
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(VoiceAssistantService.EXTRA_TEXT) ?: return
            statusText.text = text
        }
    }

    private val transcriptReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(VoiceAssistantService.EXTRA_TEXT) ?: return
            transcriptText.text = "Escuchando: \"$text\""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val contactsRepository = ContactsRepository(applicationContext)

        statusText = findViewById(R.id.statusText)
        transcriptText = findViewById(R.id.transcriptText)
        val startButton = findViewById<Button>(R.id.startButton)
        val emergencyButton = findViewById<Button>(R.id.emergencyButton)
        val settingsButton = findViewById<Button>(R.id.settingsButton)

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        startButton.setOnClickListener {
            if (hasAllPermissions()) {
                startVoiceService()
            } else {
                ActivityCompat.requestPermissions(this, requiredPermissions, 100)
            }
        }

        emergencyButton.setOnClickListener {
            val contact = contactsRepository.emergencyContact()
            if (contact == null) {
                Toast.makeText(this, "Primero agregue un contacto de emergencia en Configuración", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.phone}"))
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
                startActivity(intent)
            } else {
                ActivityCompat.requestPermissions(this, requiredPermissions, 100)
            }
        }

        if (hasAllPermissions()) {
            startVoiceService()
        }
    }

    override fun onStart() {
        super.onStart()
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.registerReceiver(statusReceiver, IntentFilter(VoiceAssistantService.ACTION_STATUS))
        lbm.registerReceiver(transcriptReceiver, IntentFilter(VoiceAssistantService.ACTION_TRANSCRIPT))
    }

    override fun onStop() {
        super.onStop()
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.unregisterReceiver(statusReceiver)
        lbm.unregisterReceiver(transcriptReceiver)
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasAllPermissions()) startVoiceService()
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

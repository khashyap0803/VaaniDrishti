package com.example.cash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var resultTextView: TextView
    private val cameraPermissionRequestCode = 100
    private var ttsReady = false

    // Activity Result API launcher for camera
    private val cameraActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { handleIntentData(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide default title

        // Initialize TTS with enhanced settings
        initializeTTS()

        resultTextView = findViewById(R.id.resultText)

        val captureButton: Button = findViewById(R.id.captureButton)
        captureButton.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }

        // Check if started with results
        intent?.let { handleIntentData(it) }
    }

    private fun initializeTTS() {
        tts = TextToSpeech(this, this)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Speech started
            }

            override fun onDone(utteranceId: String?) {
                // Speech completed
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
            override fun onError(utteranceId: String?) {
                // Error occurred
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "TTS error occurred", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            cameraPermissionRequestCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required to detect currency",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        cameraActivityLauncher.launch(intent)
    }

    private fun handleIntentData(intent: Intent) {
        val resultText = intent.getStringExtra("RESULT_TEXT")
        resultText?.let { text ->
            resultTextView.text = text

            // Format text for better speech clarity
            val speechText = formatTextForSpeech(text)
            speak(speechText)
        }
    }

    private fun formatTextForSpeech(text: String): String {
        // Replace "rupees" with "Indian rupees" for clarity
        var formattedText = text

        // Format currency for better pronunciation
        formattedText = formattedText.replace("2000 rupees", "two thousand Indian rupees")
        formattedText = formattedText.replace("500 rupees", "five hundred Indian rupees")
        formattedText = formattedText.replace("200 rupees", "two hundred Indian rupees")
        formattedText = formattedText.replace("100 rupees", "one hundred Indian rupees")
        formattedText = formattedText.replace("50 rupees", "fifty Indian rupees")

        // Add pauses for better speech clarity
        formattedText = formattedText.replace(" detected with ", ", detected with ")

        return formattedText
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentData(intent)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language for TTS
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported for TTS", Toast.LENGTH_SHORT).show()
            } else {
                ttsReady = true

                // Set speech rate slightly slower for better clarity
                tts.setSpeechRate(0.85f)

                // Set pitch slightly higher
                tts.setPitch(1.1f)

                // Check if we need to announce anything immediately
                intent?.let { handleIntentData(it) }
            }
        } else {
            Toast.makeText(this, "TTS initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            // First announcement - flush any previous speech
            val utteranceId = "currencyAnnouncement"
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

            // Add a pause between announcements
            tts.playSilentUtterance(1500, TextToSpeech.QUEUE_ADD, "pause")

            // Repeat the announcement
            val repeatUtteranceId = "repeatCurrencyAnnouncement"
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, repeatUtteranceId)
        } else {
            // Queue for when TTS is ready
            Toast.makeText(this, "Preparing speech...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
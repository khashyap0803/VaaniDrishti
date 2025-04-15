package com.example.cash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
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

        // Initialize TTS
        tts = TextToSpeech(this, this)

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
            speak(formatTextForSpeech(text))
        }
    }

    private fun formatTextForSpeech(text: String): String {
        var formattedText = text
        formattedText = formattedText.replace("2000_rupees", "two thousand Indian rupees")
        formattedText = formattedText.replace("500_rupees", "five hundred Indian rupees")
        formattedText = formattedText.replace("200_rupees", "two hundred Indian rupees")
        formattedText = formattedText.replace("100_rupees", "one hundred Indian rupees")
        formattedText = formattedText.replace("50_rupees", "fifty Indian rupees")
        formattedText = formattedText.replace("20_rupees", "twenty Indian rupees")
        formattedText = formattedText.replace("10_rupees", "ten Indian rupees")
        formattedText = formattedText.replace("no_currency", "No currency detected")
        formattedText = formattedText.replace(" detected with ", ", detected with ")
        return formattedText
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported for TTS", Toast.LENGTH_SHORT).show()
            } else {
                ttsReady = true
                tts.setSpeechRate(0.85f)
                tts.setPitch(1.1f)
                intent?.let { handleIntentData(it) }
            }
        } else {
            Toast.makeText(this, "TTS initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "currencyAnnouncement")
        } else {
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
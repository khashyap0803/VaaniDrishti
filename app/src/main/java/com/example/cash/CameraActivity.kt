package com.example.cash

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.camera2.Camera2Config
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import androidx.core.graphics.scale
import kotlin.math.abs
import kotlin.math.min

class CameraActivity : AppCompatActivity(), CameraXConfig.Provider {
    private lateinit var cameraExecutor: java.util.concurrent.ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var timerText: TextView
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var countDownTimer: CountDownTimer? = null
    private var autoCapture = false
    private lateinit var tfliteHelper: TFLiteHelper
    private var isEmptyFrame = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        timerText = findViewById(R.id.timerText)
        tfliteHelper = TFLiteHelper(this)

        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

        captureButton.setOnClickListener {
            captureImage()
        }

        startCamera()
        startCountdownTimer()
    }

    private fun startCountdownTimer() {
        autoCapture = true
        countDownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerText.text = getString(R.string.auto_capture_countdown, (millisUntilFinished / 1000).toInt())
            }

            override fun onFinish() {
                timerText.text = getString(R.string.capturing)
                if (autoCapture) {
                    captureImage()
                }
            }
        }.start()
    }

    private fun captureImage() {
        countDownTimer?.cancel()
        autoCapture = false
        timerText.text = getString(R.string.processing_image)
        captureButton.isEnabled = false

        imageCapture?.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                @SuppressLint("UnsafeOptInUsageError")
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    val processedBitmap = preprocessBitmap(bitmap)
                    val inputArray = bitmapToFloatArray(processedBitmap)
                    val result = processImageWithTFLite(inputArray)

                    val resultIntent = Intent()
                    resultIntent.putExtra("RESULT_TEXT", result)
                    setResult(RESULT_OK, resultIntent)

                    val mainIntent = Intent(this@CameraActivity, MainActivity::class.java)
                    mainIntent.putExtra("RESULT_TEXT", result)
                    mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(mainIntent)

                    imageProxy.close()
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    timerText.text = getString(R.string.error_capturing)
                    captureButton.isEnabled = true
                }
            }
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun preprocessBitmap(original: Bitmap): Bitmap {
        val inputSize = 224
        val dimension = min(original.width, original.height)
        val xOffset = (original.width - dimension) / 2
        val yOffset = (original.height - dimension) / 2

        val croppedBitmap = Bitmap.createBitmap(original, xOffset, yOffset, dimension, dimension)
        isEmptyFrame = !likelyContainsCurrency(croppedBitmap)
        return croppedBitmap.scale(inputSize, inputSize, false)
    }

    private fun likelyContainsCurrency(bitmap: Bitmap): Boolean {
        val sampledBitmap = bitmap.scale(bitmap.width / 4, bitmap.height / 4, false)
        val edges = countEdges(sampledBitmap)
        return edges > 250
    }

    private fun countEdges(bitmap: Bitmap): Int {
        var edgeCount = 0
        val threshold = 30
        val stepSize = 2
        for (y in stepSize until bitmap.height step stepSize) {
            for (x in stepSize until bitmap.width step stepSize) {
                val pixel1 = bitmap[x, y]
                val pixel2 = bitmap[x - stepSize, y]

                val diff = abs((pixel1 shr 16 and 0xFF) - (pixel2 shr 16 and 0xFF)) +
                        abs((pixel1 shr 8 and 0xFF) - (pixel2 shr 8 and 0xFF)) +
                        abs((pixel1 and 0xFF) - (pixel2 and 0xFF))
                if (diff > threshold) {
                    edgeCount++
                }
            }
        }
        return edgeCount
    }

    private fun bitmapToFloatArray(bitmap: Bitmap): FloatArray {
        val floatValues = FloatArray(bitmap.width * bitmap.height * 3)
        var index = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap[x, y]
                floatValues[index++] = (pixel shr 16 and 0xFF) / 255.0f
                floatValues[index++] = (pixel shr 8 and 0xFF) / 255.0f
                floatValues[index++] = (pixel and 0xFF) / 255.0f
            }
        }
        return floatValues
    }

    private fun processImageWithTFLite(inputArray: FloatArray): String {
        if (isEmptyFrame) {
            return getString(R.string.unrecognized_currency)
        }

        val outputArray = tfliteHelper.runInference(inputArray)
        val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: 0
        val maxConfidence = outputArray[maxIndex]

        val currencyLabels = arrayOf(
            "2000 rupees",
            "500 rupees",
            "200 rupees",
            "100 rupees",
            "50 rupees"
        )

        return if (maxConfidence > 0.90f) {
            getString(R.string.currency_detected, currencyLabels[maxIndex], (maxConfidence * 100).toInt())
        } else {
            getString(R.string.unrecognized_currency)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                timerText.text = getString(R.string.camera_init_failed)
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        cameraExecutor.shutdown()
    }
}
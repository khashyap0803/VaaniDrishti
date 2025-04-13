package com.example.cash

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteHelper(private val context: Context) {
    private var interpreter: Interpreter
    private val labels: List<String>

    init {
        // Load the TF Lite model
        val modelBuffer = loadModelFile()
        interpreter = Interpreter(modelBuffer)

        // Load labels from assets
        labels = loadLabels()
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("model_unquant.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(): List<String> {
        val labelsInput = context.assets.open("labels.txt")
        val reader = InputStreamReader(labelsInput)
        return reader.readLines().filter { it.isNotBlank() }
    }

    fun runInference(inputArray: FloatArray): FloatArray {
        // Get output details from model
        val outputTensor = interpreter.getOutputTensor(0)
        val outputSize = outputTensor.shape()[1] // Typically [1, numClasses]

        // Prepare input
        val inputData = ByteBuffer.allocateDirect(inputArray.size * 4) // 4 bytes per float
        inputData.order(ByteOrder.nativeOrder())

        // Add data to the buffer
        for (value in inputArray) {
            inputData.putFloat(value)
        }
        inputData.rewind()

        // Create output buffer
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4) // 4 bytes per float
        outputBuffer.order(ByteOrder.nativeOrder())

        // Run inference
        interpreter.run(inputData, outputBuffer)

        // Get results
        outputBuffer.rewind()
        val results = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            results[i] = outputBuffer.float
        }

        return results
    }
}
package com.yomu.ml

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import java.io.File
import java.nio.FloatBuffer

class OnnxRuntime(private val context: Context) {

    private val loadedModels = mutableMapOf<String, OrtSession>()
    private var ortEnv: OrtEnvironment? = null

    data class Detection(
        val label: String,
        val confidence: Float,
        val bbox: FloatArray
    )

    fun init() {
        if (ortEnv == null) {
            ortEnv = OrtEnvironment.getEnvironment()
        }
    }

    fun loadModel(modelPath: String): Boolean {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) return false

        return try {
            init()
            val env = ortEnv ?: return false
            val opts = OrtSession.SessionOptions()
            val session = env.createSession(modelFile.absolutePath, opts)
            loadedModels[modelPath] = session
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isModelLoaded(modelName: String): Boolean {
        return loadedModels.containsKey(modelName)
    }

    fun runInference(modelName: String, input: FloatArray, inputShape: LongArray): FloatArray? {
        val session = loadedModels[modelName] ?: return null
        val env = ortEnv ?: return null

        return try {
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), inputShape)
            val result = session.run(mapOf("images" to inputTensor))
            val output = result.get("output0") as? OnnxTensor
            output?.getFloatBuffer()?.array()
        } catch (e: Exception) {
            null
        }
    }

    fun runBitmapInference(modelName: String, bitmap: Bitmap): List<Detection> {
        val session = loadedModels[modelName] ?: return emptyList()
        val env = ortEnv ?: return emptyList()

        return try {
            val inputSize = 640
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val floatArray = FloatArray(inputSize * inputSize * 3)

            val pixels = IntArray(inputSize * inputSize)
            resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            for (i in pixels.indices) {
                val pixel = pixels[i]
                floatArray[i * 3] = ((pixel shr 16) and 0xFF) / 255.0f
                floatArray[i * 3 + 1] = ((pixel shr 8) and 0xFF) / 255.0f
                floatArray[i * 3 + 2] = (pixel and 0xFF) / 255.0f
            }

            val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape)
            val result = session.run(mapOf("images" to inputTensor))
            val output = result.get("output0") as? OnnxTensor

            if (output != null) {
                val outputArray = output.getFloatBuffer().array()
                parseDetections(outputArray, 0.5f)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseDetections(output: FloatArray, confidenceThreshold: Float): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numDetections = output.size / 6

        for (i in 0 until numDetections) {
            val base = i * 6
            val confidence = output[base + 4]
            if (confidence >= confidenceThreshold) {
                detections.add(
                    Detection(
                        label = "bubble",
                        confidence = confidence,
                        bbox = floatArrayOf(
                            output[base],
                            output[base + 1],
                            output[base + 2],
                            output[base + 3]
                        )
                    )
                )
            }
        }

        return detections.sortedByDescending { it.confidence }
    }

    fun releaseModel(modelName: String) {
        loadedModels.remove(modelName)?.close()
    }

    fun release() {
        for ((_, session) in loadedModels) {
            session.close()
        }
        loadedModels.clear()
        ortEnv?.close()
        ortEnv = null
    }
}

package com.yomu.ml

import android.content.Context
import android.graphics.Bitmap
import com.yomu.core.Constants
import java.io.File

class OnnxRuntime(private val context: Context) {

    private val loadedModels = mutableMapOf<String, OrtSession>()
    private var ortEnv: OrtEnvironment? = null

    data class Detection(
        val label: String,
        val confidence: Float,
        val bbox: FloatArray  // [x, y, w, h] normalized
    )

    fun init() {
        if (ortEnv == null) {
            ortEnv = OrtEnvironment.getEnvironment()
        }
    }

    private fun getModelsDir(): File {
        return File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}")
    }

    fun loadModel(modelName: String): Boolean {
        val modelFile = File(getModelsDir(), modelName)
        if (!modelFile.exists()) return false

        return try {
            init()
            val env = ortEnv ?: return false
            val session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
            loadedModels[modelName] = session
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
            val inputTensor = OnnxTensor.createTensor(env, input, inputShape)
            val result = session.run(mapOf("input" to inputTensor))
            val output = result.get("output") as? OnnxTensor
            output?.floatArray
        } catch (e: Exception) {
            null
        }
    }

    fun runBitmapInference(modelName: String, bitmap: Bitmap): List<Detection> {
        val session = loadedModels[modelName] ?: return emptyList()
        val env = ortEnv ?: return emptyList()

        return try {
            // Preprocess: resize to model input size, normalize, convert to float array
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
            val inputTensor = OnnxTensor.createTensor(env, floatArray, shape)
            val result = session.run(mapOf("images" to inputTensor))
            val output = result.get("output0") as? OnnxTensor

            if (output != null) {
                parseDetections(output.floatArray, 0.5f)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseDetections(output: FloatArray, confidenceThreshold: Float): List<Detection> {
        // YOLO output format: [batch, num_detections, 6] where 6 = [x, y, w, h, confidence, class_id]
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
                            output[base],     // x
                            output[base + 1], // y
                            output[base + 2], // w
                            output[base + 3]  // h
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
        loadedModels.values.forEach { it.close() }
        loadedModels.clear()
        ortEnv?.close()
        ortEnv = null
    }
}

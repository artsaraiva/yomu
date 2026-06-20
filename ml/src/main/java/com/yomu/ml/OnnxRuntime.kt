package com.yomu.ml

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

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

    fun runInferenceNamed(
        modelPath: String,
        inputs: Map<String, FloatArray>,
        inputShapes: Map<String, LongArray>
    ): Map<String, FloatArray>? {
        val session = loadedModels[modelPath] ?: return null
        val env = ortEnv ?: return null

        return try {
            val inputTensors = inputs.mapValues { (name, data) ->
                val shape = inputShapes[name] ?: return null
                OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
            }
            val result = session.run(inputTensors)
            readOutputs(session, result)
        } catch (e: Exception) {
            null
        }
    }

    fun runInferenceMixed(
        modelPath: String,
        floatInputs: Map<String, FloatArray>,
        floatInputShapes: Map<String, LongArray>,
        longInputs: Map<String, LongArray>,
        longInputShapes: Map<String, LongArray>
    ): Map<String, FloatArray>? {
        val session = loadedModels[modelPath] ?: return null
        val env = ortEnv ?: return null

        return try {
            val inputTensors = mutableMapOf<String, OnnxTensor>()
            for ((name, data) in floatInputs) {
                val shape = floatInputShapes[name] ?: return null
                inputTensors[name] = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
            }
            for ((name, data) in longInputs) {
                val shape = longInputShapes[name] ?: return null
                inputTensors[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)
            }
            val result = session.run(inputTensors)
            readOutputs(session, result)
        } catch (e: Exception) {
            null
        }
    }

    fun getInputNames(modelPath: String): List<String> {
        return loadedModels[modelPath]?.getInputNames()?.toList() ?: emptyList()
    }

    fun getInputShape(modelPath: String, inputName: String): LongArray? {
        val session = loadedModels[modelPath] ?: return null
        val info = session.getInputInfo()[inputName] ?: return null
        val tensorInfo = info.info as? TensorInfo ?: return null
        return tensorInfo.shape
    }

    fun isInputLong(modelPath: String, inputName: String): Boolean {
        val session = loadedModels[modelPath] ?: return false
        val info = session.getInputInfo()[inputName] ?: return false
        val tensorInfo = info.info as? TensorInfo ?: return false
        return tensorInfo.type == OnnxJavaType.INT64
    }

    fun getOutputNames(modelPath: String): List<String> {
        return loadedModels[modelPath]?.getOutputNames()?.toList() ?: emptyList()
    }

    fun getOutputShape(modelPath: String, outputName: String): LongArray? {
        val session = loadedModels[modelPath] ?: return null
        val info = session.getOutputInfo()[outputName] ?: return null
        val tensorInfo = info.info as? TensorInfo ?: return null
        return tensorInfo.shape
    }

    fun runBitmapInference(modelName: String, bitmap: Bitmap): List<Detection> {
        val session = loadedModels[modelName] ?: return emptyList()
        val env = ortEnv ?: return emptyList()

        return try {
            val inputSize = 1280
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
            val inputName = session.getInputNames().first()
            val result = session.run(mapOf(inputName to inputTensor))
            val outputName = session.getOutputNames().first()
            val output = result.get(outputName) as? OnnxTensor

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

    private fun readOutputs(
        session: OrtSession,
        result: OrtSession.Result
    ): Map<String, FloatArray> {
        val outputs = mutableMapOf<String, FloatArray>()
        for (name in session.getOutputNames()) {
            val tensor = result.get(name) as? OnnxTensor ?: continue
            outputs[name] = tensor.getFloatBuffer().array()
        }
        return outputs
    }

    private fun parseDetections(output: FloatArray, confidenceThreshold: Float): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numDetections = output.size / 6

        for (i in 0 until numDetections) {
            val base = i * 6
            val confidence = output[base + 4]
            val classId = output[base + 5].toInt()
            if (confidence >= confidenceThreshold && classId == 0) {
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

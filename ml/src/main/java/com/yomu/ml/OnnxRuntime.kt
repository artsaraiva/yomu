package com.yomu.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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

    companion object {
        private const val TAG = "OnnxRuntime"
        private const val YOLO_INPUT_SIZE = 1280
        private const val YOLO_FIELDS_PER_DETECTION = 6
        private const val YOLO_EXPECTED_DETECTIONS = 300
        private const val YOLO_EXPECTED_OUTPUT_SIZE = YOLO_EXPECTED_DETECTIONS * YOLO_FIELDS_PER_DETECTION
        private const val RTDETR_INPUT_SIZE = 640
        private const val RTDETR_SIZES_INPUT = "orig_target_sizes"
    }

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
            Log.e(TAG, "Failed to load model at $modelPath: ${e.message}", e)
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
            try {
                val result = session.run(mapOf("images" to inputTensor))
                try {
                    val output = result.get("output0").orElse(null) as? OnnxTensor
                    output?.let { readFloatBuffer(it.getFloatBuffer()) }
                } finally {
                    result.close()
                }
            } finally {
                inputTensor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "runInference failed for $modelName: ${e.message}", e)
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
        val inputTensors = mutableMapOf<String, OnnxTensor>()

        return try {
            for ((name, data) in inputs) {
                val shape = inputShapes[name] ?: return null
                inputTensors[name] = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
            }
            val result = session.run(inputTensors)
            try {
                readOutputs(session, result)
            } finally {
                result.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "runInferenceNamed failed for $modelPath: ${e.message}", e)
            null
        } finally {
            closeTensors(inputTensors.values)
        }
    }

    /** Starts a [PastKeyValueDecode] of [modelPath] over this encoder output; null when the model is not loaded. */
    fun startPastKeyValueDecode(modelPath: String, encoderHiddenStates: FloatArray, shape: LongArray): PastKeyValueDecode? {
        val session = loadedModels[modelPath] ?: return null
        val env = ortEnv ?: return null
        return try {
            PastKeyValueDecode(env, session, OnnxTensor.createTensor(env, FloatBuffer.wrap(encoderHiddenStates), shape))
        } catch (e: Exception) {
            Log.e(TAG, "startPastKeyValueDecode failed for $modelPath: ${e.message}", e)
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

    fun getOutputNames(modelPath: String): List<String> {
        return loadedModels[modelPath]?.getOutputNames()?.toList() ?: emptyList()
    }

    fun getOutputShape(modelPath: String, outputName: String): LongArray? {
        val session = loadedModels[modelPath] ?: return null
        val info = session.getOutputInfo()[outputName] ?: return null
        val tensorInfo = info.info as? TensorInfo ?: return null
        return tensorInfo.shape
    }

    fun runBitmapInference(
        modelName: String,
        bitmap: Bitmap,
        confidenceThreshold: Float
    ): List<Detection> {
        val session = loadedModels[modelName] ?: return emptyList()
        val env = ortEnv ?: return emptyList()

        // Spike #437: the ogkalu RT-DETR v4-s takes a second input and returns page-pixel boxes.
        if (session.inputNames.contains(RTDETR_SIZES_INPUT)) {
            return runRtDetrInference(session, env, bitmap, confidenceThreshold)
        }

        return try {
            val startNs = System.nanoTime()
            val letterbox = Letterbox(bitmap.width, bitmap.height, YOLO_INPUT_SIZE)
            val resized = Bitmap.createScaledBitmap(bitmap, letterbox.scaledWidth, letterbox.scaledHeight, true)
            val pixels = IntArray(letterbox.inputWidth * letterbox.inputHeight) { Letterbox.PAD_COLOR }
            resized.getPixels(pixels, 0, letterbox.inputWidth, 0, 0, letterbox.scaledWidth, letterbox.scaledHeight)
            val floatArray = bitmapPixelsToCHW(pixels, letterbox.inputWidth, letterbox.inputHeight)

            val shape = longArrayOf(1, 3, letterbox.inputHeight.toLong(), letterbox.inputWidth.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape)
            try {
                val inputName = session.getInputNames().first()
                val result = session.run(mapOf(inputName to inputTensor))
                try {
                    val outputName = session.getOutputNames().first()
                    val output = result.get(outputName).orElse(null) as? OnnxTensor

                    if (output != null) {
                        val outputArray = readFloatBuffer(output.getFloatBuffer())
                        if (outputArray.size != YOLO_EXPECTED_OUTPUT_SIZE) {
                            if (outputArray.size % YOLO_FIELDS_PER_DETECTION != 0) {
                                Log.e(
                                    TAG,
                                    "YOLO output size ${outputArray.size} is invalid; not divisible by $YOLO_FIELDS_PER_DETECTION"
                                )
                            } else {
                                Log.e(
                                    TAG,
                                    "YOLO output size ${outputArray.size} does not match expected $YOLO_EXPECTED_OUTPUT_SIZE"
                                )
                            }
                            emptyList()
                        } else {
                            val detections = parseYoloDetections(outputArray, YOLO_FIELDS_PER_DETECTION, confidenceThreshold)
                            Log.i(TAG, "YOLO_DETECT ms=${(System.nanoTime() - startNs) / 1_000_000} kept=${detections.size}")
                            if (detections.isEmpty()) {
                                Log.d(TAG, "YOLO inference produced zero detections above threshold")
                            }
                            detections.map { it.copy(bbox = letterbox.toSource(it.bbox)) }
                        }
                    } else {
                        Log.e(TAG, "YOLO output tensor was null for model $modelName")
                        emptyList()
                    }
                } finally {
                    result.close()
                }
            } finally {
                inputTensor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "runBitmapInference failed for $modelName: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Spike #437: `ogkalu/comic-text-and-bubble-detector` v4-s. A plain 640x640 stretch, RGB/255,
     * `orig_target_sizes` = [width, height]; `labels`/`boxes`/`scores` come back in page pixels.
     * Classes 1 (`text_bubble`) and 2 (`text_free`) are Yomu's text; class 0 (`bubble`) is dropped.
     */
    private fun runRtDetrInference(
        session: OrtSession,
        env: OrtEnvironment,
        bitmap: Bitmap,
        confidenceThreshold: Float
    ): List<Detection> {
        return try {
            val startNs = System.nanoTime()
            val resized = Bitmap.createScaledBitmap(bitmap, RTDETR_INPUT_SIZE, RTDETR_INPUT_SIZE, true)
            val pixels = IntArray(RTDETR_INPUT_SIZE * RTDETR_INPUT_SIZE)
            resized.getPixels(pixels, 0, RTDETR_INPUT_SIZE, 0, 0, RTDETR_INPUT_SIZE, RTDETR_INPUT_SIZE)
            val floatArray = bitmapPixelsToCHW(pixels, RTDETR_INPUT_SIZE, RTDETR_INPUT_SIZE)
            val images = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(floatArray), longArrayOf(1, 3, RTDETR_INPUT_SIZE.toLong(), RTDETR_INPUT_SIZE.toLong())
            )
            val sizes = OnnxTensor.createTensor(
                env, LongBuffer.wrap(longArrayOf(bitmap.width.toLong(), bitmap.height.toLong())), longArrayOf(1, 2)
            )
            try {
                val result = session.run(mapOf("images" to images, RTDETR_SIZES_INPUT to sizes))
                try {
                    val labels = readLongBuffer((result.get("labels").get() as OnnxTensor).longBuffer)
                    val boxes = readFloatBuffer((result.get("boxes").get() as OnnxTensor).floatBuffer)
                    val scores = readFloatBuffer((result.get("scores").get() as OnnxTensor).floatBuffer)
                    val detections = mutableListOf<Detection>()
                    for (i in scores.indices) {
                        if (scores[i] >= confidenceThreshold && labels[i] != 0L) {
                            detections.add(
                                Detection(
                                    label = "bubble",
                                    confidence = scores[i],
                                    bbox = floatArrayOf(boxes[i * 4], boxes[i * 4 + 1], boxes[i * 4 + 2], boxes[i * 4 + 3])
                                )
                            )
                        }
                    }
                    Log.i(TAG, "RTDETR_DETECT ms=${(System.nanoTime() - startNs) / 1_000_000} kept=${detections.size}")
                    detections.sortedByDescending { it.confidence }
                } finally {
                    result.close()
                }
            } finally {
                images.close()
                sizes.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "runRtDetrInference failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun readOutputs(
        session: OrtSession,
        result: OrtSession.Result
    ): Map<String, FloatArray> {
        val outputs = mutableMapOf<String, FloatArray>()
        for (name in session.getOutputNames()) {
            val tensor = result.get(name).orElse(null) as? OnnxTensor ?: continue
            outputs[name] = readFloatBuffer(tensor.getFloatBuffer())
        }
        return outputs
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

    private fun closeTensors(tensors: Collection<OnnxTensor>) {
        for (tensor in tensors) {
            try {
                tensor.close()
            } catch (_: Exception) {
            }
        }
    }
}

internal fun readFloatBuffer(buffer: FloatBuffer): FloatArray {
    val copy = buffer.duplicate()
    copy.rewind()
    val output = FloatArray(copy.remaining())
    copy.get(output)
    return output
}

internal fun readLongBuffer(buffer: LongBuffer): LongArray {
    val copy = buffer.duplicate()
    copy.rewind()
    val output = LongArray(copy.remaining())
    copy.get(output)
    return output
}

internal fun bitmapPixelsToCHW(pixels: IntArray, width: Int, height: Int): FloatArray {
    val planeSize = width * height
    require(pixels.size == planeSize) {
        "pixels size ${pixels.size} must equal width*height ($planeSize)"
    }

    val floatArray = FloatArray(planeSize * 3)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        floatArray[i] = ((pixel shr 16) and 0xFF) / 255f
        floatArray[planeSize + i] = ((pixel shr 8) and 0xFF) / 255f
        floatArray[2 * planeSize + i] = (pixel and 0xFF) / 255f
    }
    return floatArray
}

internal fun parseYoloDetections(
    output: FloatArray,
    fieldsPerDetection: Int,
    confidenceThreshold: Float
): List<OnnxRuntime.Detection> {
    if (fieldsPerDetection <= 0 || output.size % fieldsPerDetection != 0) {
        return emptyList()
    }

    val detections = mutableListOf<OnnxRuntime.Detection>()
    val numDetections = output.size / fieldsPerDetection
    for (i in 0 until numDetections) {
        val base = i * fieldsPerDetection
        val confidence = output[base + 4]
        val classId = output[base + 5].toInt()
        if (confidence >= confidenceThreshold && classId == 0) {
            detections.add(
                OnnxRuntime.Detection(
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

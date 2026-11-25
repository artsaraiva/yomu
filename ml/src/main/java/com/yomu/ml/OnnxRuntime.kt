package com.yomu.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

class OnnxRuntime(private val context: Context) {

    private val loadedModels = mutableMapOf<String, OrtSession>()
    private var ortEnv: OrtEnvironment? = null

    companion object {
        private const val TAG = "OnnxRuntime"
        // The detector was trained on pages stretched to this square, not letterboxed (#437).
        private const val DETECTOR_INPUT_SIZE = 640
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

    /**
     * Runs Hayai's prefill over one crop and returns its [HayaiDecode], or null when either graph is not loaded or the
     * prefill fails. [pixelValues] and [positions] hold [slots] rows each, the crop's [rows] × [columns] patches first
     * and zero padding after; [bosId] is the start token.
     */
    @Suppress("LongParameterList")
    fun startHayaiDecode(
        prefillPath: String,
        decodePath: String,
        pixelValues: FloatArray,
        positions: FloatArray,
        slots: Int,
        rows: Int,
        columns: Int,
        bosId: Long
    ): HayaiDecode? {
        val prefill = loadedModels[prefillPath] ?: return null
        val decode = loadedModels[decodePath] ?: return null
        val env = ortEnv ?: return null
        // The vision tower merges 2 × 2 patches, so the text attends to this many image tokens.
        val visionTokens = ((rows + 1) / 2) * ((columns + 1) / 2)
        val inputs = mapOf(
            "pixel_values" to OnnxTensor.createTensor(env, FloatBuffer.wrap(pixelValues), longArrayOf(1, slots.toLong(), (pixelValues.size / slots).toLong())),
            "pos_embeds" to OnnxTensor.createTensor(env, FloatBuffer.wrap(positions), longArrayOf(1, slots.toLong(), (positions.size / slots).toLong())),
            "spatial_shapes" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(rows.toLong(), columns.toLong())), longArrayOf(1, 2)),
            "bos_ids" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(bosId)), longArrayOf(1, 1)),
            "l_vis_arange" to OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(visionTokens) { it.toLong() }), longArrayOf(visionTokens.toLong()))
        )
        return try {
            val result = prefill.run(inputs)
            // The decode owns the prefill's result from here; if it cannot start, nothing else will close it.
            runCatching { HayaiDecode(env, decode, result) }.getOrElse { result.close(); throw it }
        } catch (e: Exception) {
            Log.e(TAG, "Hayai prefill failed for $prefillPath: ${e.message}", e)
            null
        } finally {
            closeTensors(inputs.values)
        }
    }

    /**
     * Detects text on [bitmap] with the ogkalu RT-DETR detector loaded from [modelName]. The page is stretched to the
     * model's square input and its `[width, height]` passed alongside, so boxes come back in page pixels. Empty on failure.
     */
    fun runBitmapInference(
        modelName: String,
        bitmap: Bitmap,
        confidenceThreshold: Float
    ): List<Detection> {
        val session = loadedModels[modelName] ?: return emptyList()
        val env = ortEnv ?: return emptyList()
        val inputs = mutableMapOf<String, OnnxTensor>()

        return try {
            val size = DETECTOR_INPUT_SIZE
            val pixels = IntArray(size * size)
            Bitmap.createScaledBitmap(bitmap, size, size, true).getPixels(pixels, 0, size, 0, 0, size, size)
            inputs["images"] = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(bitmapPixelsToCHW(pixels, size, size)), longArrayOf(1, 3, size.toLong(), size.toLong())
            )
            // Width first: [height, width] puts boxes past the page edge (#437).
            inputs["orig_target_sizes"] = OnnxTensor.createTensor(
                env, LongBuffer.wrap(longArrayOf(bitmap.width.toLong(), bitmap.height.toLong())), longArrayOf(1, 2)
            )
            val result = session.run(inputs)
            try {
                val labels = result.tensor("labels")?.getLongBuffer()?.let(::readLongBuffer)
                val boxes = result.tensor("boxes")?.getFloatBuffer()?.let(::readFloatBuffer)
                val scores = result.tensor("scores")?.getFloatBuffer()?.let(::readFloatBuffer)
                if (labels == null || boxes == null || scores == null) {
                    Log.e(TAG, "Detector outputs missing for model $modelName")
                    emptyList()
                } else {
                    parseRtDetrDetections(labels, boxes, scores, confidenceThreshold)
                }
            } finally {
                result.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "runBitmapInference failed for $modelName: ${e.message}", e)
            emptyList()
        } finally {
            closeTensors(inputs.values)
        }
    }

    private fun OrtSession.Result.tensor(name: String): OnnxTensor? = get(name).orElse(null) as? OnnxTensor

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

private const val RT_DETR_TEXT_FREE = 2L
/** The ogkalu RT-DETR's classes by label id. Spike #471 keeps class 0, `bubble`, the balloon around the text. */
private val RT_DETR_TEXT_CLASSES = mapOf(0L to "bubble", 1L to "text_bubble", RT_DETR_TEXT_FREE to "text_free")
// Below this, `text_free` boxes sound effects and the viewing app's own toolbar labels (#437).
private const val RT_DETR_TEXT_FREE_FLOOR = 0.5f

/** Turns the RT-DETR's `labels`, `boxes` (xyxy, page pixels, four per box) and `scores` into text detections. */
internal fun parseRtDetrDetections(
    labels: LongArray,
    boxes: FloatArray,
    scores: FloatArray,
    confidenceThreshold: Float
): List<OnnxRuntime.Detection> = scores.indices.mapNotNull { i ->
    val label = RT_DETR_TEXT_CLASSES[labels[i]]
    val threshold = if (labels[i] == RT_DETR_TEXT_FREE) maxOf(confidenceThreshold, RT_DETR_TEXT_FREE_FLOOR) else confidenceThreshold
    if (label == null || scores[i] < threshold) null
    else OnnxRuntime.Detection(label, scores[i], boxes.copyOfRange(i * 4, i * 4 + 4))
}

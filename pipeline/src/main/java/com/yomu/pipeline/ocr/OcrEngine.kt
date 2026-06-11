package com.yomu.pipeline.ocr

import android.graphics.Bitmap
import com.yomu.ml.OnnxRuntime

data class OcrResult(
    val text: String,
    val confidence: Float,
    val boundingBox: FloatArray
)

class OcrEngine(private val onnxRuntime: OnnxRuntime) {

    companion object {
        private const val MODEL_NAME = "manga_ocr.onnx"
        private const val CONFIDENCE_THRESHOLD = 0.3f
    }

    private var isLoaded = false

    fun loadModel(): Boolean {
        if (!isLoaded) {
            isLoaded = onnxRuntime.loadModel(MODEL_NAME)
        }
        return isLoaded
    }

    fun isModelLoaded(): Boolean = isLoaded

    fun extractText(region: Bitmap): OcrResult? {
        if (!isLoaded) return null

        val inputSize = 256
        val resized = Bitmap.createScaledBitmap(region, inputSize, inputSize, true)
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
        val output = onnxRuntime.runInference(MODEL_NAME, floatArray, shape)

        if (output == null || output.isEmpty()) {
            return null
        }

        // MangaOCR output decoding - simplified for Phase 1
        // In production, this needs proper CTC decoding of character probabilities
        val decodedText = decodeOutput(output)

        return OcrResult(
            text = decodedText,
            confidence = output.average().toFloat(),
            boundingBox = floatArrayOf(
                region.width.toFloat(),
                region.height.toFloat(),
                0f, 0f
            )
        )
    }

    private fun decodeOutput(output: FloatArray): String {
        // Simplified decoder for Phase 1
        // In production, use CTC beam search decoding with MangaOCR's character set
        if (output.isEmpty()) return ""

        val sb = StringBuilder()
        var prevCharId = -1

        // Simulated decoding: take argmax at each timestep, collapse repeats, remove blanks
        for (i in output.indices) {
            val charId = output[i].toInt()
            if (charId != prevCharId && charId != 0) {
                sb.append(Char(charId + 0x3000)) // Map to Unicode CJK range
                prevCharId = charId
            }
        }

        return sb.toString()
    }

    fun release() {
        onnxRuntime.releaseModel(MODEL_NAME)
        isLoaded = false
    }
}

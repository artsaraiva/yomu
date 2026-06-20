package com.yomu.pipeline.ocr

import android.graphics.Bitmap
import android.util.Log
import com.yomu.ml.OnnxRuntime

data class OcrResult(
    val text: String,
    val confidence: Float,
    val boundingBox: FloatArray
)

class OcrEngine(private val onnxRuntime: OnnxRuntime) {

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.3f
    }

    private var isLoaded = false
    private var modelPath: String? = null

    fun loadModel(modelPath: String): Boolean {
        if (!isLoaded) {
            this.modelPath = modelPath
            isLoaded = onnxRuntime.loadModel(modelPath)
        }
        return isLoaded
    }

    fun isModelLoaded(): Boolean = isLoaded

    fun extractText(region: Bitmap): OcrResult? {
        if (!isLoaded) return null
        val path = modelPath ?: return null

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
        val output = onnxRuntime.runInference(path, floatArray, shape)

        if (output == null || output.isEmpty()) {
            return null
        }

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
        Log.w("OcrEngine", "OCR decode not implemented — awaiting MangaOCR model integration")
        return "[OCR: awaiting model integration]"
    }

    fun release() {
        modelPath?.let { onnxRuntime.releaseModel(it) }
        isLoaded = false
        modelPath = null
    }
}

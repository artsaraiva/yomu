package com.yomu.pipeline.bubble

import android.graphics.Bitmap
import android.graphics.RectF
import com.yomu.ml.OnnxRuntime

data class Bubble(
    val id: Int,
    val boundingBox: RectF,
    val confidence: Float,
    val textRegion: RectF? = null
)

class BubbleDetector(private val onnxRuntime: OnnxRuntime) {

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.5f
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

    fun detect(bitmap: Bitmap): List<Bubble> {
        if (!isLoaded) return emptyList()
        val path = modelPath ?: return emptyList()

        val origWidth = bitmap.width
        val origHeight = bitmap.height

        val detections = onnxRuntime.runBitmapInference(path, bitmap)

        return detections
            .filter { it.confidence >= CONFIDENCE_THRESHOLD }
            .mapIndexed { index, detection ->
                val x = detection.bbox[0] / 640f * origWidth
                val y = detection.bbox[1] / 640f * origHeight
                val w = detection.bbox[2] / 640f * origWidth
                val h = detection.bbox[3] / 640f * origHeight

                Bubble(
                    id = index + 1,
                    boundingBox = RectF(x, y, x + w, y + h),
                    confidence = detection.confidence,
                    textRegion = RectF(x, y, x + w, y + h)
                )
            }
    }

    fun release() {
        modelPath?.let { onnxRuntime.releaseModel(it) }
        isLoaded = false
        modelPath = null
    }
}

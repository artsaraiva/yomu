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
                val x1 = detection.bbox[0] / 1280f * origWidth
                val y1 = detection.bbox[1] / 1280f * origHeight
                val x2 = detection.bbox[2] / 1280f * origWidth
                val y2 = detection.bbox[3] / 1280f * origHeight

                Bubble(
                    id = index + 1,
                    boundingBox = RectF(x1, y1, x2, y2),
                    confidence = detection.confidence,
                    textRegion = RectF(x1, y1, x2, y2)
                )
            }
    }

    fun release() {
        modelPath?.let { onnxRuntime.releaseModel(it) }
        isLoaded = false
        modelPath = null
    }
}

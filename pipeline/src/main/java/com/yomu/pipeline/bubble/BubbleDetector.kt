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
        private const val MODEL_NAME = "bubble_detection.onnx"
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }

    private var isLoaded = false

    fun loadModel(): Boolean {
        if (!isLoaded) {
            isLoaded = onnxRuntime.loadModel(MODEL_NAME)
        }
        return isLoaded
    }

    fun isModelLoaded(): Boolean = isLoaded

    fun detect(bitmap: Bitmap): List<Bubble> {
        if (!isLoaded) return emptyList()

        val detections = onnxRuntime.runBitmapInference(MODEL_NAME, bitmap)

        return detections
            .filter { it.confidence >= CONFIDENCE_THRESHOLD }
            .mapIndexed { index, detection ->
                Bubble(
                    id = index + 1,
                    boundingBox = RectF(
                        detection.bbox[0],
                        detection.bbox[1],
                        detection.bbox[0] + detection.bbox[2],
                        detection.bbox[1] + detection.bbox[3]
                    ),
                    confidence = detection.confidence,
                    textRegion = RectF(
                        detection.bbox[0],
                        detection.bbox[1],
                        detection.bbox[0] + detection.bbox[2],
                        detection.bbox[1] + detection.bbox[3]
                    )
                )
            }
    }

    fun release() {
        onnxRuntime.releaseModel(MODEL_NAME)
        isLoaded = false
    }
}

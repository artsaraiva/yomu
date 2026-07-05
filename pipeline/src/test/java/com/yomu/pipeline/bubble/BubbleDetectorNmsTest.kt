package com.yomu.pipeline.bubble

import com.yomu.ml.OnnxRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleDetectorNmsTest {

    @Test
    fun nonMaxSuppressDetections_removesLowerConfidenceOverlappingDetections() {
        val high = detection(0.9f, floatArrayOf(0f, 0f, 100f, 100f))
        val lowOverlap = detection(0.6f, floatArrayOf(10f, 10f, 110f, 110f))
        val separate = detection(0.7f, floatArrayOf(300f, 300f, 360f, 360f))

        val kept = nonMaxSuppressDetections(
            detections = listOf(lowOverlap, separate, high),
            iouThreshold = 0.45f
        )

        assertEquals(2, kept.size)
        assertTrue(kept.contains(high))
        assertTrue(kept.contains(separate))
    }

    @Test
    fun nonMaxSuppressDetections_keepsBoxesWhenOverlapUnderThreshold() {
        val first = detection(0.9f, floatArrayOf(0f, 0f, 100f, 100f))
        val second = detection(0.8f, floatArrayOf(40f, 40f, 140f, 140f))

        val kept = nonMaxSuppressDetections(
            detections = listOf(first, second),
            iouThreshold = 0.45f
        )

        assertEquals(2, kept.size)
    }

    @Test
    fun detectionIou_calculatesExpectedValue() {
        val first = detection(0.9f, floatArrayOf(0f, 0f, 100f, 100f))
        val second = detection(0.8f, floatArrayOf(10f, 10f, 110f, 110f))

        val iou = detectionIou(first, second)

        assertEquals(0.6806723f, iou, 1e-4f)
    }

    private fun detection(confidence: Float, bbox: FloatArray): OnnxRuntime.Detection {
        return OnnxRuntime.Detection(
            label = "bubble",
            confidence = confidence,
            bbox = bbox
        )
    }
}

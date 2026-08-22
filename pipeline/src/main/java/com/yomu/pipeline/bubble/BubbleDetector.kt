package com.yomu.pipeline.bubble

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.yomu.ml.OnnxRuntime

data class Bubble(
    val id: Int,
    val boundingBox: RectF,
    val confidence: Float,
    val textRegion: RectF? = null
)

class BubbleDetector(private val onnxRuntime: OnnxRuntime) {

    companion object {
        private const val TAG = "BubbleDetector"
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val NMS_IOU_THRESHOLD = 0.80f
        // #88 bug B: a small box sitting almost entirely inside a bigger one has low IoU, so IoU-NMS
        // keeps both — two boxes over the same balloon, whose translated texts then paint on top of
        // each other. Suppress a box that is at least this fraction contained in a higher-confidence
        // kept box. 0.97 (not lower) so only near-perfect duplicates go; genuinely-close distinct
        // balloons the model under-separates stay for the detector eval (#57), not this heuristic.
        private const val NMS_CONTAINMENT_THRESHOLD = 0.97f
    }

    private var isLoaded = false
    private var modelPath: String? = null

    /** Pre-NMS (post-threshold) vs post-NMS counts of the last [detect] call. The benchmark reads
     *  this to check whether NMS ever removes a box (#57); production ignores it. */
    data class DetectStats(val thresholded: Int, val kept: Int)
    var lastStats: DetectStats? = null
        private set

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

        val rawDetections = onnxRuntime.runBitmapInference(path, bitmap)
        val confidenceFiltered = rawDetections.filter { it.confidence >= CONFIDENCE_THRESHOLD }
        val detections = nonMaxSuppressDetections(
            confidenceFiltered,
            NMS_IOU_THRESHOLD,
            NMS_CONTAINMENT_THRESHOLD
        )
        lastStats = DetectStats(thresholded = confidenceFiltered.size, kept = detections.size)
        Log.d(
            TAG,
            "Bubble detection raw=${rawDetections.size} thresholded=${confidenceFiltered.size} kept=${detections.size}"
        )
        detections.forEachIndexed { index, detection ->
            Log.d(
                TAG,
                "Bubble kept index=$index confidence=${detection.confidence} bbox=${detection.bbox.joinToString(",") }"
            )
        }

        return detections
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

internal fun nonMaxSuppressDetections(
    detections: List<OnnxRuntime.Detection>,
    iouThreshold: Float,
    // Above 1 disables containment suppression (a fraction is never > 1), keeping IoU-only behaviour.
    containmentThreshold: Float = Float.MAX_VALUE
): List<OnnxRuntime.Detection> {
    if (detections.isEmpty()) return emptyList()
    val sorted = detections.sortedByDescending { it.confidence }
    val kept = mutableListOf<OnnxRuntime.Detection>()

    for (candidate in sorted) {
        // Boxes are visited highest-confidence first, so a suppressed candidate always loses to a
        // box we already trust more. IoU catches near-equal boxes; the containment checks catch the
        // #88 case where one box nearly sits inside the other (either direction) but IoU stays low.
        val redundant = kept.any { keptDetection ->
            detectionIou(candidate, keptDetection) > iouThreshold ||
                detectionContainedFraction(candidate, keptDetection) >= containmentThreshold ||
                detectionContainedFraction(keptDetection, candidate) >= containmentThreshold
        }
        if (!redundant) {
            kept.add(candidate)
        }
    }

    return kept
}

internal fun detectionContainedFraction(
    inner: OnnxRuntime.Detection,
    outer: OnnxRuntime.Detection
): Float {
    val ix1 = minOf(inner.bbox[0], inner.bbox[2])
    val iy1 = minOf(inner.bbox[1], inner.bbox[3])
    val ix2 = maxOf(inner.bbox[0], inner.bbox[2])
    val iy2 = maxOf(inner.bbox[1], inner.bbox[3])

    val ox1 = minOf(outer.bbox[0], outer.bbox[2])
    val oy1 = minOf(outer.bbox[1], outer.bbox[3])
    val ox2 = maxOf(outer.bbox[0], outer.bbox[2])
    val oy2 = maxOf(outer.bbox[1], outer.bbox[3])

    val interWidth = maxOf(0f, minOf(ix2, ox2) - maxOf(ix1, ox1))
    val interHeight = maxOf(0f, minOf(iy2, oy2) - maxOf(iy1, oy1))
    val interArea = interWidth * interHeight
    if (interArea <= 0f) return 0f

    val innerArea = maxOf(0f, ix2 - ix1) * maxOf(0f, iy2 - iy1)
    if (innerArea <= 0f) return 0f

    return interArea / innerArea
}

internal fun detectionIou(a: OnnxRuntime.Detection, b: OnnxRuntime.Detection): Float {
    val ax1 = minOf(a.bbox[0], a.bbox[2])
    val ay1 = minOf(a.bbox[1], a.bbox[3])
    val ax2 = maxOf(a.bbox[0], a.bbox[2])
    val ay2 = maxOf(a.bbox[1], a.bbox[3])

    val bx1 = minOf(b.bbox[0], b.bbox[2])
    val by1 = minOf(b.bbox[1], b.bbox[3])
    val bx2 = maxOf(b.bbox[0], b.bbox[2])
    val by2 = maxOf(b.bbox[1], b.bbox[3])

    val interLeft = maxOf(ax1, bx1)
    val interTop = maxOf(ay1, by1)
    val interRight = minOf(ax2, bx2)
    val interBottom = minOf(ay2, by2)

    val interWidth = maxOf(0f, interRight - interLeft)
    val interHeight = maxOf(0f, interBottom - interTop)
    val interArea = interWidth * interHeight
    if (interArea <= 0f) return 0f

    val areaA = maxOf(0f, ax2 - ax1) * maxOf(0f, ay2 - ay1)
    val areaB = maxOf(0f, bx2 - bx1) * maxOf(0f, by2 - by1)
    val union = areaA + areaB - interArea
    if (union <= 0f) return 0f

    return interArea / union
}

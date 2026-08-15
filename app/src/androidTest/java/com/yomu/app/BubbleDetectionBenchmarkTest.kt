package com.yomu.app

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.core.Constants
import com.yomu.pipeline.bubble.BubbleDetector
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Runs the real BubbleDetector against the eval case pages and emits results as `RESULT_JSON`
 * logcat lines that `run-benchmark.sh` scrapes, mirroring [EngineBenchmarkTest].
 *
 * Pages come from `assets/eval-cases/<case-id>/page.jpg`, copied in by `run-benchmark.sh` before
 * the build. They are gitignored: the images derive from the CC BY-NC OpenMantra dataset and must
 * not be committed.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BubbleDetectionBenchmarkTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var bubbleDetector: BubbleDetector

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun benchmarkBubbleDetection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetManager = InstrumentationRegistry.getInstrumentation().context.assets

        val caseIds = assetManager.list("eval-cases")?.sorted().orEmpty()
            .filter { assetManager.list("eval-cases/$it")?.contains("page.jpg") == true }
        check(caseIds.isNotEmpty()) {
            "No page.jpg under assets/eval-cases; run eval/run-benchmark.sh so it copies the case images in before the build"
        }

        // #57: score the incumbent detector against the yolo26s candidate on identical pages, in one
        // run. The challenger asset is optional so this test still passes on the incumbent alone when
        // run-benchmark.sh has not staged it. Each detector's weights ride into the test APK as
        // gitignored assets, so the run does not depend on what the device has downloaded.
        val stagedAssets = assetManager.list("models")?.toSet().orEmpty()
        val detectors = DETECTORS.filter { it.assetName in stagedAssets }
        check(DETECTORS.first().assetName in stagedAssets) {
            "Missing incumbent asset models/${DETECTORS.first().assetName}; run eval/run-benchmark.sh so it stages the detector weights before the build"
        }

        for (detector in detectors) {
            // A single BubbleDetector cannot switch weights (loadModel is a no-op once loaded), so
            // release between detectors to reset it for the next model path.
            val modelFile = File(context.cacheDir, detector.assetName)
            assetManager.open("models/${detector.assetName}").use { input ->
                modelFile.outputStream().use { input.copyTo(it) }
            }
            check(bubbleDetector.loadModel(modelFile.absolutePath)) {
                "Failed to load detector ${detector.id} at ${modelFile.absolutePath}"
            }

            for (caseId in caseIds) {
                val bitmap = assetManager.open("eval-cases/$caseId/page.jpg").use {
                    BitmapFactory.decodeStream(it)
                } ?: error("Failed to decode eval-cases/$caseId/page.jpg")

                val startNs = System.nanoTime()
                val boxes = bubbleDetector.detect(bitmap)
                val detectMs = (System.nanoTime() - startNs) / 1_000_000.0
                val stats = bubbleDetector.lastStats

                val json = JSONObject().apply {
                    put("detect_ms", detectMs)
                    put("nms_thresholded", stats?.thresholded ?: boxes.size)
                    put("nms_kept", stats?.kept ?: boxes.size)
                    put("boxes", JSONArray(boxes.map { bubble ->
                        JSONObject().apply {
                            put("x", bubble.boundingBox.left.toInt())
                            put("y", bubble.boundingBox.top.toInt())
                            put("w", bubble.boundingBox.width().toInt())
                            put("h", bubble.boundingBox.height().toInt())
                            // conf lets the Python scorer run the #57 confidence sweep offline,
                            // instead of re-running inference at each threshold on device.
                            put("conf", bubble.confidence)
                        }
                    }))
                }
                Log.i(TAG, "RESULT_JSON case=$caseId engine=${detector.engine} json=$json")
                bitmap.recycle()
            }

            bubbleDetector.release()
        }
    }

    private data class DetectorAsset(val id: String, val engine: String, val assetName: String)

    companion object {
        private const val TAG = "BubbleDetectionBenchmarkTest"

        // engine= is the tag run-benchmark.sh writes to <engine>.json. The incumbent keeps engine=bubble
        // so its actual.json path is unchanged; the challenger lands in a parallel actual_s.json.
        private val DETECTORS = listOf(
            DetectorAsset("yolo26n", "bubble", Constants.BUBBLE_DETECTION_MODEL),
            DetectorAsset("yolo26s", "bubble_s", "bubble_detection_s.onnx"),
        )
    }
}

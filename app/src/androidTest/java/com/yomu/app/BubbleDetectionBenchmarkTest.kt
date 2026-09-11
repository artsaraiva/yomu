package com.yomu.app

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.core.Constants
import com.yomu.core.TranslationOutcome
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
 * Runs the real BubbleDetector against the eval case pages and writes one structured detection
 * record per arm/case into the run directory (#165), mirroring [EngineBenchmarkTest].
 *
 * Nothing is scraped out of logcat any more. A detector arm that never runs writes no record, and
 * the scorer reports it as invalid rather than as an implicit pass — which is the #36 failure, where
 * detection was silently stubbed and reported a fake 100%.
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

        val records = RunRecords.open()
        Log.i(TAG, "Writing detection records for run=${records.runId}")

        for (detector in detectors) {
            val arm = ArmMeta(
                armId = detector.engine,
                provider = PROVIDER,
                modelId = detector.id,
                quantization = QUANTIZATION,
                callShape = ArmMeta.CALL_SHAPE_PAGE_IMAGE,
                targetLanguage = null
            )
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

                // Monotonic, per the record contract: wall-clock would drift under a device time
                // change mid-run and the duration is a reported metric.
                val startNs = System.nanoTime()
                val detected = runCatching { bubbleDetector.detect(bitmap) }
                val detectMs = (System.nanoTime() - startNs) / 1_000_000L
                val boxes = detected.getOrDefault(emptyList())
                val stats = bubbleDetector.lastStats

                records.detection(
                    arm = arm,
                    caseId = caseId,
                    // Zero boxes is a valid measured result whose recall fails; only a thrown
                    // detector is an error (#142).
                    outcome = if (detected.isSuccess) {
                        TranslationOutcome.SUCCESS
                    } else {
                        TranslationOutcome.ERROR
                    },
                    durationMs = detectMs,
                    pageWidth = bitmap.width,
                    pageHeight = bitmap.height,
                    boxes = JSONArray(boxes.map { bubble ->
                        JSONObject().apply {
                            put("x", bubble.boundingBox.left.toInt())
                            put("y", bubble.boundingBox.top.toInt())
                            put("w", bubble.boundingBox.width().toInt())
                            put("h", bubble.boundingBox.height().toInt())
                            // conf lets the Python scorer run the #57 confidence sweep offline,
                            // instead of re-running inference at each threshold on device.
                            put("conf", bubble.confidence)
                        }
                    }),
                    nmsThresholded = stats?.thresholded ?: boxes.size,
                    nmsKept = stats?.kept ?: boxes.size,
                    errorCode = detected.exceptionOrNull()?.let { it::class.simpleName }
                )
                bitmap.recycle()
            }

            bubbleDetector.release()
        }
    }

    private data class DetectorAsset(val id: String, val engine: String, val assetName: String)

    companion object {
        private const val TAG = "BubbleDetectionBenchmarkTest"
        private const val PROVIDER = "onnxruntime"

        // The detector weights ship as unquantized ONNX. Stated, not guessed: the manifest declares
        // the same value and the scorer requires them to agree.
        private const val QUANTIZATION = "fp32"

        // `engine` is the arm id in manifest.json and in the run records; `id` is the model id the
        // record carries as observed metadata, so a stubbed or swapped detector cannot pass (#36).
        private val DETECTORS = listOf(
            DetectorAsset("yolo26n", "bubble", Constants.BUBBLE_DETECTION_MODEL),
            DetectorAsset("yolo26s", "bubble_s", "bubble_detection_s.onnx"),
        )
    }
}

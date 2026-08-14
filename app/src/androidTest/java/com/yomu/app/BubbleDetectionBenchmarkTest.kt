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

        // The device's downloaded copy lives in the app's filesDir, which the benchmark cannot count
        // on being populated, so the test ships its own copy of the same weights and stages it.
        val modelFile = File(context.cacheDir, Constants.BUBBLE_DETECTION_MODEL)
        assetManager.open("models/${Constants.BUBBLE_DETECTION_MODEL}").use { input ->
            modelFile.outputStream().use { input.copyTo(it) }
        }
        check(bubbleDetector.loadModel(modelFile.absolutePath)) {
            "Failed to load bubble detection model at ${modelFile.absolutePath}"
        }
        val caseIds = assetManager.list("eval-cases")?.sorted().orEmpty()
            .filter { assetManager.list("eval-cases/$it")?.contains("page.jpg") == true }
        check(caseIds.isNotEmpty()) {
            "No page.jpg under assets/eval-cases; run eval/run-benchmark.sh so it copies the case images in before the build"
        }

        for (caseId in caseIds) {
            val bitmap = assetManager.open("eval-cases/$caseId/page.jpg").use {
                BitmapFactory.decodeStream(it)
            } ?: error("Failed to decode eval-cases/$caseId/page.jpg")
            val boxes = bubbleDetector.detect(bitmap)

            val json = JSONObject().apply {
                put("boxes", JSONArray(boxes.map { bubble ->
                    JSONObject().apply {
                        put("x", bubble.boundingBox.left.toInt())
                        put("y", bubble.boundingBox.top.toInt())
                        put("w", bubble.boundingBox.width().toInt())
                        put("h", bubble.boundingBox.height().toInt())
                    }
                }))
            }
            Log.i(TAG, "RESULT_JSON case=$caseId engine=bubble json=$json")
            bitmap.recycle()
        }

        bubbleDetector.release()
    }

    companion object {
        private const val TAG = "BubbleDetectionBenchmarkTest"
    }
}

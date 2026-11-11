package com.yomu.app

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.ml.OnnxRuntime
import com.yomu.pipeline.bubble.BubbleDetector
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Spike #437: times the shipped yolo26n and the ogkalu RT-DETR v4-s on the same pages; report-only. */
@RunWith(AndroidJUnit4::class)
class DetectorSpeedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun timeBothDetectors() {
        val dir = File("/data/local/tmp/yomu-437")
        val pages = dir.listFiles { f -> f.extension == "png" || f.extension == "jpg" }.orEmpty().sortedBy { it.name }
        check(pages.isNotEmpty()) { "No pages under $dir" }
        val onnx = OnnxRuntime(context)
        try {
            for (model in listOf("bubble_detection.onnx", "detector-v4-s_int8.onnx")) {
                val staged = File(context.cacheDir, model)
                File(dir, model).copyTo(staged, overwrite = true)
                val detector = BubbleDetector(onnx)
                check(detector.loadModel(staged.absolutePath)) { "Cannot load $model" }
                for (page in pages) {
                    val bitmap = checkNotNull(BitmapFactory.decodeFile(page.absolutePath))
                    detector.detect(bitmap, 0.25f) // warm-up
                    val ms = LongArray(5) {
                        val t0 = System.nanoTime()
                        val n = detector.detect(bitmap, 0.25f).size
                        val t = (System.nanoTime() - t0) / 1_000_000
                        Log.i(TAG, "DETECT model=$model page=${page.name} size=${bitmap.width}x${bitmap.height} kept=$n ms=$t")
                        t
                    }
                    Log.i(TAG, "DETECT_MEDIAN model=$model page=${page.name} ms=${ms.sorted()[2]}")
                }
                detector.release()
            }
        } finally {
            onnx.release()
        }
    }

    companion object {
        private const val TAG = "DetectorSpeed"
    }
}

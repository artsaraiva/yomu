package com.yomu.app

import android.graphics.BitmapFactory
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.core.Constants
import com.yomu.core.RuntimeLimits
import com.yomu.ml.LlamaBridge
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.OnnxRuntime
import com.yomu.pipeline.ModelPaths
import com.yomu.pipeline.PipelineResult
import com.yomu.pipeline.TranslationPipeline
import com.yomu.pipeline.TranslationPipeline.Stage
import com.yomu.pipeline.bubble.BubbleDetector
import com.yomu.pipeline.context.ContextAssembler
import com.yomu.pipeline.ocr.OcrEngine
import com.yomu.pipeline.translation.TranslationEngine
import com.yomu.pipeline.typesetting.Typesetter
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Report-only speed benchmark (#230): per-stage milliseconds and peak PSS of the real
 * [TranslationPipeline.processPage] for every [LlmModelCatalog] entry. Nothing about accuracy or the
 * numbers is asserted; it fails only when the pipeline errors. Run via scripts/run-speed-benchmark.sh,
 * which pushes the pages and weights to [FIXTURE_DIR] and turns the TIMING lines into a table.
 */
@RunWith(AndroidJUnit4::class)
class SpeedBenchmarkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun timeEveryCatalogModel(): Unit = runBlocking {
        val pages = File(FIXTURE_DIR, "pages").listFiles { f -> f.extension == "jpg" }.orEmpty().sortedBy { it.name }
        check(pages.isNotEmpty()) { "No pages under $FIXTURE_DIR/pages; run scripts/run-speed-benchmark.sh" }
        val vision = stage(Constants.VISION_MODELS_DIR, VISION_FILES)
        val modelPaths = ModelPaths(
            bubbleDetectionPath = File(vision, Constants.BUBBLE_DETECTION_MODEL).absolutePath,
            ocrEncoderPath = File(vision, Constants.OCR_ENCODER_MODEL).absolutePath,
            ocrDecoderPath = File(vision, Constants.OCR_DECODER_MODEL).absolutePath,
            ocrVocabPath = File(vision, Constants.OCR_VOCAB_FILE).absolutePath
        )
        val sampler = PssSampler().also { it.start() }
        // Shared across entries: its ORT environment is a process singleton, so one per model would leak.
        val onnx = OnnxRuntime(context)
        var rows = 0
        try {
            for (option in LlmModelCatalog.ALL) {
                // An entry the run script did not fetch is skipped rather than failing the run: the
                // catalog now carries deliverables no emulator or 8 GiB phone can hold (Ministral 3
                // 8B, #287), and the script pushes only what the device under test can run.
                if (!File(FIXTURE_DIR, "models/${Constants.LLM_MODELS_DIR}/${option.ggufFileName}").isFile) {
                    Log.i(TAG, "TIMING_SKIPPED model=${option.id} reason=not_staged")
                    continue
                }
                // One GGUF in filesDir at a time: staging all of them next to their /data/local/tmp
                // copies can ENOSPC a small emulator partition.
                val llmDir = stage(Constants.LLM_MODELS_DIR, listOf(option.ggufFileName))
                val native = LlamaBridge(context)
                // Rebuilt per entry, on the call shape the catalog ships for it (batch or per-line).
                // The pipeline's confidenceThreshold is never set here, so detection runs at the
                // default, not the reader's stored value (#227).
                val slot = LlamaTranslationBridge(native, LlmModelCatalog.profileFor(option, llmDir, option.generationDefaults, RuntimeLimits()))
                val pipeline = TranslationPipeline(
                    BubbleDetector(onnx),
                    OcrEngine(onnx),
                    ContextAssembler(),
                    TranslationEngine { slot },
                    Typesetter()
                ).apply { this.modelPaths = modelPaths }
                try {
                    // Warm-up: loads detector, OCR and LLM weights; excluded from the timings, and its
                    // session context is dropped so it cannot lengthen the first timed prompt.
                    runPage(pipeline, pages.first(), sampler)
                    pipeline.release()
                    for ((index, page) in pages.withIndex()) {
                        val timings = runPage(pipeline, page, sampler)
                        timings.forEach { (stage, ms, pssKb) ->
                            Log.i(TAG, "TIMING model=${option.id} page=${index + 1} stage=${stage.name.lowercase()} ms=$ms peakPssKb=$pssKb")
                        }
                        rows += timings.size
                    }
                } finally {
                    pipeline.close()
                    native.release()
                    File(llmDir, option.ggufFileName).delete()
                }
            }
            // The script counts TIMING lines against this, so a line logcat dropped cannot pass unnoticed.
            Log.i(TAG, "TIMING_ROWS n=$rows")
        } finally {
            onnx.release()
            sampler.stop()
        }
    }

    private data class StageTiming(val stage: Stage, val ms: Long, val peakPssKb: Long)

    /**
     * Times one page by stamping the pipeline's own progress callback where the stage changes. Each
     * stage runs from its first callback to the next stage's first callback; typesetting ends at
     * onComplete. The callback does no sampling itself, so no stage pays for [Debug.getPss].
     */
    private suspend fun runPage(pipeline: TranslationPipeline, page: File, sampler: PssSampler): List<StageTiming> {
        val bitmap = checkNotNull(BitmapFactory.decodeFile(page.absolutePath)) { "Cannot decode $page" }
        val timings = mutableListOf<StageTiming>()
        var current: Stage? = null
        var startNs = 0L
        var error: String? = null
        fun endStage(nowNs: Long) {
            current?.let { timings += StageTiming(it, (nowNs - startNs) / 1_000_000L, sampler.takePeak()) }
        }
        val callback = object : TranslationPipeline.PipelineCallback {
            override fun onStageProgress(stage: Stage, progress: Float) {
                if (stage == current) return
                val now = System.nanoTime()
                endStage(now)
                current = stage
                startNs = now
            }

            override fun onError(stage: Stage, message: String) {
                error = "$stage: $message"
            }

            override fun onComplete(result: PipelineResult) = endStage(System.nanoTime())
        }
        sampler.takePeak()
        val result = try {
            pipeline.processPage(bitmap, callback)
        } finally {
            bitmap.recycle()
        }
        check(result != null) { "Pipeline failed on ${page.name}: $error" }
        return timings
    }

    /** Copies pushed fixtures into filesDir/models/<subdir>, where the app's native loaders can read them. */
    private fun stage(subdir: String, names: List<String>): File {
        val target = File(context.filesDir, "${Constants.MODELS_DIR}/$subdir").apply { mkdirs() }
        for (name in names) {
            val source = File(FIXTURE_DIR, "models/$subdir/$name")
            check(source.isFile) { "Missing $source; run scripts/run-speed-benchmark.sh" }
            val dest = File(target, name)
            if (dest.length() == source.length()) continue
            source.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
        return target
    }

    /**
     * Total PSS on a background thread, so the peak includes mid-generation spikes. [takePeak] returns
     * the max since its last call, or the latest sample when the window was shorter than one interval.
     */
    private class PssSampler {
        private val peak = AtomicLong(0)
        private val latest = AtomicLong(0)
        @Volatile private var running = true
        private var worker: Thread? = null

        fun start() {
            worker = thread(name = "pss-sampler", isDaemon = true) {
                while (running) {
                    val pss = Debug.getPss()
                    latest.set(pss)
                    peak.accumulateAndGet(pss, ::maxOf)
                    Thread.sleep(SAMPLE_INTERVAL_MS)
                }
            }
        }

        fun takePeak(): Long = peak.getAndSet(0).takeIf { it > 0 } ?: latest.get()

        fun stop() {
            running = false
            worker?.join()
        }
    }

    companion object {
        private const val TAG = "SpeedBenchmark"
        private const val FIXTURE_DIR = "/data/local/tmp/yomu-speed"
        // getPss walks smaps, which is costly with a mapped GGUF; sampling faster steals CPU from llama's threads.
        private const val SAMPLE_INTERVAL_MS = 500L
        private val VISION_FILES = listOf(
            Constants.BUBBLE_DETECTION_MODEL,
            Constants.OCR_ENCODER_MODEL,
            Constants.OCR_DECODER_MODEL,
            Constants.OCR_VOCAB_FILE
        )
    }
}

package com.yomu.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.core.Constants
import com.yomu.ml.OnnxRuntime
import com.yomu.pipeline.CropBoundsCalculator
import com.yomu.pipeline.bubble.BubbleDetector
import com.yomu.pipeline.ocr.OcrEngine
import com.yomu.pipeline.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp

/**
 * Spike #339, never merged: times the OCR stage with today's no-past export against a
 * past-key-values export of the same weights, on the speed-fixture pages, and checks every crop
 * reads identically. Run via scripts/spike-339-ocr-past.sh.
 */
@RunWith(AndroidJUnit4::class)
class OcrDecoderStateSpikeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val env = OrtEnvironment.getEnvironment()

    /** Logs each page's OCR ms per reader; fails if any crop reads differently or flips [OcrResult.unsure]. */
    @Test
    fun pastKeyValueExportsReadEveryCropLikeTodays() {
        val pages = File(FIXTURE_DIR, "pages").listFiles { f -> f.extension == "jpg" }.orEmpty().sortedBy { it.name }
        check(pages.isNotEmpty()) { "No pages under $FIXTURE_DIR/pages" }
        val vision = stage(Constants.VISION_MODELS_DIR, VISION_FILES)
        val past = stage(PAST_DIR, PAST_FILES)
        val vocab = File(vision, Constants.OCR_VOCAB_FILE).readLines().map { it.trim() }
        val sep = vocab.indexOf("[SEP]")

        val onnx = OnnxRuntime(context)
        val detector = BubbleDetector(onnx).apply { check(loadModel(File(vision, Constants.BUBBLE_DETECTION_MODEL).path)) }
        val current = OcrEngine(onnx).apply {
            check(loadModel(File(vision, Constants.OCR_ENCODER_MODEL).path, File(vision, Constants.OCR_DECODER_MODEL).path, File(vision, Constants.OCR_VOCAB_FILE).path))
        }
        val encoder = session(File(past, "encoder_model.onnx"))
        val merged = MergedDecoder(session(File(past, "decoder_model_merged.onnx")))
        val split = SplitDecoder(session(File(past, "decoder_model.onnx")), session(File(past, "decoder_with_past_model.onnx")))
        val readers = linkedMapOf<String, (Bitmap) -> Read>(
            "current" to { crop -> current.extractText(crop).let { Read(it?.text.orEmpty(), it?.confidence ?: 0f) } },
            "merged" to { crop -> merged.decode(encode(encoder, crop), sep).let { Read(text(it.tokenIds, vocab), it.confidence) } },
            "split" to { crop -> split.decode(encode(encoder, crop), sep).let { Read(text(it.tokenIds, vocab), it.confidence) } }
        )
        try {
            // Warm-up: first runs pay session and allocator setup; excluded like SpeedBenchmarkTest's.
            crops(detector, pages.first()).forEach { crop -> readers.values.forEach { it(crop) } }
            var differing = 0
            var unsureFlips = 0
            for ((index, page) in pages.withIndex()) {
                val crops = crops(detector, page)
                val ms = readers.keys.associateWith { 0L }.toMutableMap()
                for ((cropIndex, crop) in crops.withIndex()) {
                    val reads = readers.mapValues { (name, read) ->
                        val start = System.nanoTime()
                        read(crop).also { ms[name] = ms.getValue(name) + (System.nanoTime() - start) / 1_000_000L }
                    }
                    val same = reads.values.map { it.text }.toSet().size == 1
                    if (!same) differing++
                    // Same text can still differ in fp noise; a crop near 0.85 must not change the pipeline's skip.
                    if (reads.values.map { it.confidence < OcrEngine.MIN_CONFIDENCE }.toSet().size > 1) unsureFlips++
                    Log.i(TAG, "CROP page=${index + 1} crop=${cropIndex + 1} same=$same reads=$reads")
                }
                ms.forEach { (name, total) -> Log.i(TAG, "OCR_TIMING page=${index + 1} crops=${crops.size} reader=$name ms=$total") }
            }
            assertEquals("crops whose reads differ", 0, differing)
            assertEquals("crops whose unsure flag differs", 0, unsureFlips)
        } finally {
            merged.session.close()
            split.first.close()
            split.withPast.close()
            encoder.close()
            onnx.release()
        }
    }

    private fun crops(detector: BubbleDetector, page: File): List<Bitmap> {
        val bitmap = checkNotNull(BitmapFactory.decodeFile(page.absolutePath))
        // Same crop as TranslationPipeline.processPage, at the shipped detector threshold.
        return detector.detect(bitmap, BubbleDetector.DEFAULT_CONFIDENCE_THRESHOLD).map { bubble ->
            val bounds = CropBoundsCalculator.clamp(
                bitmap.width, bitmap.height,
                bubble.boundingBox.left.toInt(), bubble.boundingBox.top.toInt(),
                bubble.boundingBox.width().toInt(), bubble.boundingBox.height().toInt()
            )
            Bitmap.createBitmap(bitmap, bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    /** OcrEngine.preprocessImage: 224 square, RGB planes, (x - 0.5) / 0.5. */
    private fun encode(encoder: OrtSession, crop: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(crop, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE).also { resized.getPixels(it, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE) }
        val plane = INPUT_SIZE * INPUT_SIZE
        val values = FloatArray(3 * plane)
        for (i in pixels.indices) {
            values[i] = ((pixels[i] shr 16 and 0xFF) / 255f - 0.5f) / 0.5f
            values[i + plane] = ((pixels[i] shr 8 and 0xFF) / 255f - 0.5f) / 0.5f
            values[i + 2 * plane] = ((pixels[i] and 0xFF) / 255f - 0.5f) / 0.5f
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(values), longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())).use { input ->
            encoder.run(mapOf("pixel_values" to input)).use { result ->
                val hidden = result.get(0) as OnnxTensor
                return OnnxTensor.createTensor(env, hidden.floatBuffer, hidden.info.shape)
            }
        }
    }

    private data class Read(val text: String, val confidence: Float)

    private data class Decode(val tokenIds: List<Int>, val confidence: Float)

    /** One token in, logits plus present key/values out; greedy from [CLS] like OcrDecoding.decodeGreedy. */
    private abstract inner class PastDecoder {
        abstract fun step(token: Long, hidden: OnnxTensor, past: Map<String, OnnxTensor>?): OrtSession.Result

        fun decode(hidden: OnnxTensor, sep: Int): Decode {
            val ids = mutableListOf<Int>()
            val probabilities = mutableListOf<Float>()
            fun decoded() = Decode(ids, if (probabilities.isEmpty()) 0f else probabilities.average().toFloat())
            val results = mutableListOf<OrtSession.Result>()
            var past: Map<String, OnnxTensor>? = null
            var token = START_TOKEN_ID
            try {
                repeat(MAX_LENGTH) {
                    val result = step(token, hidden, past).also { results += it }
                    val logits = (result.get("logits").get() as OnnxTensor).floatBuffer
                    var best = 0
                    for (i in 1 until logits.limit()) if (logits[i] > logits[best]) best = i
                    // OcrDecoding.chooseToken's softmax probability of the argmax.
                    var sum = 0.0
                    for (i in 0 until logits.limit()) sum += exp((logits[i] - logits[best]).toDouble())
                    probabilities += (1.0 / sum).toFloat()
                    if (best == sep) return decoded()
                    ids += best
                    token = best.toLong()
                    past = nextPast(past, result)
                    // Every past tensor now comes from the latest result or the first one (cross-attention).
                    while (results.size > 2) results.removeAt(1).close()
                }
                return decoded()
            } finally {
                results.forEach { it.close() }
                hidden.close()
            }
        }

        /** present.N.* becomes past_key_values.N.*; an empty present (cache branch's cross-attention) keeps the old one. */
        private fun nextPast(old: Map<String, OnnxTensor>?, result: OrtSession.Result): Map<String, OnnxTensor> =
            result.filter { it.key.startsWith("present.") }.associate { (name, value) ->
                val pastName = name.replace("present.", "past_key_values.")
                val tensor = value as OnnxTensor
                pastName to if (tensor.info.shape.any { it == 0L }) old!!.getValue(pastName) else tensor
            }.let { (old.orEmpty()) + it }

        protected fun ids(token: Long) = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(token)), longArrayOf(1, 1))
    }

    private inner class MergedDecoder(val session: OrtSession) : PastDecoder() {
        private val pastNames = session.inputNames.filter { it.startsWith("past_key_values.") }
        private val empty = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1, 12, 0, 64))
        private val noCache = OnnxTensor.createTensor(env, booleanArrayOf(false))
        private val useCache = OnnxTensor.createTensor(env, booleanArrayOf(true))

        override fun step(token: Long, hidden: OnnxTensor, past: Map<String, OnnxTensor>?): OrtSession.Result = ids(token).use { input ->
            session.run(
                mapOf("input_ids" to input, "encoder_hidden_states" to hidden, "use_cache_branch" to (if (past == null) noCache else useCache)) +
                    pastNames.associateWith { past?.getValue(it) ?: empty }
            )
        }
    }

    private inner class SplitDecoder(val first: OrtSession, val withPast: OrtSession) : PastDecoder() {
        override fun step(token: Long, hidden: OnnxTensor, past: Map<String, OnnxTensor>?): OrtSession.Result = ids(token).use { input ->
            val inputs = mapOf("input_ids" to input, "encoder_hidden_states" to hidden)
            if (past == null) first.run(inputs) else withPast.run(inputs + past)
        }
    }

    private fun text(ids: List<Int>, vocab: List<String>) = buildString {
        for (id in ids) {
            val token = vocab.getOrNull(id) ?: continue
            if (token.length >= 2 && token.startsWith("[") && token.endsWith("]")) continue
            append(token.removePrefix("##"))
        }
    }.trim()

    private fun session(file: File): OrtSession = env.createSession(file.path, OrtSession.SessionOptions())

    private fun stage(subdir: String, names: List<String>): File {
        val target = File(context.filesDir, "${Constants.MODELS_DIR}/$subdir").apply { mkdirs() }
        for (name in names) {
            val source = File(FIXTURE_DIR, "models/$subdir/$name")
            check(source.isFile) { "Missing $source" }
            val dest = File(target, name)
            if (dest.length() == source.length()) continue
            source.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
        return target
    }

    companion object {
        private const val TAG = "OcrSpike"
        private const val FIXTURE_DIR = "/data/local/tmp/yomu-speed"
        private const val PAST_DIR = "ocr-past"
        private const val INPUT_SIZE = 224
        private const val START_TOKEN_ID = 2L
        private const val MAX_LENGTH = 128
        private val VISION_FILES = listOf(
            Constants.BUBBLE_DETECTION_MODEL, Constants.OCR_ENCODER_MODEL, Constants.OCR_DECODER_MODEL, Constants.OCR_VOCAB_FILE
        )
        private val PAST_FILES = listOf("encoder_model.onnx", "decoder_model.onnx", "decoder_with_past_model.onnx", "decoder_model_merged.onnx")
    }
}

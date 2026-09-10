package com.yomu.app

import android.graphics.RectF
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.core.Constants
import com.yomu.core.ModelProfile
import com.yomu.core.TranslationPromptMode
import com.yomu.core.TranslationStatus
import com.yomu.ml.LlamaBridge
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.pipeline.bubble.Bubble
import com.yomu.pipeline.context.ContextAssembler
import com.yomu.pipeline.ocr.OcrResult
import com.yomu.pipeline.translation.TranslationEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Spike for issue #137: does a grammar-constrained page-level batch call beat the shipped per-line
 * path on the ADR-0004 gates, and at what latency and memory?
 *
 * Four arms over the same 17-page corpus, on one model (Qwen2.5-1.5B, the shipped default), with
 * sampler parameters held at today's values throughout — temperature 0.2, top_k 40, top_p 0.9, no
 * repetition penalty, no min_p. The generation-parameters ticket (#139) varies those; letting both
 * move at once would make the result unattributable.
 *
 *  - `qwen_perline`             the shipped path: idKeyedBatch = false, TRANSLATION_ONLY, one native
 *                               call per bubble, sessionContext never read. The paired control.
 *  - `qwen_batch`               ADR-0002's page-level id-keyed call, unconstrained. Present so the
 *                               grammar's contribution is separable from the page-level call's —
 *                               without it, a win could be credited to either.
 *  - `qwen_batch_grammar`       the same call with the reply pinned to `[id] text` at sample time.
 *  - `qwen_batch_grammar_ctx`   and with the previous page's source/translation pairs carried in,
 *                               which is the first time sessionContext has reached a model at all.
 *
 * Deliberately NOT a production change: the grammar rides on a constructor flag, the catalog is
 * untouched, and nothing here is wired into the app.
 *
 * Run one or more arms:
 *   ./gradlew :app:connectedAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.yomu.app.ArchitectureSpikeTest \
 *     -Pandroid.testInstrumentationRunnerArguments.arms=qwen_perline,qwen_batch_grammar
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ArchitectureSpikeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var assembler: ContextAssembler

    @Test
    fun compareArchitectures() = runBlocking {
        val staged = stageQwenFixture()
        checkNotNull(staged) {
            "Qwen fixture missing at $FIXTURE_DIR/${Constants.LLM_MODELS_DIR}/${Constants.QWEN25_15B_MODEL}; " +
                "run eval/run-benchmark.sh so it pushes the weights"
        }
        hiltRule.inject()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cases = loadCases()
        check(cases.isNotEmpty()) { "No benchmark cases staged" }

        val requested = InstrumentationRegistry.getArguments().getString("arms")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        val arms = if (requested.isNullOrEmpty()) ARMS else ARMS.filter { it.name in requested }
        check(arms.isNotEmpty()) { "No arm matched arms=$requested" }

        val outputDir = File(context.filesDir, "yomu-benchmark").apply { mkdirs() }
        val rows = mutableListOf<TimingRow>()
        val native = LlamaBridge(context)

        try {
            for (arm in arms) {
                // All four arms share one GGUF, so the model is loaded once and every later
                // ensureReady() sees it resident. close() would free the native model, so arms only
                // endSession() between them; the release happens once, in the finally.
                val slot = LlamaTranslationBridge(
                    native,
                    ModelProfile(staged.absolutePath, arm.idKeyedBatch, arm.promptMode),
                    useGrammar = arm.grammar
                )
                check(slot.ensureReady()) { "Qwen unavailable for ${arm.name}: ${slot.status}" }
                val engine = TranslationEngine { slot }
                Log.i(TAG, "ARM_START arm=${arm.name} idKeyedBatch=${arm.idKeyedBatch} grammar=${arm.grammar} sessionContext=${arm.sessionContext}")
                runArm(arm, engine, cases, outputDir, rows)
                engine.endSession()
                writeTimingCsv(outputDir, rows)
            }
        } finally {
            writeTimingCsv(outputDir, rows)
            native.release()
        }
    }

    private suspend fun runArm(
        arm: Arm,
        engine: TranslationEngine,
        cases: List<BenchCase>,
        outputDir: File,
        rows: MutableList<TimingRow>
    ) {
        // Carried across cases only for the session-context arm, and built exactly the way
        // OverlayService builds it in production: the previous page's original/translated pairs.
        var carried: List<Pair<String, String>> = emptyList()

        for (case in cases) {
            val bubbles = case.boxes.mapIndexed { id, box ->
                Bubble(id = id, boundingBox = box, confidence = 1f)
            }
            val ocrResults = case.source.mapIndexedNotNull { id, text ->
                if (text.isBlank()) null else id to OcrResult(
                    text = text,
                    confidence = 1f,
                    boundingBox = floatArrayOf(
                        case.boxes[id].left, case.boxes[id].top,
                        case.boxes[id].right, case.boxes[id].bottom
                    )
                )
            }.toMap()

            val page = assembler.assemble(bubbles, ocrResults, case.width, case.height)
            val result = runCatching {
                engine.translate(page.blocks, if (arm.sessionContext) carried else emptyList())
            }.getOrNull()
            val pssKb = Debug.getPss()

            if (arm.sessionContext) {
                carried = result?.translations.orEmpty().map { it.originalText to it.translatedText }
            }

            val byId = result?.translations.orEmpty().associateBy { it.bubbleId }
            val translations = case.source.indices.map { id -> byId[id]?.translatedText ?: "" }

            // A page whose native call came back blank (the silent #58 failure: "" -> null -> an
            // empty PageTranslation) still produces a full-length translations list, because
            // TranslationEngine substitutes the Japanese source. That is exactly the substitution
            // this ticket asks to be checked for, so it is counted here rather than inferred later:
            // an id whose "translation" is its own source text was not translated.
            val untouched = case.source.indices.count { id ->
                val out = byId[id]?.translatedText
                out != null && out == case.source[id]
            }

            rows.add(
                TimingRow(
                    caseId = case.caseId,
                    engine = arm.name,
                    bubbleCount = ocrResults.size,
                    durationMs = result?.translationTimeMs ?: 0L,
                    pssKb = pssKb,
                    success = result != null,
                    sourceEchoes = untouched
                )
            )
            Log.i(
                TAG,
                "Result engine=${arm.name} case=${case.caseId} bubbles=${ocrResults.size} " +
                    "durationMs=${result?.translationTimeMs ?: 0} pssKb=$pssKb covered=${byId.size} " +
                    "sourceEchoes=$untouched"
            )
            Log.i(TAG, "RAW engine=${arm.name} case=${case.caseId} raw=${result?.rawResponse?.replace("\n", "\\n")}")

            writeEngineResult(outputDir, case.caseId, arm.name, translations)
            logEngineResult(case.caseId, arm.name, translations)
        }
    }

    /**
     * Copy the Qwen GGUF from the pushed fixtures into filesDir, where LlamaBridge can read it —
     * SELinux blocks the app UID from reading /data/local/tmp directly. Returns null if the fixture
     * is absent, which the caller turns into a loud failure: silently skipping is how a benchmark
     * ends up green while measuring nothing.
     */
    private fun stageQwenFixture(): File? {
        val source = File("$FIXTURE_DIR/${Constants.LLM_MODELS_DIR}/${Constants.QWEN25_15B_MODEL}")
        if (!source.isFile) return null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = File(
            context.filesDir,
            "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}/${Constants.QWEN25_15B_MODEL}"
        )
        target.parentFile?.mkdirs()
        if (target.exists() && target.length() == source.length()) return target
        return try {
            Log.i(TAG, "Staging Qwen fixture (${source.length()} bytes)")
            source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            target
        } catch (e: Exception) {
            Log.w(TAG, "Staging Qwen fixture failed (${e.message})")
            target.delete()
            null
        }
    }

    private fun loadCases(): List<BenchCase> {
        val assetManager = InstrumentationRegistry.getInstrumentation().context.assets
        val caseIds = assetManager.list("eval-cases")?.sorted() ?: emptyList()
        return caseIds.map { caseId ->
            val files = assetManager.list("eval-cases/$caseId").orEmpty().toSet()
            check("source.txt" in files && "expected.json" in files) {
                "assets/eval-cases/$caseId is missing source.txt or expected.json; run eval/run-benchmark.sh"
            }
            val text = assetManager.open("eval-cases/$caseId/source.txt").use {
                it.bufferedReader().readText()
            }
            val source = text.removeSuffix("\n").lines()
            val expected = assetManager.open("eval-cases/$caseId/expected.json").use {
                JSONObject(it.bufferedReader().readText())
            }
            val boxesJson = expected.getJSONArray("boxes")
            val boxes = (0 until boxesJson.length()).map { i ->
                val b = boxesJson.getJSONObject(i)
                val x = b.getDouble("x").toFloat()
                val y = b.getDouble("y").toFloat()
                RectF(x, y, x + b.getDouble("w").toFloat(), y + b.getDouble("h").toFloat())
            }
            check(boxes.size == source.size) {
                "$caseId: ${boxes.size} boxes but ${source.size} source lines; regenerate cases"
            }
            BenchCase(caseId, source, boxes, expected.getInt("image_width"), expected.getInt("image_height"))
        }
    }

    private fun writeEngineResult(
        outputDir: File,
        caseId: String,
        engineName: String,
        translations: List<String>
    ) {
        val caseDir = File(outputDir, caseId).apply { mkdirs() }
        val json = JSONObject().apply {
            put("engine", engineName)
            put("translations", JSONArray(translations))
        }
        File(caseDir, "$engineName.json").writeText(json.toString(2))
    }

    private fun logEngineResult(caseId: String, engineName: String, translations: List<String>) {
        val json = JSONObject().apply {
            put("engine", engineName)
            put("translations", JSONArray(translations))
        }
        Log.i(TAG, "RESULT_JSON case=$caseId engine=$engineName json=${json.toString()}")
    }

    private fun writeTimingCsv(outputDir: File, rows: List<TimingRow>) {
        File(outputDir, "spike137_timing.csv").bufferedWriter().use { writer ->
            writer.write("case_id,engine,bubble_count,duration_ms,peak_pss_kb,success,source_echoes")
            writer.newLine()
            rows.forEach { row ->
                writer.write(
                    "${row.caseId},${row.engine},${row.bubbleCount},${row.durationMs}," +
                        "${row.pssKb},${row.success},${row.sourceEchoes}"
                )
                writer.newLine()
            }
        }
        rows.forEach { row ->
            Log.i(
                TAG,
                "TIMING case=${row.caseId} engine=${row.engine} bubbles=${row.bubbleCount} " +
                    "durationMs=${row.durationMs} peakPssKb=${row.pssKb} success=${row.success} " +
                    "sourceEchoes=${row.sourceEchoes}"
            )
        }
    }

    private data class BenchCase(
        val caseId: String,
        val source: List<String>,
        val boxes: List<RectF>,
        val width: Int,
        val height: Int
    )

    private data class TimingRow(
        val caseId: String,
        val engine: String,
        val bubbleCount: Int,
        val durationMs: Long,
        val pssKb: Long,
        val success: Boolean,
        val sourceEchoes: Int
    )

    private data class Arm(
        val name: String,
        val idKeyedBatch: Boolean,
        val promptMode: TranslationPromptMode,
        val grammar: Boolean,
        val sessionContext: Boolean
    )

    companion object {
        private const val TAG = "ArchitectureSpikeTest"
        private const val FIXTURE_DIR = "/data/local/tmp/yomu-fixtures"

        private val ARMS = listOf(
            Arm("qwen_perline", false, TranslationPromptMode.TRANSLATION_ONLY, grammar = false, sessionContext = false),
            Arm("qwen_batch", true, TranslationPromptMode.TRANSLATION_ONLY, grammar = false, sessionContext = false),
            Arm("qwen_batch_grammar", true, TranslationPromptMode.TRANSLATION_ONLY, grammar = true, sessionContext = false),
            Arm("qwen_batch_grammar_ctx", true, TranslationPromptMode.TRANSLATION_ONLY, grammar = true, sessionContext = true)
        )
    }
}

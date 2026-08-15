package com.yomu.app

import android.content.Context
import android.graphics.RectF
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.app.translation.TranslationEngineSelector
import com.yomu.app.translation.TranslationEngineType
import com.yomu.core.Constants
import com.yomu.ml.TranslationStatus
import com.yomu.pipeline.bubble.Bubble
import com.yomu.pipeline.context.ContextAssembler
import com.yomu.pipeline.ocr.OcrResult
import com.yomu.pipeline.translation.TranslationEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EngineBenchmarkTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var selector: TranslationEngineSelector

    @Inject
    lateinit var engine: TranslationEngine

    @Inject
    lateinit var assembler: ContextAssembler

    @Before
    fun init() {
        stageModelFixtures()
        hiltRule.inject()
    }

    /**
     * Copy pushed model fixtures into filesDir, where the DI graph expects downloaded models.
     *
     * Installing the APK wipes filesDir, so before this existed every run needed the ~600MB of
     * weights re-downloaded by hand through Settings. /data/local/tmp survives reinstalls, so
     * run-benchmark.sh pushes them once and this restages them on each run. Staging happens before
     * hiltRule.inject() because the engine bridges capture their model paths at construction.
     *
     * Absent fixtures are not an error: the engine simply reports not-ready and is skipped, which
     * is the correct outcome for a machine that has no copy of a given model.
     */
    private fun stageModelFixtures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureRoot = File(FIXTURE_DIR)
        val subdirs = fixtureRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (subdirs.isEmpty()) {
            Log.w(TAG, "No model fixtures under $FIXTURE_DIR; engines needing weights will be skipped")
            return
        }
        for (subdir in subdirs) {
            val target = File(context.filesDir, "${Constants.MODELS_DIR}/${subdir.name}")
            target.mkdirs()
            for (fixture in subdir.listFiles().orEmpty().filter { it.isFile }) {
                val dest = File(target, fixture.name)
                // Re-copying 500MB on every run is pure waste; size is enough to spot a swap.
                if (dest.exists() && dest.length() == fixture.length()) {
                    Log.i(TAG, "Fixture already staged: ${subdir.name}/${fixture.name}")
                    continue
                }
                Log.i(TAG, "Staging fixture ${subdir.name}/${fixture.name} (${fixture.length()} bytes)")
                fixture.inputStream().use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    @Test
    fun benchmarkAllEngines() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cases = loadCases(InstrumentationRegistry.getInstrumentation().context)
            val outputDir = File(context.filesDir, "yomu-benchmark")
            outputDir.mkdirs()

            val timingRows = mutableListOf<TimingRow>()

            for (type in ENGINES) {
                val engineName = engineNameFor(type)
                Log.i(TAG, "Benchmarking engine=$engineName")

                selector.selectEngine(type)
                val ready = runCatching {
                    selector.ensureReady()
                }.getOrElse { false }

                if (!ready) {
                    val reason = (selector.status as? TranslationStatus.Error)?.reason ?: "not_ready"
                    Log.e(TAG, "Engine $engineName not ready, skipping. reason=$reason")
                    continue
                }

                for (case in cases) {
                    // ADR-0004: one page-level call. Ground-truth boxes stand in for detections and
                    // text_ja for OCR; ContextAssembler builds panels + reading order, then the
                    // engine translates the whole page. The LLM batches this (the gate); ML Kit /
                    // OPUS-MT translate per bubble inside the same call (the floor). Output is keyed
                    // by bubble id so a missing id scores zero rather than voiding the case.
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
                    val result = runCatching { engine.translate(page.blocks) }.getOrNull()

                    val byId = result?.translations.orEmpty().associateBy { it.bubbleId }
                    val translations = case.source.indices.map { id -> byId[id]?.translatedText ?: "" }

                    timingRows.add(
                        TimingRow(
                            caseId = case.caseId,
                            engine = engineName,
                            bubbleCount = ocrResults.size,
                            durationMs = result?.translationTimeMs ?: 0L,
                            success = result != null
                        )
                    )
                    Log.i(TAG, "Result engine=$engineName case=${case.caseId} bubbles=${ocrResults.size} durationMs=${result?.translationTimeMs ?: 0} covered=${byId.size}")

                    writeEngineResult(outputDir, case.caseId, engineName, translations)
                    logEngineResult(case.caseId, engineName, translations)
                }

                // Clear the in-memory translation cache between engines. It is keyed by source text
                // only, not by engine, so without this one engine would be served another's cached
                // translations and every per-engine number would be corrupt.
                engine.release()
            }

            writeTimingCsv(outputDir, timingRows)
            logTimingCsv(timingRows)
            selector.close()
            Log.i(TAG, "Benchmark complete. Output: ${outputDir.absolutePath}")
        }
    }

    private fun loadCases(context: Context): List<BenchCase> {
        val assetManager = context.assets
        val caseIds = assetManager.list("eval-cases")?.sorted() ?: emptyList()
        return caseIds.map { caseId ->
            val files = assetManager.list("eval-cases/$caseId").orEmpty().toSet()
            // Half-staged cases are a harness bug, not a case to skip: skipping would quietly
            // shrink the corpus and still exit green. The page-level call needs the boxes too, so
            // expected.json is now required alongside source.txt.
            check("source.txt" in files && "expected.json" in files) {
                "assets/eval-cases/$caseId is missing source.txt or expected.json; run " +
                    "eval/run-benchmark.sh so it stages case data before the build packages the APK"
            }
            val text = assetManager.open("eval-cases/$caseId/source.txt").use {
                it.bufferedReader().readText()
            }
            // The scorer reads source.txt with Python splitlines(), which drops the trailing
            // newline; lines() keeps it as an extra empty line and every case fails alignment.
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
            // Bubble id = box index = source line index (ADR-0004). If they disagree the two
            // annotation projections have drifted, which no per-case handling could paper over.
            check(boxes.size == source.size) {
                "$caseId: ${boxes.size} boxes but ${source.size} source lines; regenerate cases"
            }
            BenchCase(
                caseId = caseId,
                source = source,
                boxes = boxes,
                width = expected.getInt("image_width"),
                height = expected.getInt("image_height")
            )
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
        val file = File(outputDir, "benchmark_timing.csv")
        file.bufferedWriter().use { writer ->
            writer.write("case_id,engine,bubble_count,duration_ms,success")
            writer.newLine()
            rows.forEach { row ->
                writer.write("${row.caseId},${row.engine},${row.bubbleCount},${row.durationMs},${row.success}")
                writer.newLine()
            }
        }
    }

    private fun logTimingCsv(rows: List<TimingRow>) {
        rows.forEach { row ->
            Log.i(TAG, "TIMING case=${row.caseId} engine=${row.engine} bubbles=${row.bubbleCount} durationMs=${row.durationMs} success=${row.success}")
        }
    }

    private fun engineNameFor(type: TranslationEngineType): String = when (type) {
        TranslationEngineType.ML_KIT -> "mlkit"
        TranslationEngineType.OPUS_MT -> "opusmt"
        TranslationEngineType.LLM -> "llm"
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
        val success: Boolean
    )

    companion object {
        private const val TAG = "EngineBenchmarkTest"
        private const val FIXTURE_DIR = "/data/local/tmp/yomu-fixtures"
        private val ENGINES = listOf(
            TranslationEngineType.ML_KIT,
            TranslationEngineType.OPUS_MT,
            TranslationEngineType.LLM
        )
    }
}
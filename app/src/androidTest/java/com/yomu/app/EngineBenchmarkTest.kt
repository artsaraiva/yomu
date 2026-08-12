package com.yomu.app

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.app.translation.TranslationEngineSelector
import com.yomu.app.translation.TranslationEngineType
import com.yomu.ml.TranslationStatus
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

    @Before
    fun init() {
        hiltRule.inject()
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

                for ((caseId, lines) in cases) {
                    val translations = mutableListOf<String>()
                    for ((index, line) in lines.withIndex()) {
                        val output = if (line.isBlank()) {
                            null
                        } else {
                            runCatching { selector.translate(line) }.getOrNull()
                        }
                        val translatedText = output?.translatedText ?: ""
                        translations.add(translatedText)
                        timingRows.add(
                            TimingRow(
                                caseId = caseId,
                                engine = engineName,
                                lineIndex = index,
                                durationMs = output?.durationMs ?: 0L,
                                success = output != null,
                                source = line,
                                translation = translatedText
                            )
                        )
                        Log.i(TAG, "Result engine=$engineName case=$caseId line=$index durationMs=${output?.durationMs ?: 0} source=${line.take(40)} translation=${translatedText.take(80)}")
                    }
                    writeEngineResult(outputDir, caseId, engineName, translations)
                    logEngineResult(caseId, engineName, translations)
                }

                selector.clearMemory()
            }

            writeTimingCsv(outputDir, timingRows)
            logTimingCsv(timingRows)
            selector.close()
            Log.i(TAG, "Benchmark complete. Output: ${outputDir.absolutePath}")
        }
    }

    private fun loadCases(context: Context): List<Pair<String, List<String>>> {
        val assetManager = context.assets
        val caseIds = assetManager.list("eval-cases")?.sorted() ?: emptyList()
        return caseIds.map { caseId ->
            val text = assetManager.open("eval-cases/$caseId/source.txt").use {
                it.bufferedReader().readText()
            }
            caseId to text.lines()
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
            writer.write("case_id,engine,line_index,duration_ms,success,source,translation")
            writer.newLine()
            rows.forEach { row ->
                val escapedSource = row.source.replace(",", ";").replace("\n", " ")
                val escapedTranslation = row.translation.replace(",", ";").replace("\n", " ")
                writer.write("${row.caseId},${row.engine},${row.lineIndex},${row.durationMs},${row.success},$escapedSource,$escapedTranslation")
                writer.newLine()
            }
        }
    }

    private fun logTimingCsv(rows: List<TimingRow>) {
        rows.forEach { row ->
            Log.i(TAG, "TIMING case=${row.caseId} engine=${row.engine} line=${row.lineIndex} durationMs=${row.durationMs} success=${row.success}")
        }
    }

    private fun engineNameFor(type: TranslationEngineType): String = when (type) {
        TranslationEngineType.ML_KIT -> "mlkit"
        TranslationEngineType.OPUS_MT -> "opusmt"
        TranslationEngineType.LLM -> "llm"
    }

    private data class TimingRow(
        val caseId: String,
        val engine: String,
        val lineIndex: Int,
        val durationMs: Long,
        val success: Boolean,
        val source: String,
        val translation: String
    )

    companion object {
        private const val TAG = "EngineBenchmarkTest"
        private val ENGINES = listOf(
            TranslationEngineType.ML_KIT,
            TranslationEngineType.OPUS_MT,
            TranslationEngineType.LLM
        )
    }
}
package com.yomu.app

import android.content.Context
import android.graphics.RectF
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.app.translation.EngineSelection
import com.yomu.app.translation.TranslationEngineType
import com.yomu.core.Constants
import com.yomu.core.GenerationParams
import com.yomu.core.ModelProfile
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationPromptMode
import com.yomu.core.TranslationStatus
import com.yomu.app.translation.LlmModelCatalog
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EngineBenchmarkTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var selector: EngineSelection

    @Inject
    lateinit var engine: TranslationEngine

    @Inject
    lateinit var assembler: ContextAssembler

    // Injected only to free the 0.8b's native model before the #84 challenger loop loads a
    // different GGUF: the native side holds one model at a time, so the incumbent must be released
    // first or the first challenger's load races a still-resident model.
    @Inject
    lateinit var llamaBridge: LlamaTranslationBridge

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
                // #84 challengers are multi-GB and staged one at a time in benchmarkChallengers
                // (then deleted), so copying them all here would need ~8GB in filesDir on top of the
                // same ~8GB already in /data/local/tmp and blows the partition (ENOSPC). Skip them.
                if (fixture.name in CHALLENGER_FILE_NAMES) continue
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
    fun comparePromptModes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cases = loadCases(InstrumentationRegistry.getInstrumentation().context)
        check(cases.isNotEmpty()) { "No benchmark cases staged" }
        val outputDir = File(context.filesDir, "yomu-prompt-benchmark")
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        val rows = mutableListOf<TimingRow>()
        val native = LlamaBridge(context)
        val modelPath = File(
            context.filesDir,
            "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}/${LlmModelCatalog.DEFAULT.ggufFileName}"
        ).absolutePath
        try {
            for (mode in TranslationPromptMode.entries) {
                val slot = LlamaTranslationBridge(native, ModelProfile(modelPath, false, mode))
                check(slot.ensureReady()) { "Qwen model unavailable: ${slot.status}" }
                val measured = TranslationEngine { slot }
                runEngineOverCases(measured, "qwen_" + mode.name.lowercase(), cases, outputDir, rows)
                measured.endSession()
                slot.close()
                writeTimingCsv(outputDir, rows)
            }
            check(rows.size == cases.size * TranslationPromptMode.entries.size)
        } finally {
            writeTimingCsv(outputDir, rows)
            native.release()
        }
    }

    /**
     * #153: measure `penalty_repeat` against the gate #139 pre-registered before any number existed.
     *
     * Two things are measured per arm and they need different granularity, so both run here off one
     * model load: the 17-page gate corpus (the benefit clause) and the #152 repetition probe (the
     * no-harm clause, 22 targeted bubbles the gate corpus provably cannot see).
     *
     * [PENALTY_ARMS] carries 1.0 as well as the candidate, even though 1.0 is today's shipped
     * default and #137 already published a `qwen_perline` row for it: that row was measured on the
     * reference phone, and a delta taken against a different device's numbers measures the device.
     * The paired control is the 1.0 arm from this same run.
     *
     * The seed is pinned for the same reason. Shipped default is LLAMA_DEFAULT_SEED — a fresh random
     * draw per generate — so two arms would differ by sampling noise as well as by the penalty.
     */
    @Test
    fun measureRepeatPenalty() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = InstrumentationRegistry.getInstrumentation().context
        val cases = loadCases(assets)
        val probe = loadProbeBubbles(assets)
        check(cases.isNotEmpty()) { "No benchmark cases staged" }
        val outputDir = File(context.filesDir, "yomu-penalty").apply {
            deleteRecursively()
            mkdirs()
        }
        val rows = mutableListOf<TimingRow>()
        val native = LlamaBridge(context)
        val option = LlmModelCatalog.DEFAULT
        // The default model is also a #84 challenger, so stageModelFixtures deliberately skips it
        // (bulk-copying every challenger ENOSPCs filesDir) and it is staged one at a time instead.
        val modelPath = checkNotNull(stageChallengerFixture(option.ggufFileName)) {
            "${option.ggufFileName} is not in $FIXTURE_DIR; run eval/run-benchmark.sh to push it"
        }.absolutePath
        try {
            for (penalty in PENALTY_ARMS) {
                val arm = armName(penalty)
                val slot = LlamaTranslationBridge(
                    native,
                    ModelProfile(
                        modelPath = modelPath,
                        idKeyedBatch = option.idKeyedBatch,
                        promptMode = option.promptMode,
                        generation = GenerationParams(
                            penaltyRepeat = penalty,
                            seed = MEASUREMENT_SEED
                        )
                    )
                )
                check(slot.ensureReady()) { "Qwen model unavailable: ${slot.status}" }
                Log.i(TAG, "Penalty arm=$arm probe bubbles=${probe.size} cases=${cases.size}")
                runProbe(slot, arm, probe, outputDir)
                // The gate corpus only carries the candidate and its control. 1.2 is a probe-only
                // arm: it exists to locate the harm threshold, and #139 pre-registered one gate arm.
                if (penalty in GATE_ARMS) {
                    val measured = TranslationEngine { slot }
                    runEngineOverCases(measured, arm, cases, outputDir, rows)
                    measured.endSession()
                }
                slot.close()
                writeTimingCsv(outputDir, rows)
            }
            check(rows.size == cases.size * GATE_ARMS.size)
        } finally {
            writeTimingCsv(outputDir, rows)
            logTimingCsv(rows)
            native.release()
        }
    }

    /**
     * Run the #152 probe bubbles one per call, which is what the shipped per-line path does anyway.
     *
     * This goes through the slot rather than [TranslationEngine] on purpose: the engine substitutes
     * `bubble.sourceText` when a slot returns nothing, and on this set that substitution would hand
     * the scorer the Japanese source — whose repeated run is intact by construction — and hide
     * exactly the harm the probe exists to catch. A dropped bubble must reach the scorer as "".
     */
    private suspend fun runProbe(
        slot: LlamaTranslationBridge,
        arm: String,
        probe: List<String>,
        outputDir: File
    ) {
        val translations = probe.mapIndexed { id, source ->
            val page = TranslatablePage(listOf(listOf(TranslatableBubble(id, source))))
            val result = runCatching { slot.translatePage(page, emptyList()) }.getOrNull()
            val text = result?.byId?.get(id).orEmpty()
            Log.i(TAG, "Probe arm=$arm bubble=$id durationMs=${result?.durationMs ?: 0} out=$text")
            text
        }
        slot.endSession()
        // Written as well as logged: a looping arm emits multi-kilobyte lines and logcat's chatty
        // filter drops them, so the log alone is not a reliable transport for this set.
        writeEngineResult(outputDir, PROBE_CASE_ID, arm, translations)
        logEngineResult(PROBE_CASE_ID, arm, translations)
    }

    /** The probe's source lines, in bubble-id order. Staged into assets by run-benchmark.sh. */
    private fun loadProbeBubbles(context: Context): List<String> {
        val text = context.assets.open("$PROBE_ASSET_DIR/bubbles.json").use {
            it.bufferedReader().readText()
        }
        val bubbles = JSONObject(text).getJSONArray("bubbles")
        return (0 until bubbles.length()).map { bubbles.getJSONObject(it).getString("source") }
    }

    /** Arm name, also the `actual/<engine>.json` stem: `repeat_1.1`, not `repeat_1.1000000238`.
     *  Locale.ROOT because a device in a comma-decimal locale would otherwise name it `repeat_1,1`. */
    private fun armName(penalty: Float): String = String.format(Locale.ROOT, "repeat_%.1f", penalty)

    @Test
    fun benchmarkAllEngines() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cases = loadCases(InstrumentationRegistry.getInstrumentation().context)
            val outputDir = File(context.filesDir, "yomu-benchmark")
            outputDir.mkdirs()

            val timingRows = mutableListOf<TimingRow>()
            // The whole instrumentation test must finish inside the harness's ~20-min ceiling or it
            // is killed and no results are written. The enum engines + 0.8b are the baseline and run
            // unbounded; the challenger phase is capped so a slow/thinking model (Qwen3 ran to the
            // 120s batch timeout on every case) can't starve the rest and abort the whole run before
            // writeTimingCsv. Deadline is absolute wall-clock from test start.
            val benchDeadlineMs = System.currentTimeMillis() + TOTAL_BENCHMARK_BUDGET_MS

            // A heavy challenger's 17 cases won't fit the timeout window after the ~11-min enum
            // baseline. `-P...runnerArguments.skipBaseline=true` skips the enum engines so a focused
            // challenger run gets the whole window; the baseline is deterministic, so reuse a prior
            // run's mlkit/opusmt/llm numbers for the comparison.
            val skipBaseline = InstrumentationRegistry.getArguments().getString("skipBaseline") == "true"

            for (type in if (skipBaseline) emptyList() else ENGINES) {
                val engineName = engineNameFor(type)
                Log.i(TAG, "Benchmarking engine=$engineName")

                selector.selectEngine(type)
                val slot = selector.current()
                val ready = runCatching {
                    slot.ensureReady()
                }.getOrElse { false }

                if (!ready) {
                    val reason = (slot.status as? TranslationStatus.Error)?.reason ?: "not_ready"
                    Log.e(TAG, "Engine $engineName not ready, skipping. reason=$reason")
                    continue
                }

                runEngineOverCases(engine, engineName, cases, outputDir, timingRows)

                engine.endSession()
            }

            benchmarkChallengers(context, cases, outputDir, timingRows, benchDeadlineMs)

            writeTimingCsv(outputDir, timingRows)
            logTimingCsv(timingRows)
            selector.close()
            Log.i(TAG, "Benchmark complete. Output: ${outputDir.absolutePath}")
        }
    }

    /**
     * Run one engine over every case, appending a timing row per case. Shared by the enum engines
     * and the #84 challengers so both are measured identically: same panel assembly, same id-keyed
     * output mapping, same latency + peak-RAM sampling. Peak RAM is a total-PSS sample taken right
     * after each page translate (model weights + KV cache resident); the per-engine peak is the max
     * over its rows. ponytail: single post-translate sample, not a continuous sampler — enough to
     * rank the size/RAM trade-off; add a sampling thread if a mid-generation spike needs catching.
     *
     * [deadlineMs] caps total wall-clock: the loop stops before starting a case once the deadline
     * passes, leaving the already-run cases recorded. Checked between cases, not mid-generation —
     * the native generate call is blocking and uncancellable, so an in-flight case always finishes
     * (bounded by the bridge's own BATCH_TIMEOUT_MS). Enum engines pass MAX_VALUE (unbounded).
     */
    private suspend fun runEngineOverCases(
        engine: TranslationEngine,
        engineName: String,
        cases: List<BenchCase>,
        outputDir: File,
        timingRows: MutableList<TimingRow>,
        deadlineMs: Long = Long.MAX_VALUE
    ) {
        for ((index, case) in cases.withIndex()) {
            if (System.currentTimeMillis() >= deadlineMs) {
                Log.w(TAG, "Budget exhausted for engine=$engineName; ${cases.size - index} of ${cases.size} cases unrun")
                break
            }
            // ADR-0004: one reader trigger per page. Ground-truth boxes stand in for detections and
            // text_ja for OCR; ContextAssembler builds panels + reading order, then the engine
            // translates the whole page. A gate LLM issues the id-keyed page-level call; ML Kit /
            // OPUS-MT translate per bubble (the floor). Output is keyed by bubble id so a missing id
            // scores zero rather than voiding the case.
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
            val pssKb = Debug.getPss()

            val byId = result?.translations.orEmpty().associateBy { it.bubbleId }
            val translations = case.source.indices.map { id -> byId[id]?.translatedText ?: "" }

            timingRows.add(
                TimingRow(
                    caseId = case.caseId,
                    engine = engineName,
                    bubbleCount = ocrResults.size,
                    durationMs = result?.translationTimeMs ?: 0L,
                    pssKb = pssKb,
                    success = result != null
                )
            )
            Log.i(TAG, "Result engine=$engineName case=${case.caseId} bubbles=${ocrResults.size} durationMs=${result?.translationTimeMs ?: 0} pssKb=$pssKb covered=${byId.size}")

            writeEngineResult(outputDir, case.caseId, engineName, translations)
            logEngineResult(case.caseId, engineName, translations)
        }
    }

    /**
     * Load each #84 challenger GGUF in turn and run it on the id-keyed batch path. The native side
     * holds one model at a time, so the injected 0.8b is released first and each challenger is
     * released before the next loads. A challenger whose fixture is absent (or whose weights fail to
     * load) is skipped, not failed — mirroring stageModelFixtures's clean degradation.
     */
    private suspend fun benchmarkChallengers(
        context: Context,
        cases: List<BenchCase>,
        outputDir: File,
        timingRows: MutableList<TimingRow>,
        benchDeadlineMs: Long
    ) {
        // Free the incumbent's native model so the first challenger loads into a clear slot.
        runCatching { llamaBridge.close() }

        // Optional subset: `-Pandroid.testInstrumentationRunnerArguments.challengers=hunyuan_mt_7b,cat_translate_1.4b`
        // runs only those (comma-separated engine names). Four heavy LLMs rarely fit one timeout
        // window, so this lets a run target the models that matter and skip a known-unfit one.
        val only = InstrumentationRegistry.getArguments().getString("challengers")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        val candidates = if (only.isNullOrEmpty()) LLM_CANDIDATES else LLM_CANDIDATES.filter { it.engineName in only }

        val benchLlama = LlamaBridge(context)
        for (candidate in candidates) {
            if (System.currentTimeMillis() >= benchDeadlineMs) {
                Log.w(TAG, "Benchmark budget exhausted; skipping remaining challengers from ${candidate.engineName}")
                break
            }
            // Stage this one challenger's GGUF into filesDir, then delete it after the run. Staging
            // all of them upfront would need ~8GB alongside the same weights in /data/local/tmp and
            // ENOSPCs the partition, so peak usage is bounded to the 0.8b plus one challenger.
            val staged = stageChallengerFixture(candidate.fileName)
            if (staged == null) {
                Log.w(TAG, "Challenger ${candidate.engineName} fixture unavailable, skipping.")
                continue
            }
            try {
                val bridge = LlamaTranslationBridge(
                    benchLlama,
                    ModelProfile(staged.absolutePath, true, TranslationPromptMode.MODEL_CARD)
                )
                val ready = runCatching { bridge.ensureReady() }.getOrElse { false }
                if (!ready) {
                    val reason = (bridge.status as? TranslationStatus.Error)?.reason ?: "not_ready"
                    Log.w(TAG, "Challenger ${candidate.engineName} not ready, skipping. reason=$reason")
                    continue
                }

                // Cap this challenger's slice so one slow model (Qwen3 ran to the 120s batch timeout
                // every case) can't consume the whole remaining budget and starve the others.
                val challengerDeadline = minOf(
                    System.currentTimeMillis() + PER_CHALLENGER_BUDGET_MS,
                    benchDeadlineMs
                )
                Log.i(TAG, "Benchmarking challenger=${candidate.engineName}")
                val challengerEngine = TranslationEngine { bridge }
                runEngineOverCases(challengerEngine, candidate.engineName, cases, outputDir, timingRows, challengerDeadline)
                challengerEngine.endSession()
            } finally {
                // Free native weights before the next challenger's load; the shared benchLlama is
                // reused. Delete the staged copy so the next challenger has room.
                benchLlama.release()
                staged.delete()
            }
        }
    }

    /**
     * Copy one challenger GGUF from the pushed fixtures into filesDir, where LlamaBridge can read it
     * (SELinux blocks the app UID from reading /data/local/tmp directly, which is why staging
     * copies rather than loads in place). Returns null — a clean skip — if the fixture is absent or
     * the copy fails (e.g. ENOSPC), matching stageModelFixtures's degrade-cleanly contract.
     */
    private fun stageChallengerFixture(fileName: String): File? {
        val source = File("$FIXTURE_DIR/${Constants.LLM_MODELS_DIR}/$fileName")
        if (!source.isFile) return null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = File(
            context.filesDir,
            "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}/$fileName"
        )
        target.parentFile?.mkdirs()
        if (target.exists() && target.length() == source.length()) return target
        return try {
            Log.i(TAG, "Staging challenger fixture $fileName (${source.length()} bytes)")
            source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            target
        } catch (e: Exception) {
            Log.w(TAG, "Staging challenger fixture $fileName failed (${e.message}); skipping.")
            target.delete()
            null
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
            writer.write("case_id,engine,bubble_count,duration_ms,peak_pss_kb,success")
            writer.newLine()
            rows.forEach { row ->
                writer.write("${row.caseId},${row.engine},${row.bubbleCount},${row.durationMs},${row.pssKb},${row.success}")
                writer.newLine()
            }
        }
    }

    private fun logTimingCsv(rows: List<TimingRow>) {
        rows.forEach { row ->
            Log.i(TAG, "TIMING case=${row.caseId} engine=${row.engine} bubbles=${row.bubbleCount} durationMs=${row.durationMs} peakPssKb=${row.pssKb} success=${row.success}")
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
        val pssKb: Long,
        val success: Boolean
    )

    // A #84 challenger: the benchmark engine name (also its actual/<name>.json + CSV engine key,
    // so filesystem-safe) and the GGUF file name staged under models/llm/. Distinct from the enum
    // engines because these are loaded standalone on the id-keyed batch path, not via the selector.
    private data class Candidate(
        val engineName: String,
        val fileName: String
    )

    companion object {
        private const val TAG = "EngineBenchmarkTest"
        private const val FIXTURE_DIR = "/data/local/tmp/yomu-fixtures"
        private val ENGINES = listOf(
            TranslationEngineType.ML_KIT,
            TranslationEngineType.OPUS_MT,
            TranslationEngineType.LLM
        )

        // #84 challengers, scored on the id-keyed batch path alongside the 0.8b baseline. Engine
        // names are the run_eval_lib gate-role keys (anything not mlkit/opusmt is a gate LLM). File
        // names match the fixtures pushed by run-benchmark.sh and the ModelManager catalog entries.
        private val LLM_CANDIDATES = listOf(
            Candidate("cat_translate_1.4b", Constants.CAT_TRANSLATION_14B_MODEL),
            Candidate("cat_translate_1.4b_i1", Constants.CAT_TRANSLATION_14B_I1_MODEL),
            Candidate("cat_translate_7b", Constants.CAT_TRANSLATION_7B_MODEL),
            Candidate("translategemma_4b", Constants.TRANSLATEGEMMA_4B_MODEL),
            Candidate("qwen25_1.5b", Constants.QWEN25_15B_MODEL),
            Candidate("gemma2_2b", Constants.GEMMA2_2B_MODEL),
            Candidate("qwen3_4b", Constants.QWEN3_MODEL),
            Candidate("hunyuan_mt_7b", Constants.HUNYUAN_MT_MODEL)
        )

        // Staged one at a time by benchmarkChallengers, so stageModelFixtures must not bulk-copy
        // them upfront (ENOSPC). The 0.8b shares the llm/ subdir but is not here, so it still stages.
        private val CHALLENGER_FILE_NAMES = LLM_CANDIDATES.map { it.fileName }.toSet()

        // The whole test must land inside the harness's ~20-min connected-test ceiling. The enum
        // baseline runs unbounded (~5 min); the challenger phase stops starting/continuing work past
        // the total budget, and each challenger gets a bounded slice so one slow model can't eat it
        // all. Four heavy LLMs won't all fit one window — use the `challengers` arg to split runs, or
        // raise these (and the harness timeout) for a single full pass.
        // #153's sweep. 1.0 is the paired control, not a formality: #137's published control row was
        // measured on the reference phone, so it cannot be differenced against a run on any other
        // device. 1.2 is koharu's value — the probe carries it to find where harm starts, so the
        // threshold that blocks a value is measured rather than asserted.
        private val PENALTY_ARMS = listOf(1.0f, 1.1f, 1.2f)
        private val GATE_ARMS = setOf(1.0f, 1.1f)

        // Pinned so the arms differ by the penalty alone. The shipped default seed is
        // LLAMA_DEFAULT_SEED (a fresh draw per call), which would put sampling noise in the delta.
        private const val MEASUREMENT_SEED = 0

        private const val PROBE_ASSET_DIR = "eval-probe"

        // The probe is bubble-granular, not page-granular, so it has no case id of its own; this is
        // the key its RESULT_JSON line carries so the extractor can route it to repetition-probe/.
        private const val PROBE_CASE_ID = "repetition-probe"

        private const val TOTAL_BENCHMARK_BUDGET_MS = 17L * 60_000L
        private const val PER_CHALLENGER_BUDGET_MS = 6L * 60_000L
    }
}

package com.yomu.app

import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.core.GenerationParams
import com.yomu.core.TranslationOutcome
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The device half of the structured eval run contract (#165, graduating #142).
 *
 * One terminal JSONL record per arm/case/stage invocation, appended to
 * `files/yomu-benchmark/<run-id>/records.jsonl`. `run-benchmark.sh` extracts that directory with
 * `adb exec-out run-as ... tar`, writes `COMPLETE`, and `eval/run_records.py` validates it against
 * the manifest the host wrote *before* the run.
 *
 * Two properties matter more than the format:
 *
 * * **Nothing here is parsed back out of a log.** Logcat stays diagnostic; its chatty filter drops
 *   multi-kilobyte lines, which silently lost 12 of 22 probe bubbles once (#152).
 * * **Observed metadata is read at the execution boundary**, not restated from what the harness
 *   thinks it configured. The host declares the same fields independently in the manifest and the
 *   scorer requires them to agree — manifest-only call-shape metadata is the provenance mistake
 *   ADR-0010 corrected.
 *
 * The run id arrives as an instrumentation argument. A run started by hand without one lands in
 * `local/`, which the host never extracts, so an ad-hoc `connectedAndroidTest` still works and can
 * never be mistaken for a planned run.
 */
class RunRecords private constructor(val runId: String, private val file: File) {

    @Synchronized
    fun append(record: JSONObject) {
        file.appendText(record.toString() + "\n")
    }

    fun detection(
        arm: ArmMeta,
        caseId: String,
        outcome: TranslationOutcome,
        durationMs: Long,
        pageWidth: Int,
        pageHeight: Int,
        boxes: JSONArray,
        nmsThresholded: Int,
        nmsKept: Int,
        errorCode: String? = null
    ) = append(
        base(arm.armId, caseId, STAGE_DETECTION, outcome, durationMs, errorCode).apply {
            put("page_width", pageWidth)
            put("page_height", pageHeight)
            put("boxes", boxes)
            put("nms_thresholded", nmsThresholded)
            put("nms_kept", nmsKept)
            put("observed", arm.observed())
        }
    )

    fun contextAssembly(
        armId: String,
        caseId: String,
        outcome: TranslationOutcome,
        durationMs: Long,
        inputIds: List<Int>,
        blockIds: List<List<Int>>,
        errorCode: String? = null
    ) = append(
        base(armId, caseId, STAGE_CONTEXT, outcome, durationMs, errorCode).apply {
            put("input_ids", JSONArray(inputIds))
            put("output_block_ids", JSONArray(blockIds.map { JSONArray(it) }))
        }
    )

    fun translation(
        arm: ArmMeta,
        caseId: String,
        outcome: TranslationOutcome,
        durationMs: Long,
        requestedIds: List<Int>,
        /** Raw provider results, before TranslationEngine substitutes source text (#137). */
        rawById: Map<Int, String>,
        errorCode: String? = null
    ) = append(
        base(arm.armId, caseId, STAGE_TRANSLATION, outcome, durationMs, errorCode).apply {
            put("requested_ids", JSONArray(requestedIds))
            put(
                "results",
                JSONArray(
                    rawById.map { (id, text) ->
                        JSONObject().put("bubble_id", id).put("text", text)
                    }
                )
            )
            put("observed", arm.observed())
        }
    )

    private fun base(
        armId: String,
        caseId: String,
        stage: String,
        outcome: TranslationOutcome,
        durationMs: Long,
        errorCode: String?
    ) = JSONObject().apply {
        put("run_id", runId)
        put("arm_id", armId)
        put("case_id", caseId)
        put("stage", stage)
        put("outcome", outcome.name.lowercase())
        put("duration_ms", durationMs)
        if (errorCode != null) put("error_code", errorCode)
    }

    companion object {
        const val STAGE_DETECTION = "detection"
        const val STAGE_CONTEXT = "context_assembly"
        const val STAGE_TRANSLATION = "translation"

        /** The probe is bubble-granular, so it rides the translation transport under its own id. */
        const val PROBE_CASE_ID = "repetition-probe"

        const val BENCHMARK_DIR = "yomu-benchmark"

        fun open(): RunRecords {
            val runId = InstrumentationRegistry.getArguments().getString("runId") ?: "local"
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dir = File(context.filesDir, "$BENCHMARK_DIR/$runId").apply { mkdirs() }
            // Appended, never truncated: two test classes contribute records to the same run, and
            // the run id is unique per run, so there is nothing to clear.
            return RunRecords(runId, File(dir, "records.jsonl"))
        }
    }
}

/**
 * One arm's identity as the device observed it. Field names match `manifest.json`, because the
 * scorer compares them key by key.
 */
data class ArmMeta(
    val armId: String,
    val provider: String,
    val modelId: String,
    val quantization: String,
    val callShape: String,
    val targetLanguage: String? = "en",
    val generation: GenerationParams? = null
) {
    fun observed(): JSONObject = JSONObject().apply {
        put("provider", provider)
        put("model_id", modelId)
        put("quantization", quantization)
        put("target_language", targetLanguage ?: JSONObject.NULL)
        put("call_shape", callShape)
        if (generation != null) put("generation", generationJson(generation))
    }

    companion object {
        const val CALL_SHAPE_BATCH = "id_keyed_batch"
        const val CALL_SHAPE_PER_LINE = "per_line"
        const val CALL_SHAPE_PER_BUBBLE = "per_bubble"
        const val CALL_SHAPE_PAGE_IMAGE = "page_image"

        /** Snake_case to match the manifest's `gen.<name>` keys, which the host writes in Python. */
        fun generationJson(params: GenerationParams): JSONObject = JSONObject().apply {
            put("temperature", params.temperature.toDouble())
            put("top_k", params.topK)
            put("top_p", params.topP.toDouble())
            put("penalty_repeat", params.penaltyRepeat.toDouble())
            put("penalty_last_n", params.penaltyLastN)
            put("penalty_freq", params.penaltyFreq.toDouble())
            put("penalty_present", params.penaltyPresent.toDouble())
            // Configured cap, not a token count: no runtime here returns real prompt/completion
            // token counts, and character lengths must not be called tokens (#142).
            put("max_output_tokens", params.maxTokens)
            put("seed", params.seed)
        }

        /**
         * Quantization read off the GGUF file name, which is the only place it is stated on device.
         * `qwen25_1.5b_instruct_q4_k_m.gguf` -> `Q4_K_M`; an unrecognised name is `unknown` rather
         * than a guess, so the manifest comparison fails loudly instead of matching by accident.
         */
        fun quantizationOf(fileName: String): String =
            Regex("""(i1[_-])?q\d+(_[0kK])?(_[a-zA-Z])?""")
                .find(fileName.lowercase())
                ?.value
                ?.uppercase()
                ?: "unknown"
    }
}

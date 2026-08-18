package com.yomu.app.translation

import com.yomu.core.Constants

/**
 * How a curated LLM reaches the device (ADR-0009 three-tier split). Licence sorts a model into a
 * tier; it does not exclude it.
 */
enum class LlmModelTier {
    /** Apache/redistributable — Yomu serves it through the existing pinned-URL [ModelManager] flow. */
    HOSTED,

    /**
     * Yomu-tested but not redistributable (Gemma Terms). Pulled through the HF API under the user's
     * own credentials. The HF-auth download path is not wired yet (blocked on HF OAuth app
     * registration, #90 part C), so these are surfaced but not selectable.
     */
    HF_AUTH
}

/**
 * One curated LLM the user may put in the translation slot under [TranslationEngineType.LLM]
 * (ADR-0009). The catalog is the single source of truth linking a persisted model id to the GGUF
 * that drives [com.yomu.ml.LlamaTranslationBridge] and to its per-model [idKeyedBatch] capability
 * (#84) — killing the old hardcoded 0.8b path.
 */
data class LlmModelOption(
    val id: String,
    val displayName: String,
    val ggufFileName: String,
    val sizeBytes: Long,
    val tier: LlmModelTier,
    /** Per-model (#84): the 0.8b/Qwen default refuse page context and stay per-line (false); larger
     *  siblings can emit one id-keyed reply for the whole page (true). */
    val idKeyedBatch: Boolean
)

object LlmModelCatalog {

    // Resident cost of everything held alongside the LLM (detection + OCR + app), added to the GGUF
    // file size to predict peak footprint. ponytail: fixed estimate, not GGUF-parsed; ADR-0001's
    // exact param×quant math can replace it if the coarse fit gate proves wrong on a real device.
    const val RESIDENT_OVERHEAD_BYTES = 800L * 1024 * 1024

    // Fraction of device totalMem an app can realistically use for weights before the OS OOM-kills
    // it. Calibrated to #84: Hunyuan-7B Q4 (4.6GB) is killed on an 8GB phone, its Q3 (3.8GB) fits.
    // ponytail: single tuning knob — raise if capable devices wrongly hide a model, lower if one
    // that OOMs still shows.
    const val USABLE_RAM_FRACTION = 0.6

    /** ADR-0010: phone-confirmed default. Picking nothing keeps this; it is never gated out (part D). */
    val DEFAULT: LlmModelOption = LlmModelOption(
        id = Constants.QWEN25_15B_MODEL_ID,
        displayName = "Qwen2.5 1.5B Instruct",
        ggufFileName = Constants.QWEN25_15B_MODEL,
        sizeBytes = Constants.QWEN25_15B_SIZE,
        tier = LlmModelTier.HOSTED,
        idKeyedBatch = false
    )

    /**
     * The curated selectable shortlist. HOSTED entries are selectable today; HF_AUTH entries are
     * listed for transparency but disabled until the HF-auth path ships (#90 part C). The open
     * "Custom — unsupported" slot (ADR-0001) is a separate escape hatch, not part of this shortlist.
     */
    val ALL: List<LlmModelOption> = listOf(
        DEFAULT,
        LlmModelOption(
            id = Constants.CAT_TRANSLATION_MODEL_ID,
            displayName = "CAT-Translate 0.8B (low-storage floor)",
            ggufFileName = Constants.TRANSLATION_MODEL_4BIT,
            sizeBytes = Constants.TRANSLATION_MODEL_4BIT_SIZE,
            tier = LlmModelTier.HOSTED,
            idKeyedBatch = false
        ),
        LlmModelOption(
            id = Constants.CAT_TRANSLATION_14B_MODEL_ID,
            displayName = "CAT-Translate 1.4B",
            ggufFileName = Constants.CAT_TRANSLATION_14B_MODEL,
            sizeBytes = Constants.CAT_TRANSLATION_14B_SIZE,
            tier = LlmModelTier.HOSTED,
            idKeyedBatch = true
        ),
        // Gemma Terms — Yomu cannot redistribute, so these ship through the user's own HF account.
        LlmModelOption(
            id = Constants.GEMMA2_2B_MODEL_ID,
            displayName = "Gemma 2 2B Instruct",
            ggufFileName = Constants.GEMMA2_2B_MODEL,
            sizeBytes = Constants.GEMMA2_2B_SIZE,
            tier = LlmModelTier.HF_AUTH,
            idKeyedBatch = true
        ),
        LlmModelOption(
            id = Constants.TRANSLATEGEMMA_4B_MODEL_ID,
            displayName = "TranslateGemma 4B",
            ggufFileName = Constants.TRANSLATEGEMMA_4B_MODEL,
            sizeBytes = Constants.TRANSLATEGEMMA_4B_SIZE,
            tier = LlmModelTier.HF_AUTH,
            idKeyedBatch = true
        )
    )

    fun fromId(id: String?): LlmModelOption? = id?.let { key -> ALL.firstOrNull { it.id == key } }

    /** The selected option, or the default when nothing (or an unknown id) is persisted. */
    fun selectedOrDefault(id: String?): LlmModelOption = fromId(id) ?: DEFAULT

    /**
     * Whether [option] can run on a device reporting [totalMemBytes] of RAM (part D). The default is
     * never gated out — it must stay usable on the mid-range floor.
     */
    fun canRunOnDevice(option: LlmModelOption, totalMemBytes: Long): Boolean {
        if (option.id == DEFAULT.id) return true
        val budget = (totalMemBytes * USABLE_RAM_FRACTION).toLong()
        return option.sizeBytes + RESIDENT_OVERHEAD_BYTES <= budget
    }
}

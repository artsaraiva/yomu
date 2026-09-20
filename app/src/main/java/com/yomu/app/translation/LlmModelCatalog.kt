package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.core.GenerationParams
import com.yomu.core.ModelProfile
import com.yomu.core.RuntimeLimits
import com.yomu.core.TranslationPromptMode
import java.io.File

/**
 * One curated LLM the user may put in the translation slot (ADR-0009, ADR-0016). The catalog
 * is the single source of truth linking a persisted model id to the GGUF
 * that drives [com.yomu.ml.LlamaTranslationBridge] and to its per-model [idKeyedBatch] capability
 * (#84) — killing the old hardcoded 0.8b path.
 */
data class LlmModelOption(
    val id: String,
    val displayName: String,
    val ggufFileName: String,
    val sizeBytes: Long,
    /**
     * Confirmed licence — the evidence an entry may be redistributed, since Yomu hosts every curated
     * model through the pinned-URL [ModelManager] flow (ADR-0014). Checked 2026-08-19:
     * Qwen2.5 = Apache-2.0; CAT-Translate 0.8b/1.4b = MIT (cyberagent, finetunes of sbintuitions
     * sarashina2.2, both declared MIT). Qwen3.5 = Apache-2.0 on the card and the unsloth GGUF repo
     * (checked 2026-09-16). Ministral 3 3B/8B Instruct 2512 = Apache-2.0 on the card and the
     * first-party GGUF repo (checked 2026-09-18). Also the "governed by its own licence" notice ADR-0009 asks
     * Yomu to surface.
     */
    val licence: String,
    /** Per-model (#84): a model that can emit one id-keyed reply for the whole page carries true and
     *  routes to the grammar-constrained page-level batch call (ADR-0013); CAT-Translate 0.8b cannot
     *  and stays per-line. Read by the slot, and by the settings screen, which hides the
     *  per-line-only capture-context switch when it is true. */
    val idKeyedBatch: Boolean,
    val promptMode: TranslationPromptMode,
    /**
     * f16 KV cache per context token: layers × 2 (K and V) × KV heads × head dim × 2 bytes, from each model's
     * config.json (checked 2026-09-15). Counted against the fit budget so a larger context can gate a model out (#79).
     */
    val kvCacheBytesPerToken: Long,
    /**
     * The sampling this model's maker recommends (ADR-0017): the floor the reader's Advanced
     * settings overlay field by field in [GenerationProfileStore], so a reader who changed nothing
     * gets the maker's values and one who moved a slider keeps it across models. The three entries
     * curated before ADR-0017 carry the shipped values, so nothing changes for a reader on them.
     */
    val generationDefaults: GenerationParams,
    /** Added on desk research and not yet run on the reference phone; the picker tags it (ADR-0017). */
    val experimental: Boolean = false,
    /**
     * The system turn this entry is rendered with (#287). Empty — the default, and what every entry
     * curated before #287 carries — sends no system turn at all, which is what CAT-Translate needs
     * (#68) and what Qwen's template already handles on its own. It exists because Ministral 3's
     * template injects Mistral's Le Chat assistant prompt when no system message is sent, so an
     * entry rendered through it must state its own role or be told it is a chat assistant.
     */
    val systemMessage: String = ""
)

/** The RAM a translation model must fit inside on this device: [percent] of [totalMemBytes], at [contextTokens]. */
data class FitBudget(val totalMemBytes: Long, val percent: Int, val contextTokens: Int)

/** The persisted model id, or null when absent or stored as another type (which would otherwise throw). */
internal fun SharedPreferences.storedLlmModelId(): String? =
    runCatching { getString(Constants.PREF_LLM_MODEL, null) }.getOrNull()

object LlmModelCatalog {

    // Resident cost of everything held alongside the LLM (detection + OCR + app), added to the GGUF
    // file size to predict peak footprint. ponytail: fixed estimate, not GGUF-parsed; ADR-0001's
    // exact param×quant math can replace it if the coarse fit gate proves wrong on a real device.
    const val RESIDENT_OVERHEAD_BYTES = 800L * 1024 * 1024

    // Default share of device totalMem an app can realistically use for weights before the OS OOM-kills
    // it. Calibrated to #84: Hunyuan-7B Q4 (4.6GB) is killed on an 8GB phone, its Q3 (3.8GB) fits.
    // The reader may move it (#79) — raise if a capable device wrongly hides a model, lower if one
    // that OOMs still shows.
    const val DEFAULT_FIT_BUDGET_PERCENT = 60

    /** ADR-0010: phone-confirmed default. Picking nothing keeps this; it is never gated out (part D). */
    val DEFAULT: LlmModelOption = LlmModelOption(
        id = Constants.QWEN25_15B_MODEL_ID,
        displayName = "Qwen2.5 1.5B Instruct",
        ggufFileName = Constants.QWEN25_15B_MODEL,
        sizeBytes = Constants.QWEN25_15B_SIZE,
        licence = "Apache-2.0",
        idKeyedBatch = true,
        promptMode = TranslationPromptMode.TRANSLATION_ONLY,
        kvCacheBytesPerToken = 28L * 2 * 2 * 128 * 2,
        generationDefaults = GenerationParams()
    )

    /**
     * Ministral's replacement for the Le Chat default: the role, and nothing about the reply's shape.
     * What to translate and how to lay the answer out stays in the user turn, which is the one the
     * batch grammar and the per-line prompts are written against (ADR-0013).
     */
    private const val MINISTRAL_SYSTEM_MESSAGE =
        "You are a manga translator. You translate Japanese into natural English."

    /**
     * The curated selectable shortlist. Every entry is redistributable and hosted.
     *
     * Gemma / TranslateGemma were the intended gated-download members, but there is no usable
     * licence-clean gated GGUF for them: the official gated repo ships only a 10.5 GB f32 file, and
     * the small Q4_K_M quants live only on *ungated* public re-hosts, where the gate — hence the
     * whole "user accepts the licence under their own account" premise — cannot apply. So they are
     * dropped rather than offered through a gate that does not exist, and the HF-auth mechanism was
     * deleted (#187; ADR-0014 names the commit to revive it from). The open "Custom — unsupported"
     * slot (ADR-0001) is a separate hatch.
     */
    val ALL: List<LlmModelOption> = listOf(
        DEFAULT,
        LlmModelOption(
            id = Constants.CAT_TRANSLATION_MODEL_ID,
            displayName = "CAT-Translate 0.8B",
            ggufFileName = Constants.TRANSLATION_MODEL_4BIT,
            sizeBytes = Constants.TRANSLATION_MODEL_4BIT_SIZE,
            licence = "MIT",
            idKeyedBatch = false,
            promptMode = TranslationPromptMode.MODEL_CARD,
            kvCacheBytesPerToken = 24L * 2 * 8 * 80 * 2,
            generationDefaults = GenerationParams()
        ),
        LlmModelOption(
            id = Constants.CAT_TRANSLATION_14B_MODEL_ID,
            displayName = "CAT-Translate 1.4B",
            ggufFileName = Constants.CAT_TRANSLATION_14B_MODEL,
            sizeBytes = Constants.CAT_TRANSLATION_14B_SIZE,
            licence = "MIT",
            idKeyedBatch = true,
            promptMode = TranslationPromptMode.MODEL_CARD,
            kvCacheBytesPerToken = 24L * 2 * 8 * 112 * 2,
            generationDefaults = GenerationParams()
        ),
        LlmModelOption(
            id = Constants.QWEN35_08B_MODEL_ID,
            displayName = "Qwen3.5 0.8B",
            ggufFileName = Constants.QWEN35_08B_MODEL,
            sizeBytes = Constants.QWEN35_08B_SIZE,
            licence = "Apache-2.0",
            idKeyedBatch = true,
            promptMode = TranslationPromptMode.TRANSLATION_ONLY,
            // 6 of its 24 layers hold a KV cache; the 18 DeltaNet layers keep a fixed ~19 MiB state instead.
            kvCacheBytesPerToken = 6L * 2 * 2 * 256 * 2,
            generationDefaults = GenerationParams(temperature = 1.0f, topK = 20, topP = 1.0f, penaltyPresent = 2.0f),
            experimental = true
        ),
        LlmModelOption(
            id = Constants.QWEN35_2B_MODEL_ID,
            displayName = "Qwen3.5 2B",
            ggufFileName = Constants.QWEN35_2B_MODEL,
            sizeBytes = Constants.QWEN35_2B_SIZE,
            licence = "Apache-2.0",
            idKeyedBatch = true,
            promptMode = TranslationPromptMode.TRANSLATION_ONLY,
            // Only its 6 full-attention layers hold a KV cache; the 18 DeltaNet layers keep a fixed ~19 MiB state instead.
            kvCacheBytesPerToken = 6L * 2 * 2 * 256 * 2,
            // The card's "Non-thinking mode for text tasks" values, from the research document's
            // sampling table: temperature 1.0, top-p 1.00, top-k 20, presence 2.0, repetition 1.0.
            // The first entry whose sampling differs from the shipped floor, which is what ADR-0017
            // reversed #139 for.
            generationDefaults = GenerationParams(temperature = 1.0f, topK = 20, topP = 1.0f, penaltyPresent = 2.0f),
            experimental = true
        ),
        LlmModelOption(
            id = Constants.QWEN35_4B_MODEL_ID,
            displayName = "Qwen3.5 4B",
            ggufFileName = Constants.QWEN35_4B_MODEL,
            sizeBytes = Constants.QWEN35_4B_SIZE,
            licence = "Apache-2.0",
            idKeyedBatch = true,
            promptMode = TranslationPromptMode.TRANSLATION_ONLY,
            // 8 of its 32 layers hold a KV cache; the DeltaNet layers keep a fixed ~50 MiB state instead.
            kvCacheBytesPerToken = 8L * 2 * 4 * 256 * 2,
            // The card's "Instruct (or non-thinking) mode for general tasks" values, shared with the 9B.
            generationDefaults = GenerationParams(temperature = 0.7f, topK = 20, topP = 0.8f, penaltyPresent = 1.5f),
            experimental = true
        ),
        LlmModelOption(
            id = Constants.QWEN35_9B_MODEL_ID,
            displayName = "Qwen3.5 9B",
            ggufFileName = Constants.QWEN35_9B_MODEL,
            sizeBytes = Constants.QWEN35_9B_SIZE,
            licence = "Apache-2.0",
            idKeyedBatch = true,
            promptMode = TranslationPromptMode.TRANSLATION_ONLY,
            // 8 of its 32 layers hold a KV cache; the DeltaNet layers keep a fixed ~50 MiB state instead.
            // At 6.2 GiB it is the first entry the fit gate keeps off an 8 GB phone (ADR-0017's ~9B ceiling).
            kvCacheBytesPerToken = 8L * 2 * 4 * 256 * 2,
            generationDefaults = GenerationParams(temperature = 0.7f, topK = 20, topP = 0.8f, penaltyPresent = 1.5f),
            experimental = true
        ),
        LlmModelOption(
            id = Constants.QWEN3_4B_2507_MODEL_ID,
            displayName = "Qwen3 4B Instruct 2507",
            ggufFileName = Constants.QWEN3_4B_2507_MODEL,
            sizeBytes = Constants.QWEN3_4B_2507_SIZE,
            licence = "Apache-2.0",
            idKeyedBatch = true,
            promptMode = TranslationPromptMode.TRANSLATION_ONLY,
            // Plain GQA over all 36 layers, so every one holds a KV cache — the heaviest of the Qwen line.
            kvCacheBytesPerToken = 36L * 2 * 8 * 128 * 2,
            // Card and generation_config.json agree. It is non-thinking only, so no presence penalty is asked for.
            generationDefaults = GenerationParams(temperature = 0.7f, topK = 20, topP = 0.8f),
            experimental = true
        ),
        // Ministral 3 Instruct 2512, first-party Q4_K_M (research addendum pins, checked 2026-09-18).
        // Both carry the same template up to the model name inside its default system prompt, so both
        // need MINISTRAL_SYSTEM_MESSAGE: with no system turn the template renders Mistral's Le Chat
        // assistant prompt instead of any translation instruction.
        LlmModelOption(
            id = Constants.MINISTRAL3_3B_MODEL_ID,
            displayName = "Ministral 3 3B Instruct",
            ggufFileName = Constants.MINISTRAL3_3B_MODEL,
            sizeBytes = Constants.MINISTRAL3_3B_SIZE,
            licence = "Apache-2.0",
            idKeyedBatch = true,
            promptMode = TranslationPromptMode.TRANSLATION_ONLY,
            // 26 layers, 8 KV heads, head dim 128: 106,496 B/token, the heaviest of the sub-4B entries.
            kvCacheBytesPerToken = 26L * 2 * 8 * 128 * 2,
            // The card's only "Recommended Settings" line is temperature below 0.1; every other knob
            // stays on the shipped floor.
            generationDefaults = GenerationParams(temperature = 0.05f),
            experimental = true,
            systemMessage = MINISTRAL_SYSTEM_MESSAGE
        ),
        LlmModelOption(
            id = Constants.MINISTRAL3_8B_MODEL_ID,
            displayName = "Ministral 3 8B Instruct",
            ggufFileName = Constants.MINISTRAL3_8B_MODEL,
            sizeBytes = Constants.MINISTRAL3_8B_SIZE,
            licence = "Apache-2.0",
            idKeyedBatch = true,
            promptMode = TranslationPromptMode.TRANSLATION_ONLY,
            // 34 layers, 8 KV heads, head dim 160 (hidden 5120 / 32 heads). The addendum's shortlist
            // row mistypes the head dim as 128; 174,080 is the figure its fit verdicts were computed
            // from, and it is what keeps this entry off an 8 GiB phone.
            kvCacheBytesPerToken = 34L * 2 * 8 * 160 * 2,
            generationDefaults = GenerationParams(temperature = 0.05f),
            experimental = true,
            systemMessage = MINISTRAL_SYSTEM_MESSAGE
        )
    )

    fun fromId(id: String?): LlmModelOption? = id?.let { key -> ALL.firstOrNull { it.id == key } }

    /** The selected option, or the default when nothing (or an unknown id) is persisted. */
    fun selectedOrDefault(id: String?): LlmModelOption = fromId(id) ?: DEFAULT

    fun profileFor(option: LlmModelOption, modelsDir: File, generation: GenerationParams, runtime: RuntimeLimits): ModelProfile =
        ModelProfile(
            modelPath = File(modelsDir, option.ggufFileName).absolutePath,
            idKeyedBatch = option.idKeyedBatch,
            promptMode = option.promptMode,
            // The option's maker sampling with the reader's global Advanced settings over it
            // (ADR-0017, reversing #139's "no per-model values before a measurement"); resolved by
            // the caller, which is the layer that owns the store.
            generation = generation,
            runtime = runtime,
            systemMessage = option.systemMessage
        )

    /**
     * Whether [option] — weights, resident overhead and its KV cache at the budget's context size — fits inside
     * [budget] (part D, #79). The default is never gated out — it must stay usable on the mid-range floor.
     */
    fun canRunOnDevice(option: LlmModelOption, budget: FitBudget): Boolean {
        if (option.id == DEFAULT.id) return true
        val needed = option.sizeBytes + RESIDENT_OVERHEAD_BYTES + option.kvCacheBytesPerToken * budget.contextTokens
        return needed <= budget.totalMemBytes / 100 * budget.percent
    }
}

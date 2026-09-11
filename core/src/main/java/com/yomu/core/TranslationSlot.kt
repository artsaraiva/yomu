package com.yomu.core

sealed class TranslationStatus {
    data object NotReady : TranslationStatus()
    data object Downloading : TranslationStatus()
    data object Ready : TranslationStatus()
    data class Error(val reason: String) : TranslationStatus()
}

enum class TranslationPromptMode { MODEL_CARD, TRANSLATION_ONLY, CAPTURE_CONTEXT }

data class TranslatableBubble(val bubbleId: Int, val sourceText: String)

data class TranslatablePage(val panels: List<List<TranslatableBubble>>)

/**
 * Every knob the sampler and the generation loop read, in one place.
 *
 * Before this existed the values were split three ways: temperature crossed the JNI as an
 * argument, top_k and top_p were literals inside `rebuild_sampler`, and the token cap was a
 * private constant in `LlamaTranslationBridge`. ADR-0008 spent a whole cycle blaming model
 * quality for what turned out to be an undocumented `MAX_TOKENS = 64` in that constant pile, so
 * each default below records where its value came from. #139 declined a further ADR on the
 * grounds that this comment is the deliverable that prevents the repeat.
 *
 * Defaults are byte-identical to the behaviour that shipped before they moved here. Changing one
 * is a measurement, not a refactor: #137 owns the control row every candidate value is scored
 * against.
 *
 * There is deliberately no per-model override. Two models get different parameters only when a
 * measurement forces them apart.
 */
data class GenerationParams(
    /** Was `LlamaTranslationBridge.TEMPERATURE`. Low: translation wants the likely token. */
    val temperature: Float = 0.2f,
    /** Was a literal in `rebuild_sampler`. */
    val topK: Int = 40,
    /** Was a literal in `rebuild_sampler`. */
    val topP: Float = 0.9f,
    /** 1.0 disables the repeat penalty. The value, or the null result, is #137's to record. */
    val penaltyRepeat: Float = 1.0f,
    /**
     * Deliberately shorter than the prompt: a longer window would penalise tokens the model is
     * meant to echo back (the `[id]` tags, names repeated across bubbles).
     */
    val penaltyLastN: Int = 64,
    /** No proposed job. Present so a run can try it without another JNI change. */
    val penaltyFreq: Float = 0.0f,
    /** No proposed job. Present so a run can try it without another JNI change. */
    val penaltyPresent: Float = 0.0f,
    /** Was `LlamaTranslationBridge.MAX_TOKENS`. Not a sampler knob; read by the decode loop. */
    val maxTokens: Int = 256,
    /**
     * `LLAMA_DEFAULT_SEED` (0xFFFFFFFF as a uint32), made explicit rather than implied by the
     * C++ default. Kept out of [samplerArray] because a Float cannot hold 0xFFFFFFFF exactly.
     */
    val seed: Int = -1
) {
    /**
     * The sampler knobs as a flat array, in [SAMPLER_INDEX] order. C++ reads the same order by
     * index; the two lists must be edited together. `maxTokens` and `seed` are not here — they
     * are not sampler knobs and cross the JNI as their own arguments.
     */
    fun samplerArray(): FloatArray = floatArrayOf(
        temperature,
        topK.toFloat(),
        topP,
        penaltyLastN.toFloat(),
        penaltyRepeat,
        penaltyFreq,
        penaltyPresent
    )

    companion object {
        /** Index order of [samplerArray]. Mirrored by `SamplerIndex` in `llama_jni.cpp`. */
        val SAMPLER_INDEX = listOf(
            "temperature",
            "topK",
            "topP",
            "penaltyLastN",
            "penaltyRepeat",
            "penaltyFreq",
            "penaltyPresent"
        )
    }
}

data class ModelProfile(
    val modelPath: String,
    val idKeyedBatch: Boolean,
    val promptMode: TranslationPromptMode,
    val generation: GenerationParams = GenerationParams()
)

data class PageTranslation(
    val byId: Map<Int, String>,
    val rawResponse: String,
    val durationMs: Long
)

interface TranslationSlot {
    val status: TranslationStatus

    suspend fun ensureReady(): Boolean
    suspend fun translatePage(
        page: TranslatablePage,
        sessionContext: List<Pair<String, String>>
    ): PageTranslation

    fun endSession()
    fun close()
}

package com.yomu.core

sealed class TranslationStatus {
    data object NotReady : TranslationStatus()
    data object Downloading : TranslationStatus()
    data object Ready : TranslationStatus()
    data class Error(val reason: String) : TranslationStatus()
}

enum class TranslationPromptMode { MODEL_CARD, TRANSLATION_ONLY }

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
    /**
     * 1.0 disables the repeat penalty. **Measured, not assumed** (#153, against the gate #139
     * pre-registered): `1.1` cleared neither bar and is not shipped.
     *
     * On the 17-page gate corpus, 1.1 against a 1.0 control on the same device: non-translation
     * 0.000 both, Japanese residue 0.007 both (the same bubble, a name the model half-romanises —
     * nothing repetitive in it), coverage 100% with 0 source echoes both, readability 1.034 →
     * 1.027. No gated metric moves. On the #152 repetition probe, harm rises monotonically with the
     * penalty: 8 → 12 → 14 hits of 17 scored bubbles at 1.0 / 1.1 / 1.2, with `ふふっ` and `キヒヒッ`
     * flipping clean-to-harmed at 1.1. The only improvement is latency (probe wall time 11.5 s →
     * 5.7 s, from runaway loops stopping early), which #139 excluded in advance.
     *
     * Full write-up, including why the probe's absolute "zero harm" bar is unreachable even at 1.0:
     * `eval/repeat-penalty-153.md`. Re-open only with a new job for it — the page-level batch path,
     * where #137 measured the loops, would be one.
     */
    val penaltyRepeat: Float = 1.0f,
    /**
     * Deliberately shorter than the prompt: a longer window would penalise tokens the model is
     * meant to echo back (the `[id]` tags, names repeated across bubbles). Inert while
     * [penaltyRepeat] is 1.0; kept because it is the window the next candidate would be measured
     * with (#153).
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
    val seed: Int = -1,
    /**
     * Excludes `[` from the batch grammar's `line` rule, making a fake `[id=2]` tag written inside
     * one line unreachable (#214, the two #198 survivors at 1.1). Treated as structural under
     * ADR-0013 because an id tag is structure, but it does bar a translation like `[laughs]`; none
     * of the 17 ADR-0004 references contain `[`, so the corpus cost is zero. **Measured, then
     * shipped** (#214, SM-S911B, seed pinned, against `false` in the same run): residue 0.027 →
     * 0.014, every tag-shaped hit gone, both survivors the already-adjudicated run-on past the
     * last id; chrF2 27.9 → 28.1, readability 1.306 → 1.261, median page 13.8 s → 11.7 s. The
     * run-on class survives, so the 160-char line bound stays. Not a sampler knob, so not in
     * [samplerArray]; it lives here so the run record's `generation` block carries it and the
     * scorer checks it per arm.
     */
    val lineExcludesIdBracket: Boolean = true
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

/**
 * The reader-facing subset of [GenerationParams] (#192, decided in #144): allowed range, slider
 * step and shipped default for each field Settings exposes. The one place these are stated — the
 * Settings panel renders from it, `GenerationProfileStore` validates against it, and the inference
 * boundary re-checks through [withinBounds]. Every other field stays a constant; repeat-penalty in
 * particular stays out of the reader's hands (#153).
 *
 * Values cross as Float so one persistence path serves all fields; [TOP_K] rounds on [write].
 */
enum class GenerationBound(
    val min: Float,
    val max: Float,
    val step: Float,
    val read: (GenerationParams) -> Float,
    val write: (GenerationParams, Float) -> GenerationParams
) {
    TEMPERATURE(0f, 1f, 0.1f, { it.temperature }, { p, v -> p.copy(temperature = v) }),
    TOP_K(1f, 100f, 1f, { it.topK.toFloat() }, { p, v -> p.copy(topK = Math.round(v)) }),
    TOP_P(0f, 1f, 0.05f, { it.topP }, { p, v -> p.copy(topP = v) });

    val default: Float get() = read(GenerationParams())

    fun accepts(value: Float): Boolean = value.isFinite() && value in min..max
}

/** This profile with every reader-facing field outside its [GenerationBound] reset to the default. */
fun GenerationParams.withinBounds(): GenerationParams = GenerationBound.entries.fold(this) { params, bound ->
    if (bound.accepts(bound.read(params))) params else bound.write(params, bound.default)
}

data class ModelProfile(
    val modelPath: String,
    val idKeyedBatch: Boolean,
    val promptMode: TranslationPromptMode,
    val generation: GenerationParams = GenerationParams()
)

/**
 * How a page-level call ended, typed at the boundary that knows the cause.
 *
 * The eval's run records carry this verbatim (#142/#165). It exists so the harness never has to
 * classify a run by matching log or exception text: [TIMEOUT] and [OVERFLOW] in particular must be
 * reported by the layer that actually hit the deadline or the decode budget, not inferred later.
 *
 * [TIMEOUT] and [OVERFLOW] are typed at `llama_jni.cpp` (#149) and carried up through
 * `GenerationResult`: an empty reply means the prompt was refused or the deadline hit, never a
 * silent blank.
 */
enum class TranslationOutcome { SUCCESS, BLANK, TIMEOUT, OVERFLOW, ERROR, NOT_LOADED }

data class PageTranslation(
    val byId: Map<Int, String>,
    val rawResponse: String,
    val durationMs: Long,
    val outcome: TranslationOutcome = TranslationOutcome.SUCCESS,
    /** Machine-readable cause, never prose: `load_failed`, `model_missing`, an exception class. */
    val errorCode: String? = null,
    /**
     * The batch prompt overflowed and the page was answered per-line instead. Kept apart from
     * [errorCode] because which call shape ran and what went wrong are independent (#202).
     */
    val batchOverflowFallback: Boolean = false
) {
    companion object {
        /** The empty page every slot returns when it could not be made ready, with its reason. */
        fun notLoaded(status: TranslationStatus): PageTranslation = PageTranslation(
            emptyMap(),
            "",
            0L,
            TranslationOutcome.NOT_LOADED,
            (status as? TranslationStatus.Error)?.reason ?: "not_ready"
        )
    }
}

interface TranslationSlot {
    val status: TranslationStatus

    suspend fun ensureReady(): Boolean
    suspend fun translatePage(page: TranslatablePage): PageTranslation

    fun endSession()
    fun close()
}

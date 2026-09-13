package com.yomu.ml

import com.yomu.core.GenerationParams

sealed class GenerationResult {
    abstract val durationMs: Long

    data class Success(
        val text: String,
        override val durationMs: Long
    ) : GenerationResult()

    data class NotLoaded(
        override val durationMs: Long
    ) : GenerationResult()

    data class Blank(
        override val durationMs: Long
    ) : GenerationResult()

    /**
     * The prompt did not fit the decode budget, typed at `llama_jni.cpp` rather than inferred from
     * an empty reply (#149). The page-level batch path falls back to per-line on this and only on
     * this: falling back on any blank reply would retry every empty completion as N calls.
     */
    data class Overflow(
        override val durationMs: Long
    ) : GenerationResult()

    /** The deadline fired inside `llama_decode` before any text came back. */
    data class Timeout(
        override val durationMs: Long
    ) : GenerationResult()

    data class Error(
        val reason: String,
        override val durationMs: Long
    ) : GenerationResult()
}

interface TextGenerationBridge {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000
    }

    val isNativeAvailable: Boolean
    val isModelLoaded: Boolean

    fun loadModel(modelPath: String, nCtx: Int = 2048, nGpuLayers: Int = 0): Boolean
    /** [grammar] is GBNF constraining the sampler; empty means unconstrained. */
    fun generate(
        prompt: String,
        params: GenerationParams = GenerationParams(),
        maxTokens: Int = params.maxTokens,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        grammar: String = ""
    ): GenerationResult
    fun release()
}

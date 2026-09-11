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
    fun generate(
        prompt: String,
        params: GenerationParams = GenerationParams(),
        maxTokens: Int = params.maxTokens,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): GenerationResult
    fun release()
}

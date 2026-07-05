package com.yomu.ml

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
    val isNativeAvailable: Boolean
    val isModelLoaded: Boolean

    fun loadModel(modelPath: String, nCtx: Int = 2048, nGpuLayers: Int = 0): Boolean
    fun generate(prompt: String, maxTokens: Int = 512, temperature: Float = 0.7f): GenerationResult
    fun release()
}

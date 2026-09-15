package com.yomu.ml

import com.yomu.core.GenerationParams

internal class FakeLlamaBridge(
    private val resultForPrompt: (String) -> GenerationResult
) : LlamaBridge(null) {
    val prompts = mutableListOf<String>()
    val grammars = mutableListOf<String>()
    val maxTokens = mutableListOf<Int>()
    val params = mutableListOf<GenerationParams>()
    var releaseCalls = 0
    var clearMemoryCalls = 0
    val abortRequests = mutableListOf<Boolean>()

    override val isNativeAvailable: Boolean get() = true
    override val isModelLoaded: Boolean get() = true
    override fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int): Boolean = true
    override fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int, nThreads: Int): Boolean = true
    override fun generate(
        prompt: String,
        params: GenerationParams,
        maxTokens: Int,
        timeoutMs: Int,
        grammar: String
    ): GenerationResult {
        prompts += prompt
        grammars += grammar
        this.maxTokens += maxTokens
        this.params += params
        return resultForPrompt(prompt)
    }

    override fun setAbortRequested(requested: Boolean) {
        abortRequests += requested
    }

    override fun release() {
        releaseCalls++
    }

    override fun clearMemory() {
        clearMemoryCalls++
    }
}

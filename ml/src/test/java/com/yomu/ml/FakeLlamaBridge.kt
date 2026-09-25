package com.yomu.ml

import com.yomu.core.GenerationParams

internal class FakeLlamaBridge(
    private val resultForPrompt: (String) -> GenerationResult
) : LlamaBridge(null) {
    val prompts = mutableListOf<String>()
    val grammars = mutableListOf<String>()
    val maxTokens = mutableListOf<Int>()
    val params = mutableListOf<GenerationParams>()
    val systemMessages = mutableListOf<String>()
    var releaseCalls = 0
    var clearMemoryCalls = 0
    val abortRequests = mutableListOf<Boolean>()
    /** Context tokens and threads of every native load, in order. */
    val loads = mutableListOf<Pair<Int, Int>>()
    /** Whether each native load was allowed to repack the weights, in order. */
    val repacks = mutableListOf<Boolean>()
    private var loaded = true

    override val isNativeAvailable: Boolean get() = true
    override val isModelLoaded: Boolean get() = loaded
    override fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int): Boolean = true
    override fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int, nThreads: Int, repackWeights: Boolean): Boolean {
        loads += nCtx to nThreads
        repacks += repackWeights
        loaded = true
        return true
    }
    override fun generate(
        prompt: String,
        params: GenerationParams,
        maxTokens: Int,
        timeoutMs: Int,
        grammar: String,
        systemMessage: String
    ): GenerationResult {
        prompts += prompt
        grammars += grammar
        systemMessages += systemMessage
        this.maxTokens += maxTokens
        this.params += params
        return resultForPrompt(prompt)
    }

    override fun setAbortRequested(requested: Boolean) {
        abortRequests += requested
    }

    override fun release() {
        releaseCalls++
        loaded = false
    }

    override fun clearMemory() {
        clearMemoryCalls++
    }
}

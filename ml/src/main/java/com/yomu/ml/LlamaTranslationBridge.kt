package com.yomu.ml

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class LlamaTranslationBridge(
    private val llamaBridge: LlamaBridge,
    private val modelPath: String
) : TranslationBridge {

    companion object {
        private const val TAG = "LlamaTranslationBridge"
        private const val N_CTX = 2048
        private const val N_GPU_LAYERS = 0
        private val N_THREADS = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
        private const val MAX_TOKENS = 64
        // Reserve room for the prompt-final tokens and EOS so prompt + output stays within N_CTX.
        private const val BATCH_TOKEN_RESERVE = 96
        // Floor for the page-level budget: keep it well above the per-line MAX_TOKENS so a dense
        // page never silently falls back to the truncating cap ADR-0002 removed.
        private const val BATCH_MIN_TOKENS = 256
        private const val TEMPERATURE = 0.2f
        private const val TIMEOUT_MS = 15_000
        private const val BATCH_TIMEOUT_MS = 120_000
    }

    private val readinessMutex = Mutex()

    @Volatile
    override var status: TranslationStatus = TranslationStatus.NotReady
        private set

    override suspend fun ensureReady(): Boolean = readinessMutex.withLock {
        if (status is TranslationStatus.Ready) return@withLock true
        if (!llamaBridge.isNativeAvailable) {
            status = TranslationStatus.Error("native_unavailable")
            return@withLock false
        }
        if (llamaBridge.isModelLoaded) {
            status = TranslationStatus.Ready
            return@withLock true
        }
        if (!File(modelPath).exists()) {
            status = TranslationStatus.Error("model_missing")
            return@withLock false
        }
        val loaded = llamaBridge.loadModel(modelPath, N_CTX, N_GPU_LAYERS, N_THREADS)
        status = if (loaded) TranslationStatus.Ready else TranslationStatus.Error("load_failed")
        loaded
    }

    override suspend fun translate(sourceText: String): TranslationOutput? {
        if (sourceText.isBlank()) return null
        if (status !is TranslationStatus.Ready && !ensureReady()) return null
        return generate(sourceText, MAX_TOKENS, TIMEOUT_MS)
    }

    override suspend fun translateBatch(prompt: String): TranslationOutput? {
        if (prompt.isBlank()) return null
        if (status !is TranslationStatus.Ready && !ensureReady()) return null
        // ponytail: chars/2 is a rough token estimate that keeps prompt + output within N_CTX
        // without a tokenizer; its ceiling is a page dense enough to overflow N_CTX, where an exact
        // tokenizer would be needed. #58's corpus (~8-9 short ids/page) stays far under.
        val promptTokenEstimate = prompt.length / 2
        val budget = (N_CTX - promptTokenEstimate - BATCH_TOKEN_RESERVE).coerceIn(BATCH_MIN_TOKENS, N_CTX)
        return generate(prompt, budget, BATCH_TIMEOUT_MS)
    }

    private fun generate(prompt: String, maxTokens: Int, timeoutMs: Int): TranslationOutput? {
        return when (val result = llamaBridge.generate(prompt, maxTokens, TEMPERATURE, timeoutMs)) {
            is GenerationResult.Success -> {
                val text = result.text.trim()
                if (text.isBlank()) {
                    Log.w(TAG, "generate blank promptLength=${prompt.length}")
                    null
                } else {
                    Log.i(
                        TAG,
                        "generate success promptLength=${prompt.length} translatedLength=${text.length} maxTokens=$maxTokens durationMs=${result.durationMs}"
                    )
                    TranslationOutput(
                        translatedText = text,
                        confidence = 0.8f,
                        durationMs = result.durationMs
                    )
                }
            }
            is GenerationResult.Blank,
            is GenerationResult.Error,
            is GenerationResult.NotLoaded -> null
        }
    }

    override fun supportsBatch(): Boolean = true

    override fun clearMemory() {
        llamaBridge.clearMemory()
    }

    override fun close() {
        status = TranslationStatus.NotReady
        Log.i(TAG, "close completed")
    }

    fun release() {
        llamaBridge.release()
        status = TranslationStatus.NotReady
        Log.i(TAG, "release completed")
    }
}

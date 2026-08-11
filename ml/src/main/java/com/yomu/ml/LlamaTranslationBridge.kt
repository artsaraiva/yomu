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
        private const val TEMPERATURE = 0.2f
        private const val TIMEOUT_MS = 15_000
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

        return when (val result = llamaBridge.generate(sourceText, MAX_TOKENS, TEMPERATURE, TIMEOUT_MS)) {
            is GenerationResult.Success -> {
                val text = result.text.trim()
                if (text.isBlank()) {
                    Log.w(TAG, "translate blank sourceLength=${sourceText.length}")
                    null
                } else {
                    Log.i(
                        TAG,
                        "translate success sourceLength=${sourceText.length} translatedLength=${text.length} durationMs=${result.durationMs}"
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

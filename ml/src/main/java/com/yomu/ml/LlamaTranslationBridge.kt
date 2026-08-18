package com.yomu.ml

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class LlamaTranslationBridge(
    private val llamaBridge: LlamaBridge,
    modelPath: String,
    // Per-model, not per-class: the curated 0.8b refuses on any surrounding context (#68/#71) so it
    // stays on the per-line floor (false), but a larger sibling can emit one id-keyed reply for the
    // whole page (#72/#84). The #84 bake-off constructs capable candidates with this set true so
    // TranslationEngine.translate routes them through translateBatch instead of the per-line path.
    idKeyedBatch: Boolean = false
) : TranslationBridge {

    // Runtime-selected (ADR-0009 #90): which curated GGUF occupies the translation slot, switchable
    // via [selectModel]. Was a compile-time constant hardcoded to the 0.8b path.
    @Volatile
    private var modelPath: String = modelPath

    @Volatile
    private var idKeyedBatch: Boolean = idKeyedBatch

    /**
     * Point the bridge at a different curated model (#90 part A). A no-op if unchanged; otherwise it
     * releases the loaded native model and drops to NotReady so the next [ensureReady] loads the new
     * GGUF. Safe to call while an engine other than LLM is active — the reload is lazy.
     */
    fun selectModel(newModelPath: String, newIdKeyedBatch: Boolean) {
        if (newModelPath == modelPath && newIdKeyedBatch == idKeyedBatch) return
        llamaBridge.release()
        modelPath = newModelPath
        idKeyedBatch = newIdKeyedBatch
        status = TranslationStatus.NotReady
        Log.i(TAG, "selectModel switched idKeyedBatch=$newIdKeyedBatch")
    }

    companion object {
        private const val TAG = "LlamaTranslationBridge"
        private const val N_CTX = 2048
        private const val N_GPU_LAYERS = 0
        private val N_THREADS = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
        // One bubble's English, not one short line: the per-line page-context path (#71) sends the
        // whole page as context and asks for a single bubble back, but a dense narration box (up to
        // MAX_SOURCE_CHARS of source) can exceed 64 output tokens. 64 was #58's truncation → residue
        // gate failure (#65 US-12); size the per-bubble budget above it. Generation still stops at
        // EOS, so short balloons are unaffected — this only lifts the ceiling for long ones.
        private const val MAX_TOKENS = 256
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
                    // Raw model text, so a failed run can be diagnosed as prompt-format vs. model
                    // quality without re-instrumenting the device (ADR-0002 eval honesty).
                    Log.i(TAG, "generate raw=${text.replace("\n", "\\n")}")
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

    override fun supportsIdKeyedBatch(): Boolean = idKeyedBatch

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

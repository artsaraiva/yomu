package com.yomu.ml.opusmt

import android.util.Log
import com.yomu.ml.OnnxRuntime
import com.yomu.ml.TranslationBridge
import com.yomu.ml.TranslationOutput
import com.yomu.ml.TranslationStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OpusMtTranslationBridge(
    private val onnxRuntime: OnnxRuntime,
    private val encoderModelPath: String,
    private val decoderModelPath: String,
    private val tokenizerPath: String
) : TranslationBridge {

    companion object {
        private const val TAG = "OpusMtTranslationBridge"
        private const val CONFIDENCE = 0.85f
    }

    private val readinessMutex = Mutex()
    private var translator: OpusMtTranslator? = null

    @Volatile
    override var status: TranslationStatus = TranslationStatus.NotReady
        private set

    override suspend fun ensureReady(): Boolean = readinessMutex.withLock {
        if (status is TranslationStatus.Ready) return@withLock true

        val t = OpusMtTranslator(onnxRuntime, encoderModelPath, decoderModelPath, tokenizerPath)
        val ready = t.load()
        if (ready) {
            translator = t
            status = TranslationStatus.Ready
            Log.i(TAG, "ensureReady success")
        } else {
            t.close()
            status = TranslationStatus.Error("load_failed")
            Log.w(TAG, "ensureReady failed")
        }
        ready
    }

    override suspend fun translate(sourceText: String): TranslationOutput? {
        if (sourceText.isBlank()) return null
        if (status !is TranslationStatus.Ready && !ensureReady()) return null

        val t = translator ?: return null
        val startMs = System.currentTimeMillis()
        return try {
            val (inputIds, mask) = t.encode(sourceText)
            val generated = t.translate(inputIds, mask) ?: return null
            val text = t.decode(generated).trim()
            if (text.isBlank()) {
                Log.w(TAG, "translate blank sourceLength=${sourceText.length}")
                null
            } else {
                val durationMs = System.currentTimeMillis() - startMs
                Log.i(
                    TAG,
                    "translate success sourceLength=${sourceText.length} translatedLength=${text.length} durationMs=$durationMs"
                )
                TranslationOutput(text, CONFIDENCE, durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "translate failed sourceLength=${sourceText.length}", e)
            status = TranslationStatus.Error(e::class.simpleName ?: "error")
            null
        }
    }

    override fun supportsBatch(): Boolean = false

    override fun close() {
        translator?.close()
        translator = null
        status = TranslationStatus.NotReady
        Log.i(TAG, "close completed")
    }
}

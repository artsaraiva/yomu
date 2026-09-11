package com.yomu.ml.opusmt

import android.util.Log
import com.yomu.core.PageTranslation
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.core.TranslationSlot
import com.yomu.core.TranslationStatus
import com.yomu.ml.OnnxRuntime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OpusMtTranslationBridge(
    private val onnxRuntime: OnnxRuntime,
    private val encoderModelPath: String,
    private val decoderModelPath: String,
    private val tokenizerPath: String
) : TranslationSlot {

    companion object {
        private const val TAG = "OpusMtTranslationBridge"
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

    override suspend fun translatePage(
        page: TranslatablePage,
        sessionContext: List<Pair<String, String>>
    ): PageTranslation {
        if (status !is TranslationStatus.Ready && !ensureReady()) {
            return PageTranslation(
                emptyMap(),
                "",
                0L,
                TranslationOutcome.NOT_LOADED,
                (status as? TranslationStatus.Error)?.reason ?: "not_ready"
            )
        }
        val bubbles = page.panels.flatten()
        val outputs = bubbles.mapNotNull { bubble ->
            translate(bubble.sourceText)?.let { output -> bubble.bubbleId to output }
        }
        return PageTranslation(
            byId = outputs.associate { (id, output) -> id to output.text },
            rawResponse = outputs.joinToString("\n") { (id, output) -> "[$id] ${output.text}" },
            durationMs = outputs.sumOf { it.second.durationMs },
            // Per-bubble decode reports no typed cause of its own, so the only honest distinction is
            // answered vs not.
            outcome = if (bubbles.isNotEmpty() && outputs.isEmpty()) {
                TranslationOutcome.BLANK
            } else {
                TranslationOutcome.SUCCESS
            }
        )
    }

    private suspend fun translate(sourceText: String): TranslatedText? {
        if (sourceText.isBlank()) return null
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
                TranslatedText(text, durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "translate failed sourceLength=${sourceText.length}", e)
            status = TranslationStatus.Error(e::class.simpleName ?: "error")
            null
        }
    }

    override fun endSession() = Unit

    override fun close() {
        translator?.close()
        translator = null
        status = TranslationStatus.NotReady
        Log.i(TAG, "close completed")
    }

    private data class TranslatedText(val text: String, val durationMs: Long)
}

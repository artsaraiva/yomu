package com.yomu.app.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.yomu.core.PageTranslation
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationSlot
import com.yomu.core.TranslationStatus
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitTranslationBridge @Inject constructor() : TranslationSlot {

    companion object {
        private const val TAG = "MlKitTranslationBridge"
        private const val DOWNLOAD_TIMEOUT_MS = 120_000L
    }

    private val readinessMutex = Mutex()
    private var translator: Translator? = null

    @Volatile
    override var status: TranslationStatus = TranslationStatus.NotReady
        private set

    override suspend fun ensureReady(): Boolean {
        return readinessMutex.withLock {
            if (status is TranslationStatus.Ready) return@withLock true
            status = TranslationStatus.Downloading
            val activeTranslator = translator ?: createTranslator().also { translator = it }
            val conditions = DownloadConditions.Builder().build()
            Log.i(TAG, "ensureReady start")
            runCatching {
                withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                    activeTranslator.downloadModelIfNeeded(conditions).await()
                    true
                } ?: false
            }.fold(
                onSuccess = { ready ->
                    if (ready) {
                        status = TranslationStatus.Ready
                        Log.i(TAG, "ensureReady success")
                        true
                    } else {
                        status = TranslationStatus.NotReady
                        Log.w(TAG, "ensureReady timeout")
                        false
                    }
                },
                onFailure = { error ->
                    val reason = error::class.simpleName ?: "Error"
                    status = TranslationStatus.Error(reason)
                    Log.w(TAG, "ensureReady failed reason=$reason")
                    false
                }
            )
        }
    }

    override suspend fun translatePage(
        page: TranslatablePage,
        sessionContext: List<Pair<String, String>>
    ): PageTranslation {
        val bubbles = page.panels.flatten()
        if (bubbles.isEmpty()) return PageTranslation(emptyMap(), "", 0L)
        if (status !is TranslationStatus.Ready && !ensureReady()) {
            return PageTranslation(emptyMap(), "", 0L)
        }
        val outputs = bubbles.mapNotNull { bubble ->
            translate(bubble.sourceText)?.let { output -> bubble.bubbleId to output }
        }
        return PageTranslation(
            byId = outputs.associate { (id, output) -> id to output.text },
            rawResponse = outputs.joinToString("\n") { (id, output) -> "[$id] ${output.text}" },
            durationMs = outputs.sumOf { it.second.durationMs }
        )
    }

    private suspend fun translate(sourceText: String): TranslatedText? {
        if (sourceText.isBlank()) return null
        val activeTranslator = translator ?: return null
        val startTime = System.currentTimeMillis()
        return runCatching {
            activeTranslator.translate(sourceText).await()
        }.fold(
            onSuccess = { translated ->
                val cleaned = translated.trim()
                if (cleaned.isBlank()) {
                    Log.w(TAG, "translate blank sourceLength=${sourceText.length}")
                    null
                } else {
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "translate success sourceLength=${sourceText.length} translatedLength=${cleaned.length} durationMs=$duration")
                    TranslatedText(cleaned, duration)
                }
            },
            onFailure = { error ->
                val duration = System.currentTimeMillis() - startTime
                val reason = error::class.simpleName ?: "Error"
                status = TranslationStatus.Error(reason)
                Log.w(TAG, "translate failed sourceLength=${sourceText.length} durationMs=$duration reason=$reason")
                null
            }
        )
    }

    override fun endSession() = Unit

    override fun close() {
        translator?.close()
        translator = null
        status = TranslationStatus.NotReady
        Log.i(TAG, "close completed")
    }

    private fun createTranslator(): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        return Translation.getClient(options)
    }

    private data class TranslatedText(val text: String, val durationMs: Long)
}

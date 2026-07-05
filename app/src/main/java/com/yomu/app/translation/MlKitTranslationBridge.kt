package com.yomu.app.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.yomu.ml.TranslationBridge
import com.yomu.ml.TranslationOutput
import com.yomu.ml.TranslationStatus
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitTranslationBridge @Inject constructor() : TranslationBridge {

    companion object {
        private const val TAG = "MlKitTranslationBridge"
        private const val DOWNLOAD_TIMEOUT_MS = 120_000L
    }

    private val readinessMutex = Mutex()
    private var translator: Translator = createTranslator()

    @Volatile
    override var status: TranslationStatus = TranslationStatus.NotReady
        private set

    override suspend fun ensureReady(): Boolean {
        return readinessMutex.withLock {
            if (status is TranslationStatus.Ready) return@withLock true
            status = TranslationStatus.Downloading
            val conditions = DownloadConditions.Builder().build()
            Log.i(TAG, "ensureReady start")
            runCatching {
                withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                    translator.downloadModelIfNeeded(conditions).await()
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

    override suspend fun translate(sourceText: String): TranslationOutput? {
        if (sourceText.isBlank()) return null
        if (status !is TranslationStatus.Ready && !ensureReady()) {
            return null
        }

        val startTime = System.currentTimeMillis()
        return runCatching {
            translator.translate(sourceText).await()
        }.fold(
            onSuccess = { translated ->
                val cleaned = translated.trim()
                if (cleaned.isBlank()) {
                    Log.w(TAG, "translate blank sourceLength=${sourceText.length}")
                    null
                } else {
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "translate success sourceLength=${sourceText.length} translatedLength=${cleaned.length} durationMs=$duration")
                    TranslationOutput(
                        translatedText = cleaned,
                        confidence = 0.8f,
                        durationMs = duration
                    )
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

    override fun close() {
        translator.close()
        translator = createTranslator()
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
}

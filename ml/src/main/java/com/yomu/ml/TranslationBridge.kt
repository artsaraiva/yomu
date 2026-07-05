package com.yomu.ml

sealed class TranslationStatus {
    data object NotReady : TranslationStatus()
    data object Downloading : TranslationStatus()
    data object Ready : TranslationStatus()
    data class Error(val reason: String) : TranslationStatus()
}

data class TranslationOutput(
    val translatedText: String,
    val confidence: Float,
    val durationMs: Long
)

interface TranslationBridge {
    val status: TranslationStatus

    suspend fun ensureReady(): Boolean
    suspend fun translate(sourceText: String): TranslationOutput?
    fun close()
}

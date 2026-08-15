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

    /**
     * Translate a whole page-level prompt in one call. Unlike [translate] (a single line, small
     * output budget), this requests an output budget sized to the page so a full page is not
     * truncated. Default delegates to [translate] for per-line-only bridges.
     */
    suspend fun translateBatch(prompt: String): TranslationOutput? = translate(prompt)

    fun supportsBatch(): Boolean = false
    fun clearMemory() {}
    fun close()
}

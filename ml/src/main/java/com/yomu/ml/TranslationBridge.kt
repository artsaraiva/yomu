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

    /**
     * True when this LLM should receive the whole page as context per translation (ADR-0002,
     * #71). The engine issues one call per bubble, each carrying the surrounding page, and maps
     * the reply to that bubble's id. Distinct from [supportsIdKeyedBatch]: this model translates
     * one line at a time in its trained single-text form.
     */
    fun supportsBatch(): Boolean = false

    /**
     * True only for a model capable of emitting one id-keyed reply for the whole page in a single
     * call (#72's larger CAT-Translate sibling). The curated 0.8b cannot (#68), so it stays on the
     * per-line context path; this gates the retained single-call batch path for the capable model.
     */
    fun supportsIdKeyedBatch(): Boolean = false
    fun clearMemory() {}
    fun close()
}

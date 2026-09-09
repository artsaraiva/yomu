package com.yomu.core

sealed class TranslationStatus {
    data object NotReady : TranslationStatus()
    data object Downloading : TranslationStatus()
    data object Ready : TranslationStatus()
    data class Error(val reason: String) : TranslationStatus()
}

enum class TranslationPromptMode { MODEL_CARD, TRANSLATION_ONLY, CAPTURE_CONTEXT }

data class TranslatableBubble(val bubbleId: Int, val sourceText: String)

data class TranslatablePage(val panels: List<List<TranslatableBubble>>)

data class ModelProfile(
    val modelPath: String,
    val idKeyedBatch: Boolean,
    val promptMode: TranslationPromptMode
)

data class PageTranslation(
    val byId: Map<Int, String>,
    val rawResponse: String,
    val durationMs: Long
)

interface TranslationSlot {
    val status: TranslationStatus

    suspend fun ensureReady(): Boolean
    suspend fun translatePage(
        page: TranslatablePage,
        sessionContext: List<Pair<String, String>>
    ): PageTranslation

    fun endSession()
    fun close()
}

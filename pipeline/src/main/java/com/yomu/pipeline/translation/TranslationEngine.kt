package com.yomu.pipeline.translation

import com.yomu.ml.TranslationBridge
import com.yomu.ml.TranslationOutput
import com.yomu.ml.TranslationStatus
import com.yomu.pipeline.context.ConversationBlock

data class TranslatedBubble(
    val bubbleId: Int,
    val originalText: String,
    val translatedText: String,
    val confidence: Float
)

data class TranslationResult(
    val translations: List<TranslatedBubble>,
    val rawPrompt: String,
    val rawResponse: String,
    val translationTimeMs: Long
)

class TranslationEngine(private val translationBridge: TranslationBridge) {

    companion object {
        private const val MAX_SOURCE_CHARS = 300
        private const val CACHE_SIZE = 128
    }

    private val cache = object : LinkedHashMap<String, TranslationOutput>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TranslationOutput>?): Boolean {
            return size > CACHE_SIZE
        }
    }

    suspend fun ensureReady(): Boolean {
        return translationBridge.ensureReady()
    }

    fun isReady(): Boolean = translationBridge.status is TranslationStatus.Ready

    @Suppress("UNUSED_PARAMETER")
    suspend fun translate(
        blocks: List<ConversationBlock>,
        sessionContext: List<Pair<String, String>> = emptyList()
    ): TranslationResult {
        val startTime = System.currentTimeMillis()

        val translated = if (translationBridge.supportsBatch()) {
            translateBatch(blocks)
        } else {
            translateBubbles(blocks)
        }
        val translations = translated.ifEmpty { fallbackTranslations(blocks) }

        return TranslationResult(
            translations = translations,
            rawPrompt = "",
            rawResponse = translations.joinToString("\n") { translation ->
                "[${translation.bubbleId}] ${translation.translatedText}"
            },
            translationTimeMs = System.currentTimeMillis() - startTime
        )
    }

    fun fallback(blocks: List<ConversationBlock>): TranslationResult {
        val translations = fallbackTranslations(blocks)
        return TranslationResult(
            translations = translations,
            rawPrompt = "",
            rawResponse = "",
            translationTimeMs = 0L
        )
    }

    private suspend fun translateBubbles(blocks: List<ConversationBlock>): List<TranslatedBubble> {
        val translations = mutableListOf<TranslatedBubble>()
        for (block in blocks) {
            for (bubbleId in block.readingOrder) {
                val original = block.textByBubbleId[bubbleId]?.text?.take(MAX_SOURCE_CHARS) ?: continue
                val cached = cache[original]
                val output = cached ?: translationBridge.translate(original)?.also { translated ->
                    cache[original] = translated
                }
                val translatedText = output?.translatedText.orEmpty()
                val hasTranslation = translatedText.isNotBlank()
                translations.add(
                    TranslatedBubble(
                        bubbleId = bubbleId,
                        originalText = original,
                        translatedText = translatedText.ifBlank { original },
                        confidence = if (hasTranslation) (output?.confidence ?: 0.1f) else 0.1f
                    )
                )
            }
        }
        return translations
    }

    private suspend fun translateBatch(blocks: List<ConversationBlock>): List<TranslatedBubble> {
        val items = mutableListOf<Pair<Int, String>>()
        for (block in blocks) {
            for (bubbleId in block.readingOrder) {
                val original = block.textByBubbleId[bubbleId]?.text?.take(MAX_SOURCE_CHARS) ?: continue
                items.add(bubbleId to original)
            }
        }
        if (items.isEmpty()) return emptyList()

        val prompt = buildString {
            appendLine("Translate these Japanese phrases to English, one per line, numbered:")
            items.forEachIndexed { index, (_, original) ->
                appendLine("${index + 1}. $original")
            }
        }

        val output = translationBridge.translate(prompt) ?: return fallbackTranslations(blocks)
        val parsed = parseNumberedTranslations(output.translatedText)

        return items.mapIndexed { index, (bubbleId, original) ->
            val translated = parsed[index + 1]?.takeIf { it.isNotBlank() } ?: original
            val hasTranslation = translated != original
            if (hasTranslation) {
                cache[original] = TranslationOutput(translated, output.confidence, output.durationMs)
            }
            TranslatedBubble(
                bubbleId = bubbleId,
                originalText = original,
                translatedText = translated,
                confidence = if (hasTranslation) output.confidence else 0.1f
            )
        }
    }

    private fun parseNumberedTranslations(response: String): Map<Int, String> {
        val regex = "^\\s*(\\d+)\\.\\s*(.*)$".toRegex()
        return response.lineSequence()
            .mapNotNull { line ->
                val match = regex.matchEntire(line) ?: return@mapNotNull null
                val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                number to match.groupValues[2].trim()
            }
            .toMap()
    }

    private fun fallbackTranslations(blocks: List<ConversationBlock>): List<TranslatedBubble> {
        return blocks.flatMap { block ->
            block.bubbles.mapNotNull { bubble ->
                val original = block.textByBubbleId[bubble.id]?.text?.take(MAX_SOURCE_CHARS) ?: return@mapNotNull null
                TranslatedBubble(
                    bubbleId = bubble.id,
                    originalText = original,
                    translatedText = original,
                    confidence = 0.1f
                )
            }
        }
    }

    fun release() {
        cache.clear()
        translationBridge.clearMemory()
    }

    fun close() {
        cache.clear()
        translationBridge.close()
    }
}

package com.yomu.pipeline.translation

import com.yomu.ml.TranslationBridge
import com.yomu.ml.TranslationOutput
import com.yomu.ml.TranslationStatus
import com.yomu.pipeline.context.ConversationBlock
import java.security.MessageDigest
import java.text.Normalizer

interface TranslationCacheRepository {
    suspend fun get(engineId: String, modelId: String?, sourceText: String): TranslationOutput?
    suspend fun put(engineId: String, modelId: String?, sourceText: String, output: TranslationOutput)
}

fun normalizeForCache(text: String): String {
    return Normalizer.normalize(text.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFKC)
}

fun buildCacheKey(engineId: String, modelId: String?, sourceText: String): String {
    val normalized = normalizeForCache(sourceText)
    val input = "$engineId:${modelId ?: ""}:$normalized"
    return hashSha256(input)
}

private fun hashSha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

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

class TranslationEngine(
    private val translationBridge: TranslationBridge,
    private val engineId: String = "default",
    private val modelId: String? = null,
    private val cacheRepository: TranslationCacheRepository? = null
) {

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

    suspend fun translate(
        blocks: List<ConversationBlock>,
        sessionContext: List<Pair<String, String>> = emptyList()
    ): TranslationResult {
        val startTime = System.currentTimeMillis()

        val translated = if (translationBridge.supportsBatch()) {
            translateBatch(blocks, sessionContext)
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
                val cached = cache[original] ?: cacheRepository?.get(engineId, modelId, original)?.also {
                    cache[original] = it
                }
                val output = cached ?: translationBridge.translate(original)?.also { translated ->
                    cache[original] = translated
                    cacheRepository?.put(engineId, modelId, original, translated)
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

    /**
     * Page-level LLM path (ADR-0002). Every non-empty bubble goes out in one call, each addressed
     * by its real detector id (`[<id>] text`), panels rendered as markers, the previous page's
     * source/translation pairs prepended as session context. The response is parsed by id: a bubble
     * whose id does not come back falls back to its own source text and only that bubble, so a
     * dropped/merged/prefaced line never shifts later bubbles into the wrong balloon. The line-keyed
     * cache is bypassed here so the same line is free to translate differently in another scene.
     */
    private suspend fun translateBatch(
        blocks: List<ConversationBlock>,
        sessionContext: List<Pair<String, String>>
    ): List<TranslatedBubble> {
        val panels = blocks.map { block ->
            block.readingOrder.mapNotNull { bubbleId ->
                val original = block.textByBubbleId[bubbleId]?.text?.take(MAX_SOURCE_CHARS)
                if (original.isNullOrBlank()) null else bubbleId to original
            }
        }.filter { it.isNotEmpty() }
        val items = panels.flatten()
        if (items.isEmpty()) return emptyList()

        val prompt = buildBatchPrompt(panels, sessionContext)
        val output = translationBridge.translateBatch(prompt) ?: return fallbackTranslations(blocks)
        val parsed = parseIdKeyedTranslations(output.translatedText)

        return items.map { (bubbleId, original) ->
            val translated = parsed[bubbleId]?.takeIf { it.isNotBlank() }
            TranslatedBubble(
                bubbleId = bubbleId,
                originalText = original,
                translatedText = translated ?: original,
                confidence = if (translated != null) output.confidence else 0.1f
            )
        }
    }

    private fun buildBatchPrompt(
        panels: List<List<Pair<Int, String>>>,
        sessionContext: List<Pair<String, String>>
    ): String = buildString {
        if (sessionContext.isNotEmpty()) {
            appendLine("Previous page (for context):")
            sessionContext.forEach { (source, target) -> appendLine("$source => $target") }
            appendLine()
        }
        appendLine(
            "Translate each Japanese line to English. Keep the [id] tag before each line. " +
                "Panels are separated by ---."
        )
        panels.forEachIndexed { index, panel ->
            if (index > 0) appendLine("---")
            panel.forEach { (bubbleId, original) -> appendLine("[$bubbleId] $original") }
        }
    }

    private fun parseIdKeyedTranslations(response: String): Map<Int, String> {
        val regex = "^\\s*\\[(\\d+)]\\s*(.*)$".toRegex()
        return response.lineSequence()
            .mapNotNull { line ->
                val match = regex.matchEntire(line) ?: return@mapNotNull null
                val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                id to match.groupValues[2].trim()
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

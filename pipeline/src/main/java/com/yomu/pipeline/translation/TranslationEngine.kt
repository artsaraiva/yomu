package com.yomu.pipeline.translation

import com.yomu.core.PageTranslation
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationSlot
import com.yomu.pipeline.context.ConversationBlock

private val NON_TRANSLATION_PATTERNS = listOf(
    Regex("""(?i)^\s*(?:sure[!,.]?\s*)?(?:here(?:['’]s| is| are)\s+(?:the |an? )?(?:english )?translations?\b|(?:english )?translation\s*:)"""),
    Regex("""(?i)translate the following"""),
    Regex("""(?i)\bas an ai\b"""),
    Regex("""(?i)\bi\s*['’]?m unable to\b"""),
    Regex("""(?i)\bi\s+(?:can['’]?t|cannot|can not|will not|won['’]?t)\s+(?:help|assist|translate|provide|comply|fulfill|do that|with that)"""),
    Regex("""(?i)\b(?:please provide|provide me with)\b.{0,80}\b(?:japanese|manga|target|complete)\s+(?:manga\s+)?text\b"""),
    Regex("""(?i)^\s*the (?:(?:english )?translation of (?:the )?(?:given )?japanese text\b|japanese text\b.{0,150}\btranslates? to\b)""")
)

private val TOKEN_SPLIT = Regex("""\s+""")
private const val MIN_LOOP_TOKENS = 8
private const val LOOP_UNIQUE_DIVISOR = 4

internal fun looksLikeNonTranslation(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    if (NON_TRANSLATION_PATTERNS.any { it.containsMatchIn(trimmed) }) return true
    val tokens = trimmed.split(TOKEN_SPLIT).filter { it.isNotBlank() }
    if (tokens.size < MIN_LOOP_TOKENS) return false
    return tokens.map { it.lowercase() }.toSet().size * LOOP_UNIQUE_DIVISOR <= tokens.size
}

// A target with no letter or digit — a lone "?", "...", "!?" — carries nothing to translate, and
// asking a model to translate it invites a request for the missing text instead (#120).
private fun TranslatableBubble.carriesText(): Boolean = sourceText.any { it.isLetterOrDigit() }

private fun String?.usableTranslation(): String? =
    this?.takeIf { it.isNotBlank() && !looksLikeNonTranslation(it) }

data class TranslatedBubble(
    val bubbleId: Int,
    val originalText: String,
    val translatedText: String,
    val confidence: Float
)

data class TranslationResult(
    val translations: List<TranslatedBubble>,
    val rawResponse: String,
    val translationTimeMs: Long
)

class TranslationEngine(
    private val slotProvider: () -> TranslationSlot,
    private val closeSlots: () -> Unit
) {

    constructor(slotProvider: () -> TranslationSlot) : this(
        slotProvider,
        { slotProvider().close() }
    )

    companion object {
        private const val MAX_SOURCE_CHARS = 300
        private const val TRANSLATED_CONFIDENCE = 0.8f
        private const val FALLBACK_CONFIDENCE = 0.1f
    }

    suspend fun translate(
        blocks: List<ConversationBlock>,
        sessionContext: List<Pair<String, String>> = emptyList()
    ): TranslationResult {
        val page = project(blocks)
        val bubbles = page.panels.flatten()
        if (bubbles.isEmpty()) return TranslationResult(emptyList(), "", 0L)

        val translatable = TranslatablePage(
            page.panels.mapNotNull { panel -> panel.filter { it.carriesText() }.ifEmpty { null } }
        )
        val output = if (translatable.panels.isEmpty()) {
            PageTranslation(emptyMap(), "", 0L)
        } else {
            slotProvider().translatePage(translatable, sessionContext)
        }
        val translations = bubbles.map { bubble ->
            val translated = if (bubble.carriesText()) {
                output.byId[bubble.bubbleId].usableTranslation()
            } else {
                bubble.sourceText
            }
            TranslatedBubble(
                bubbleId = bubble.bubbleId,
                originalText = bubble.sourceText,
                translatedText = translated ?: bubble.sourceText,
                confidence = if (translated == null) FALLBACK_CONFIDENCE else TRANSLATED_CONFIDENCE
            )
        }
        return TranslationResult(translations, output.rawResponse, output.durationMs)
    }

    private fun project(blocks: List<ConversationBlock>): TranslatablePage = TranslatablePage(
        blocks.map { block ->
            block.readingOrder.mapNotNull { bubbleId ->
                block.textByBubbleId[bubbleId]?.text
                    ?.take(MAX_SOURCE_CHARS)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { TranslatableBubble(bubbleId, it) }
            }
        }.filter { it.isNotEmpty() }
    )

    fun endSession() {
        slotProvider().endSession()
    }

    fun close() {
        closeSlots()
    }
}

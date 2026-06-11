package com.yomu.pipeline.translation

import com.yomu.ml.LlamaBridge
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

class TranslationEngine(private val llamaBridge: LlamaBridge) {

    companion object {
        private const val MAX_TOKENS = 512
        private const val TEMPERATURE = 0.3f
        private const val SYSTEM_PROMPT = "You are a manga translator. Translate the following Japanese manga dialogue into natural English. Maintain character voices, emotional tone, and conversational flow. Output only the translations, one per line, numbered."
    }

    private var isLoaded = false

    fun loadModel(modelName: String): Boolean {
        if (!llamaBridge.isNativeAvailable) return false
        isLoaded = llamaBridge.loadModel(modelName)
        return isLoaded
    }

    fun isModelLoaded(): Boolean = isLoaded

    fun translate(blocks: List<ConversationBlock>): TranslationResult {
        val startTime = System.currentTimeMillis()

        val prompt = buildPrompt(blocks)
        val response = llamaBridge.generate(prompt, MAX_TOKENS, TEMPERATURE)
        val translations = parseResponse(response, blocks)

        return TranslationResult(
            translations = translations,
            rawPrompt = prompt,
            rawResponse = response,
            translationTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun buildPrompt(blocks: List<ConversationBlock>): String {
        val sb = StringBuilder()
        sb.appendLine(SYSTEM_PROMPT)
        sb.appendLine()
        sb.appendLine("Translate this manga page:")
        sb.appendLine()

        for ((blockIndex, block) in blocks.withIndex()) {
            sb.appendLine("--- Conversation Block ${blockIndex + 1} ---")
            for ((textIndex, bubbleId) in block.readingOrder.withIndex()) {
                val text = if (textIndex < block.texts.size) block.texts[textIndex].text else ""
                if (text.isNotEmpty()) {
                    sb.appendLine("[${bubbleId}] $text")
                }
            }
            sb.appendLine()
        }

        sb.appendLine("Translations:")
        return sb.toString()
    }

    private fun parseResponse(
        response: String,
        blocks: List<ConversationBlock>
    ): List<TranslatedBubble> {
        val translations = mutableListOf<TranslatedBubble>()

        val lineRegex = Regex("""\[(\d+)\]\s*(.+)""")

        for (line in response.lines()) {
            val match = lineRegex.find(line.trim())
            if (match != null) {
                val bubbleId = match.groupValues[1].toIntOrNull() ?: continue
                val translatedText = match.groupValues[2].trim()

                val originalText = findOriginalText(bubbleId, blocks)

                translations.add(
                    TranslatedBubble(
                        bubbleId = bubbleId,
                        originalText = originalText,
                        translatedText = translatedText,
                        confidence = 0.8f
                    )
                )
            }
        }

        return translations
    }

    private fun findOriginalText(bubbleId: Int, blocks: List<ConversationBlock>): String {
        for (block in blocks) {
            val bubble = block.bubbles.find { it.id == bubbleId } ?: continue
            val textIndex = block.bubbles.indexOf(bubble)
            if (textIndex < block.texts.size) {
                return block.texts[textIndex].text
            }
        }
        return ""
    }

    fun release() {
        llamaBridge.release()
        isLoaded = false
    }
}

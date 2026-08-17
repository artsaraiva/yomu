package com.yomu.pipeline.translation

import android.graphics.RectF
import com.yomu.ml.TranslationBridge
import com.yomu.ml.TranslationOutput
import com.yomu.ml.TranslationStatus
import com.yomu.pipeline.bubble.Bubble
import com.yomu.pipeline.context.ConversationBlock
import com.yomu.pipeline.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class TranslationEngineTest {

    @Test
    fun translate_englishSuccessReplacesOcr() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            outputForText = mapOf(
                "こんにちは" to TranslationOutput(
                    translatedText = "Hello there",
                    confidence = 0.8f,
                    durationMs = 55L
                )
            )
        )
        val engine = TranslationEngine(
            bridge
        )

        val result = engine.translate(listOf(singleBubbleBlock("こんにちは")))

        assertEquals(1, result.translations.size)
        assertEquals("Hello there", result.translations.first().translatedText)
        assertEquals(0.8f, result.translations.first().confidence)
    }

    @Test
    fun translate_nullOutputFallsBack() = runTest {
        val engine = TranslationEngine(
            FakeTranslationBridge(status = TranslationStatus.Ready)
        )

        val result = engine.translate(listOf(singleBubbleBlock("おはよう")))

        assertEquals("おはよう", result.translations.first().translatedText)
        assertEquals(0.1f, result.translations.first().confidence)
    }

    @Test
    fun translate_notReadyFallsBack() = runTest {
        val bridge = FakeTranslationBridge(status = TranslationStatus.NotReady)
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(singleBubbleBlock("ありがとう")))

        assertEquals("ありがとう", result.translations.first().translatedText)
        assertEquals(0.1f, result.translations.first().confidence)
    }

    @Test
    fun translate_cacheRepeatedSourceCallsBridgeOnce() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            outputForText = mapOf(
                "同じ" to TranslationOutput(
                    translatedText = "Same",
                    confidence = 0.8f,
                    durationMs = 10L
                )
            )
        )
        val engine = TranslationEngine(bridge)

        val block = conversationBlock(
            1 to "同じ",
            2 to "同じ"
        )
        val result = engine.translate(listOf(block))

        assertEquals(2, result.translations.size)
        assertEquals("Same", result.translations[0].translatedText)
        assertEquals("Same", result.translations[1].translatedText)
        assertEquals(1, bridge.translateCalls["同じ"])
    }

    @Test
    fun translate_emptyOutputFallsBack() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            outputForText = mapOf(
                "空" to TranslationOutput(
                    translatedText = "   ",
                    confidence = 0.8f,
                    durationMs = 5L
                )
            )
        )
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(singleBubbleBlock("空")))

        assertEquals("空", result.translations.first().translatedText)
        assertEquals(0.1f, result.translations.first().confidence)
    }

    @Test
    fun translate_llmPerLineTranslatesEachBubbleInItsOwnCall() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            outputForText = mapOf(
                "こんにちは" to TranslationOutput("Hello", 0.8f, 10L),
                "さようなら" to TranslationOutput("Goodbye", 0.8f, 10L)
            ),
            supportsBatch = true
        )
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(conversationBlock(1 to "こんにちは", 2 to "さようなら")))

        val byId = result.translations.associateBy { it.bubbleId }
        assertEquals("Hello", byId[1]?.translatedText)
        assertEquals("Goodbye", byId[2]?.translatedText)
        // One call per bubble, and never the single-shot id-keyed batch call.
        assertEquals(2, bridge.translateCallCount)
        assertEquals(0, bridge.batchCalls)
    }

    @Test
    fun translate_llmPerLineMissingBubbleFallsBackToSourceForThatIdOnly() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            outputForText = mapOf(
                "こんにちは" to TranslationOutput("Hello", 0.8f, 10L)
            ),
            supportsBatch = true
        )
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(conversationBlock(1 to "こんにちは", 2 to "さようなら")))

        val byId = result.translations.associateBy { it.bubbleId }
        assertEquals("Hello", byId[1]?.translatedText)
        assertEquals("さようなら", byId[2]?.translatedText)
        assertEquals(0.8f, byId[1]?.confidence)
        assertEquals(0.1f, byId[2]?.confidence)
    }

    @Test
    fun translate_llmPerLineUsesBareModelCardFormWithoutContext() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            outputForText = mapOf(
                "こんにちは" to TranslationOutput("Hello", 0.8f, 10L),
                "さようなら" to TranslationOutput("Goodbye", 0.8f, 10L)
            ),
            supportsBatch = true
        )
        val engine = TranslationEngine(bridge)

        engine.translate(listOf(conversationBlock(1 to "こんにちは", 2 to "さようなら")))

        // The 0.8b refuses with any surrounding context (#71 amendment): each prompt carries only
        // its own target line, never the other bubble's source.
        val helloPrompt = bridge.prompts.single { it.contains("こんにちは") }
        assertTrue(helloPrompt.endsWith("こんにちは"))
        assertFalse(helloPrompt.contains("さようなら"))
    }

    @Test
    fun translate_idKeyedBatchTranslatesAllBubblesWithOneCall() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            batchResponse = "[1] Hello\n[2] Goodbye",
            supportsBatch = true,
            supportsIdKeyedBatch = true
        )
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(conversationBlock(1 to "こんにちは", 2 to "さようなら")))

        assertEquals(2, result.translations.size)
        assertEquals("Hello", result.translations[0].translatedText)
        assertEquals("Goodbye", result.translations[1].translatedText)
        assertEquals(1, bridge.batchCalls)
    }

    @Test
    fun translate_idKeyedBatchMissingIdFallsBackToSourceForThatIdOnly() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            batchResponse = "[1] Hello",
            supportsBatch = true,
            supportsIdKeyedBatch = true
        )
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(conversationBlock(1 to "こんにちは", 2 to "さようなら")))

        assertEquals(2, result.translations.size)
        assertEquals("Hello", result.translations[0].translatedText)
        assertEquals("さようなら", result.translations[1].translatedText)
        assertEquals(0.8f, result.translations[0].confidence)
        assertEquals(0.1f, result.translations[1].confidence)
    }

    @Test
    fun translate_idKeyedBatchIdReturnedOutOfOrderLandsInRightBubble() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            batchResponse = "[2] Goodbye\n[1] Hello",
            supportsBatch = true,
            supportsIdKeyedBatch = true
        )
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(conversationBlock(1 to "こんにちは", 2 to "さようなら")))

        val byId = result.translations.associateBy { it.bubbleId }
        assertEquals("Hello", byId[1]?.translatedText)
        assertEquals("Goodbye", byId[2]?.translatedText)
    }

    @Test
    fun translate_idKeyedBatchMergedOrPrefacedLineDoesNotShiftLaterBubbles() = runTest {
        // A prefaced line with no [id] and a merged extra line must be ignored, not consumed
        // positionally — every id must still land in its own bubble.
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            batchResponse = "Sure! Here are the translations:\n[1] Hello\nand also\n[2] Goodbye",
            supportsBatch = true,
            supportsIdKeyedBatch = true
        )
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(conversationBlock(1 to "こんにちは", 2 to "さようなら")))

        val byId = result.translations.associateBy { it.bubbleId }
        assertEquals("Hello", byId[1]?.translatedText)
        assertEquals("Goodbye", byId[2]?.translatedText)
    }

    // Session context is prepended only on the id-keyed batch path (a larger sibling model, #72).
    // The shipping 0.8b takes translatePerLine, which drops context by design (#71 amendment).
    @Test
    fun translate_idKeyedBatchPrependsSessionContextToPrompt() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            batchResponse = "[1] Hello",
            supportsBatch = true,
            supportsIdKeyedBatch = true
        )
        val engine = TranslationEngine(bridge)

        engine.translate(
            listOf(conversationBlock(1 to "こんにちは")),
            sessionContext = listOf("前の台詞" to "The previous line")
        )

        val prompt = bridge.batchPrompts.single()
        assertTrue(prompt.contains("Previous page"))
        assertTrue(prompt.contains("前の台詞 => The previous line"))
    }

    @Test
    fun translate_idKeyedBatchOmitsContextHeaderWhenSessionContextEmpty() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            batchResponse = "[1] Hello",
            supportsBatch = true,
            supportsIdKeyedBatch = true
        )
        val engine = TranslationEngine(bridge)

        engine.translate(listOf(conversationBlock(1 to "こんにちは")))

        assertFalse(bridge.batchPrompts.single().contains("Previous page"))
    }

    @Test
    fun release_clearsBridgeMemory() {
        val bridge = FakeTranslationBridge(status = TranslationStatus.Ready)
        val engine = TranslationEngine(bridge)

        engine.release()

        assertEquals(1, bridge.clearMemoryCalls)
    }

    @Test
    fun looksLikeNonTranslation_flagsRefusalsEchoesAndLoops() {
        assertTrue(looksLikeNonTranslation("I'm sorry, but I can't help with that."))
        assertTrue(looksLikeNonTranslation("I'm unable to translate this."))
        assertTrue(looksLikeNonTranslation("Translate the following Japanese text into English. foo"))
        assertTrue(looksLikeNonTranslation("As an AI language model, I cannot do this."))
        assertTrue(looksLikeNonTranslation("I'm sorry I'm sorry I'm sorry I'm sorry"))
    }

    @Test
    fun looksLikeNonTranslation_keepsLegitDialogue() {
        assertFalse(looksLikeNonTranslation("I'm sorry!"))
        assertFalse(looksLikeNonTranslation("I'm sorry, I can't come with you today."))
        assertFalse(looksLikeNonTranslation("Sorry for being late, let's go back there."))
        assertFalse(looksLikeNonTranslation("This is a picture of a native of the moon."))
        assertFalse(looksLikeNonTranslation("I love you I love you I love you"))
        assertFalse(looksLikeNonTranslation("No no no no no no"))
    }

    @Test
    fun translate_perLineDropsNonTranslationOutputToSource() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            outputForText = mapOf(
                "こんにちは" to TranslationOutput("I'm sorry, but I can't help with that.", 0.8f, 10L)
            ),
            supportsBatch = true
        )
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(singleBubbleBlock("こんにちは")))

        assertEquals("こんにちは", result.translations.first().translatedText)
        assertEquals(0.1f, result.translations.first().confidence)
    }

    @Test
    fun translate_batchDisabledUsesPerBubblePath() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            outputForText = mapOf(
                "こんにちは" to TranslationOutput(
                    translatedText = "Hello",
                    confidence = 0.8f,
                    durationMs = 10L
                )
            ),
            supportsBatch = false
        )
        val engine = TranslationEngine(bridge)

        val result = engine.translate(listOf(singleBubbleBlock("こんにちは")))

        assertEquals("Hello", result.translations.first().translatedText)
        assertEquals(1, bridge.translateCalls["こんにちは"])
    }

    private fun singleBubbleBlock(text: String): ConversationBlock {
        return conversationBlock(1 to text)
    }

    private fun conversationBlock(vararg bubbleTexts: Pair<Int, String>): ConversationBlock {
        val bubbles = bubbleTexts.map { (id, _) ->
            Bubble(
                id = id,
                boundingBox = RectF(0f, 0f, 100f, 100f),
                confidence = 0.9f
            )
        }
        val textByBubbleId = bubbleTexts.associate { (id, text) ->
            id to OcrResult(
                text = text,
                confidence = 1f,
                boundingBox = floatArrayOf(0f, 0f, 1f, 1f)
            )
        }
        val texts = bubbleTexts.map { (_, text) ->
            OcrResult(
                text = text,
                confidence = 1f,
                boundingBox = floatArrayOf(0f, 0f, 1f, 1f)
            )
        }

        return ConversationBlock(
            blockId = 1,
            bubbles = bubbles,
            texts = texts,
            readingOrder = bubbleTexts.map { it.first },
            textByBubbleId = textByBubbleId
        )
    }

    private class FakeTranslationBridge(
        status: TranslationStatus = TranslationStatus.Ready,
        private val outputForText: Map<String, TranslationOutput> = emptyMap(),
        private val batchResponse: String? = null,
        private val supportsBatch: Boolean = false,
        private val supportsIdKeyedBatch: Boolean = false
    ) : TranslationBridge {
        override var status: TranslationStatus = status
        val translateCalls: MutableMap<String, Int> = mutableMapOf()
        val prompts: MutableList<String> = mutableListOf()
        val batchPrompts: MutableList<String> = mutableListOf()
        var translateCallCount: Int = 0
        var batchCalls: Int = 0
        var clearMemoryCalls: Int = 0

        override suspend fun ensureReady(): Boolean {
            return this.status is TranslationStatus.Ready
        }

        // The page-context path passes a whole prompt whose tail is the target line, so match an
        // output whose key the prompt ends with; the floor path passes the bare source (exact key).
        override suspend fun translate(sourceText: String): TranslationOutput? {
            translateCalls[sourceText] = (translateCalls[sourceText] ?: 0) + 1
            translateCallCount++
            prompts.add(sourceText)
            return outputForText[sourceText]
                ?: outputForText.entries.firstOrNull { sourceText.endsWith(it.key) }?.value
        }

        override suspend fun translateBatch(prompt: String): TranslationOutput? {
            batchCalls++
            batchPrompts.add(prompt)
            return batchResponse?.let { TranslationOutput(it, 0.8f, 100L) }
        }

        override fun supportsBatch(): Boolean = supportsBatch

        override fun supportsIdKeyedBatch(): Boolean = supportsIdKeyedBatch

        override fun clearMemory() {
            clearMemoryCalls++
        }

        override fun close() {
            status = TranslationStatus.NotReady
        }
    }
}

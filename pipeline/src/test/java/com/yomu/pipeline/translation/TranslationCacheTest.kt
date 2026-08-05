package com.yomu.pipeline.translation

import android.graphics.RectF
import com.yomu.ml.TranslationBridge
import com.yomu.ml.TranslationOutput
import com.yomu.ml.TranslationStatus
import com.yomu.pipeline.bubble.Bubble
import com.yomu.pipeline.context.ConversationBlock
import com.yomu.pipeline.ocr.OcrResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationCacheTest {

    @Test
    fun normalizeForCache_trimsAndCollapsesWhitespace() {
        assertEquals("hello world", normalizeForCache("  hello   world  "))
        assertEquals("a b c", normalizeForCache("a\nb\tc"))
    }

    @Test
    fun normalizeForCache_nfkcNormalizes() {
        assertEquals("A", normalizeForCache("Ａ"))
    }

    @Test
    fun buildCacheKey_differsAcrossEngines() {
        val keyA = buildCacheKey("engineA", null, "hello")
        val keyB = buildCacheKey("engineB", null, "hello")
        assertNotEquals(keyA, keyB)
    }

    @Test
    fun buildCacheKey_differsAcrossModels() {
        val keyA = buildCacheKey("engine", "modelA", "hello")
        val keyB = buildCacheKey("engine", "modelB", "hello")
        assertNotEquals(keyA, keyB)
    }

    @Test
    fun translate_repositoryHitReturnsStoredOutputAndSkipsBridge() = runTest {
        val repo = FakeTranslationCacheRepository()
        repo.put("engine", null, "こんにちは", TranslationOutput("Hello", 0.9f, 10L))
        val bridge = FakeTranslationBridge(status = TranslationStatus.Ready)
        val engine = TranslationEngine(bridge, "engine", null, repo)

        val result = engine.translate(listOf(singleBubbleBlock("こんにちは")))

        assertEquals("Hello", result.translations.first().translatedText)
        assertEquals(0.9f, result.translations.first().confidence)
        assertEquals(0, bridge.translateCalls.size)
    }

    @Test
    fun translate_repositoryMissFallsThroughToBridgeAndWritesResult() = runTest {
        val bridge = FakeTranslationBridge(
            status = TranslationStatus.Ready,
            outputForText = mapOf(
                "こんにちは" to TranslationOutput("Hello", 0.8f, 10L)
            )
        )
        val repo = FakeTranslationCacheRepository()
        val engine = TranslationEngine(bridge, "engine", null, repo)

        val result = engine.translate(listOf(singleBubbleBlock("こんにちは")))

        assertEquals("Hello", result.translations.first().translatedText)
        assertEquals(1, repo.putCalls.size)
        assertEquals("こんにちは", repo.putCalls.first().sourceText)
    }

    @Test
    fun translate_repositoryMissWithNullBridgeOutputDoesNotWrite() = runTest {
        val bridge = FakeTranslationBridge(status = TranslationStatus.Ready)
        val repo = FakeTranslationCacheRepository()
        val engine = TranslationEngine(bridge, "engine", null, repo)

        val result = engine.translate(listOf(singleBubbleBlock("さようなら")))

        assertEquals("さようなら", result.translations.first().translatedText)
        assertEquals(0, repo.putCalls.size)
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
        private val outputForText: Map<String, TranslationOutput> = emptyMap()
    ) : TranslationBridge {
        override var status: TranslationStatus = status
        val translateCalls: MutableMap<String, Int> = mutableMapOf()

        override suspend fun ensureReady(): Boolean = status is TranslationStatus.Ready

        override suspend fun translate(sourceText: String): TranslationOutput? {
            translateCalls[sourceText] = (translateCalls[sourceText] ?: 0) + 1
            return outputForText[sourceText]
        }

        override fun supportsBatch(): Boolean = false
        override fun clearMemory() {}
        override fun close() {
            status = TranslationStatus.NotReady
        }
    }

    private class FakeTranslationCacheRepository : TranslationCacheRepository {
        private val store: MutableMap<String, TranslationOutput> = mutableMapOf()
        val putCalls: MutableList<PutCall> = mutableListOf()

        override suspend fun get(engineId: String, modelId: String?, sourceText: String): TranslationOutput? {
            return store[buildCacheKey(engineId, modelId, sourceText)]
        }

        override suspend fun put(engineId: String, modelId: String?, sourceText: String, output: TranslationOutput) {
            putCalls.add(PutCall(engineId, modelId, sourceText))
            store[buildCacheKey(engineId, modelId, sourceText)] = output
        }

        data class PutCall(
            val engineId: String,
            val modelId: String?,
            val sourceText: String
        )
    }
}

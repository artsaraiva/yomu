package com.yomu.ml

import com.yomu.core.ModelProfile
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.core.TranslationPromptMode
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaTranslationBridgeOverflowTest {

    @Test
    fun translatePage_batchOverflowFallsBackToPerLine() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { prompt ->
            if (prompt.contains("Panels are separated")) {
                GenerationResult.Overflow(5L)
            } else {
                GenerationResult.Success(if (prompt.endsWith("こんにちは")) "Hello" else "Goodbye", 10L)
            }
        }
        val slot = LlamaTranslationBridge(native, batchProfile(model))

        val result = slot.translatePage(page(1 to "こんにちは", 2 to "さようなら"))

        assertEquals(mapOf(1 to "Hello", 2 to "Goodbye"), result.byId)
        assertEquals(TranslationOutcome.SUCCESS, result.outcome)
        assertTrue(result.batchOverflowFallback)
        assertNull(result.errorCode)
        assertEquals(3, native.prompts.size)
        model.delete()
    }

    @Test
    fun translatePage_batchOverflowThenPerLineFailureReportsBoth() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { prompt ->
            when {
                prompt.contains("Panels are separated") -> GenerationResult.Overflow(5L)
                prompt.endsWith("こんにちは") -> GenerationResult.Success("Hello", 10L)
                else -> GenerationResult.Timeout(15L)
            }
        }
        val slot = LlamaTranslationBridge(native, batchProfile(model))

        val result = slot.translatePage(page(1 to "こんにちは", 2 to "さようなら"))

        assertEquals(mapOf(1 to "Hello"), result.byId)
        assertTrue(result.batchOverflowFallback)
        assertEquals(TranslationOutcome.TIMEOUT, result.outcome)
        assertEquals("deadline", result.errorCode)
        model.delete()
    }

    @Test
    fun translatePage_batchThatFitsIsNotMarkedAsFallback() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val slot = LlamaTranslationBridge(
            FakeLlamaBridge { GenerationResult.Success("[1] Hello", 1L) },
            batchProfile(model)
        )

        val result = slot.translatePage(page(1 to "こんにちは"))

        assertFalse(result.batchOverflowFallback)
        model.delete()
    }

    private fun batchProfile(model: File): ModelProfile =
        ModelProfile(model.absolutePath, true, TranslationPromptMode.MODEL_CARD)

    private fun page(vararg bubbles: Pair<Int, String>): TranslatablePage =
        TranslatablePage(listOf(bubbles.map { (id, source) -> TranslatableBubble(id, source) }))
}

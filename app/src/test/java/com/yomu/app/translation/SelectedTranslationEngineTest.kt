package com.yomu.app.translation

import android.content.SharedPreferences
import android.util.Log
import com.yomu.core.Constants
import com.yomu.core.ModelProfile
import com.yomu.core.TranslationPromptMode
import com.yomu.core.TranslationStatus
import com.yomu.ml.GenerationResult
import com.yomu.ml.LlamaBridge
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.opusmt.OpusMtTranslationBridge
import com.yomu.pipeline.context.ConversationBlock
import com.yomu.pipeline.ocr.OcrResult
import com.yomu.pipeline.translation.TranslationEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class SelectedTranslationEngineTest {
    private val native = Mockito.mock(LlamaBridge::class.java)
    private val llm = LlamaTranslationBridge(
        native,
        ModelProfile("unused.gguf", true, TranslationPromptMode.MODEL_CARD)
    )
    private val prefs = Mockito.mock(SharedPreferences::class.java)

    private fun engine(selected: String = "llm"): TranslationEngine {
        Mockito.`when`(prefs.getString(Constants.PREF_TRANSLATION_ENGINE, null)).thenReturn(selected)
        Mockito.`when`(native.isNativeAvailable).thenReturn(true)
        Mockito.`when`(native.isModelLoaded).thenReturn(true)
        val selection = EngineSelection(
            Mockito.mock(MlKitTranslationBridge::class.java),
            Mockito.mock(OpusMtTranslationBridge::class.java),
            llm, prefs
        )
        return TranslationEngine(selection::current, selection::close)
    }

    @Test
    fun `selected page translation uses page budget and timeout`() = runTest {
        Mockito.mockStatic(Log::class.java).use {
            val engine = engine()
            var prompt = ""
            var tokens = 0
            var timeout = 0
            Mockito.`when`(native.generate(Mockito.anyString(), Mockito.anyInt(), Mockito.anyFloat(), Mockito.anyInt()))
                .thenAnswer { call ->
                    prompt = call.getArgument(0)
                    tokens = call.getArgument(1)
                    timeout = call.getArgument(3)
                    GenerationResult.Success("[1] Hello\n[2] Goodbye", 1L)
                }
            val texts = listOf("こんにちは", "さようなら").map {
                OcrResult(it, 1f, floatArrayOf(0f, 0f, 1f, 1f))
            }
            val page = ConversationBlock(1, emptyList(), texts, listOf(1, 2), mapOf(1 to texts[0], 2 to texts[1]))

            val result = engine.translate(listOf(page))

            assertEquals(listOf("Hello", "Goodbye"), result.translations.map { it.translatedText })
            assertEquals((2048 - prompt.length / 2 - 96).coerceIn(256, 2048), tokens)
            assertTrue(tokens > 256)
            assertEquals(120_000, timeout)
        }
    }

    @Test
    fun `engine teardown releases native weights even when LLM is inactive`() = runTest {
        Mockito.mockStatic(Log::class.java).use {
            val engine = engine("ml_kit")
            assertTrue(llm.ensureReady())

            engine.close()

            Mockito.verify(native).release()
            assertEquals(TranslationStatus.NotReady, llm.status)
        }
    }
}

package com.yomu.ml

import com.yomu.core.GenerationParams
import com.yomu.core.ModelProfile
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.core.TranslationPromptMode
import com.yomu.core.TranslationStatus
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaTranslationBridgeTest {

    @Test
    fun translatePage_modelCardTranslatesEachBubbleWithoutContext() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { prompt ->
            GenerationResult.Success(if (prompt.endsWith("こんにちは")) "Hello" else "Goodbye", 10L)
        }
        val slot = LlamaTranslationBridge(native, profile(model, TranslationPromptMode.MODEL_CARD))

        val result = slot.translatePage(page(1 to "こんにちは", 2 to "さようなら"))

        assertEquals(mapOf(1 to "Hello", 2 to "Goodbye"), result.byId)
        assertEquals(2, native.prompts.size)
        assertTrue(native.prompts[0].endsWith("こんにちは"))
        assertFalse(native.prompts[0].contains("さようなら"))
        assertEquals(20L, result.durationMs)
        model.delete()
    }

    @Test
    fun translatePage_translationOnlyPromptContainsOnlyTheTarget() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { GenerationResult.Success("Hello", 1L) }
        val slot = LlamaTranslationBridge(native, profile(model, TranslationPromptMode.TRANSLATION_ONLY))

        slot.translatePage(page(1 to "こんにちは", 2 to "さようなら"))

        assertTrue(native.prompts.first().contains("Return only"))
        assertTrue(native.prompts.first().endsWith("こんにちは"))
        assertFalse(native.prompts.first().contains("さようなら"))
        model.delete()
    }

    @Test
    fun translatePage_idKeyedBatchBuildsOnePagePromptAndParsesById() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge {
            GenerationResult.Success("Here are the translations:\n[2] Goodbye\nignored\n[1] Hello", 100L)
        }
        val slot = LlamaTranslationBridge(native, profile(model, idKeyedBatch = true))
        val page = TranslatablePage(
            listOf(
                listOf(TranslatableBubble(1, "こんにちは")),
                listOf(TranslatableBubble(2, "さようなら"))
            )
        )

        val result = slot.translatePage(page)

        assertEquals(mapOf(2 to "Goodbye", 1 to "Hello"), result.byId)
        assertEquals(1, native.prompts.size)
        assertTrue(native.prompts.single().contains("[1] こんにちは\n---\n[2] さようなら"))
        assertEquals(100L, result.durationMs)
        model.delete()
    }

    @Test
    fun translatePage_idKeyedBatchOmitsMissingIds() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val slot = LlamaTranslationBridge(
            FakeLlamaBridge { GenerationResult.Success("[1] Hello", 1L) },
            profile(model, idKeyedBatch = true)
        )

        val result = slot.translatePage(page(1 to "こんにちは", 2 to "さようなら"))

        assertEquals(mapOf(1 to "Hello"), result.byId)
        model.delete()
    }

    @Test
    fun translatePage_blankOrFailedLinesAreOmitted() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val results = ArrayDeque<GenerationResult>().apply {
            add(GenerationResult.Blank(1L))
            add(GenerationResult.Error("fail", 2L))
        }
        val slot = LlamaTranslationBridge(
            FakeLlamaBridge { results.removeFirst() },
            profile(model)
        )

        val result = slot.translatePage(page(1 to "一", 2 to "二"))

        assertTrue(result.byId.isEmpty())
        model.delete()
    }

    @Test
    fun translatePage_batchPinsTheReplyShapeWithAGrammarPerId() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { GenerationResult.Success("[1] Hello\n[2] Bye", 1L) }
        val slot = LlamaTranslationBridge(native, profile(model, idKeyedBatch = true))

        slot.translatePage(page(1 to "こんにちは", 2 to "さようなら"))

        val grammar = native.grammars.single()
        assertTrue(grammar, grammar.startsWith("root ::= \"[1] \" line \"\\n\" \"[2] \" line \"\\n\""))
        assertTrue(grammar, grammar.contains("line ::= [^\\r\\n]{1,160}"))
        model.delete()
    }

    @Test
    fun translatePage_perLineSamplesUnconstrained() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { GenerationResult.Success("Hello", 1L) }
        val slot = LlamaTranslationBridge(native, profile(model))

        slot.translatePage(page(1 to "こんにちは"))

        assertEquals(listOf(""), native.grammars)
        model.delete()
    }

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
        val slot = LlamaTranslationBridge(native, profile(model, idKeyedBatch = true))

        val result = slot.translatePage(page(1 to "こんにちは", 2 to "さようなら"))

        assertEquals(mapOf(1 to "Hello", 2 to "Goodbye"), result.byId)
        assertEquals(TranslationOutcome.SUCCESS, result.outcome)
        assertEquals(LlamaTranslationBridge.BATCH_OVERFLOW_FALLBACK, result.errorCode)
        assertEquals(3, native.prompts.size)
        model.delete()
    }

    @Test
    fun translatePage_batchBudgetIsTheFixedPageCap() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { GenerationResult.Success("[1] Hello", 1L) }
        val slot = LlamaTranslationBridge(native, profile(model, idKeyedBatch = true))

        slot.translatePage(page(1 to "こんにちは".repeat(50)))

        assertEquals(listOf(768), native.maxTokens)
        model.delete()
    }

    @Test
    fun generate_outOfBoundsSamplerValuesReachNativeAsDefaults() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { GenerationResult.Success("Hello", 1L) }
        val unsafe = GenerationParams(temperature = Float.NaN, topK = 0, topP = 0.5f, penaltyRepeat = 1.1f)
        val slot = LlamaTranslationBridge(native, profile(model).copy(generation = unsafe))

        slot.translatePage(page(1 to "こんにちは"))

        assertEquals(listOf(GenerationParams(topP = 0.5f, penaltyRepeat = 1.1f)), native.params)
        model.delete()
    }

    @Test
    fun generate_inBoundsSamplerValuesReachNativeUnchanged() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { GenerationResult.Success("Hello", 1L) }
        val tuned = GenerationParams(temperature = 1f, topK = 1, topP = 0f)
        val slot = LlamaTranslationBridge(native, profile(model).copy(generation = tuned))

        slot.translatePage(page(1 to "こんにちは"))

        assertEquals(listOf(tuned), native.params)
        model.delete()
    }

    @Test
    fun selectModel_switchesTheWholeProfileAndDropsReadiness() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { GenerationResult.Success("x", 1L) }
        val slot = LlamaTranslationBridge(native, profile(model))
        assertTrue(slot.ensureReady())

        slot.selectModel(ModelProfile("/other.gguf", true, TranslationPromptMode.TRANSLATION_ONLY))

        assertEquals(TranslationStatus.NotReady, slot.status)
        assertEquals(1, native.releaseCalls)
        model.delete()
    }

    @Test
    fun selectModel_sameProfileIsANoOp() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val native = FakeLlamaBridge { GenerationResult.Success("x", 1L) }
        val profile = profile(model)
        val slot = LlamaTranslationBridge(native, profile)
        assertTrue(slot.ensureReady())

        slot.selectModel(profile)

        assertEquals(TranslationStatus.Ready, slot.status)
        assertEquals(0, native.releaseCalls)
        model.delete()
    }

    @Test
    fun endSessionClearsMemoryAndCloseReleasesWeights() {
        val native = FakeLlamaBridge { GenerationResult.Success("x", 1L) }
        val slot = LlamaTranslationBridge(native, ModelProfile("", false, TranslationPromptMode.MODEL_CARD))

        slot.endSession()
        slot.close()

        assertEquals(1, native.clearMemoryCalls)
        assertEquals(1, native.releaseCalls)
        assertEquals(TranslationStatus.NotReady, slot.status)
    }

    private fun profile(
        model: File,
        promptMode: TranslationPromptMode = TranslationPromptMode.MODEL_CARD,
        idKeyedBatch: Boolean = false
    ): ModelProfile = ModelProfile(model.absolutePath, idKeyedBatch, promptMode)

    private fun page(vararg bubbles: Pair<Int, String>): TranslatablePage =
        TranslatablePage(listOf(bubbles.map { (id, source) -> TranslatableBubble(id, source) }))

    private class FakeLlamaBridge(
        private val resultForPrompt: (String) -> GenerationResult
    ) : LlamaBridge(null) {
        val prompts = mutableListOf<String>()
        val grammars = mutableListOf<String>()
        val maxTokens = mutableListOf<Int>()
        val params = mutableListOf<GenerationParams>()
        var releaseCalls = 0
        var clearMemoryCalls = 0

        override val isNativeAvailable: Boolean get() = true
        override val isModelLoaded: Boolean get() = true
        override fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int): Boolean = true
        override fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int, nThreads: Int): Boolean = true
        override fun generate(
            prompt: String,
            params: GenerationParams,
            maxTokens: Int,
            timeoutMs: Int,
            grammar: String
        ): GenerationResult {
            prompts += prompt
            grammars += grammar
            this.maxTokens += maxTokens
            this.params += params
            return resultForPrompt(prompt)
        }

        override fun release() {
            releaseCalls++
        }

        override fun clearMemory() {
            clearMemoryCalls++
        }
    }
}

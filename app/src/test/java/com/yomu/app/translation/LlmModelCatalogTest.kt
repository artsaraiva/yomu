package com.yomu.app.translation

import com.yomu.core.Constants
import com.yomu.core.GenerationParams
import com.yomu.core.RuntimeLimits
import com.yomu.core.TranslationPromptMode
import com.yomu.core.withinBounds
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmModelCatalogTest {

    private val eightGb = 8L * 1024 * 1024 * 1024
    private val fourGb = 4L * 1024 * 1024 * 1024

    private fun fit(
        totalMemBytes: Long,
        percent: Int = LlmModelCatalog.DEFAULT_FIT_BUDGET_PERCENT,
        contextTokens: Int = RuntimeLimits.DEFAULT_CONTEXT_TOKENS
    ) = FitBudget(totalMemBytes, percent, contextTokens)

    @Test
    fun `default is Qwen2_5 1_5B`() {
        assertEquals(Constants.QWEN25_15B_MODEL_ID, LlmModelCatalog.DEFAULT.id)
        assertEquals(TranslationPromptMode.TRANSLATION_ONLY, LlmModelCatalog.DEFAULT.promptMode)
    }

    @Test
    fun `default is not experimental`() {
        assertFalse(LlmModelCatalog.DEFAULT.experimental)
    }

    @Test
    fun `Qwen3_5 2B is an experimental page-batch entry`() {
        val qwen35 = LlmModelCatalog.fromId(Constants.QWEN35_2B_MODEL_ID)!!

        assertTrue(qwen35.experimental)
        assertTrue(qwen35.idKeyedBatch)
        assertEquals(TranslationPromptMode.TRANSLATION_ONLY, qwen35.promptMode)
        assertEquals("Apache-2.0", qwen35.licence)
    }

    @Test
    fun `the entries phone-checked before the Experimental tier are not experimental`() {
        val checked = listOf(Constants.QWEN25_15B_MODEL_ID, Constants.CAT_TRANSLATION_MODEL_ID, Constants.CAT_TRANSLATION_14B_MODEL_ID)
        checked.forEach { assertFalse(it, LlmModelCatalog.fromId(it)!!.experimental) }
    }

    @Test
    fun `selectedOrDefault falls back to default for null or unknown id`() {
        assertEquals(LlmModelCatalog.DEFAULT, LlmModelCatalog.selectedOrDefault(null))
        assertEquals(LlmModelCatalog.DEFAULT, LlmModelCatalog.selectedOrDefault("no-such-model"))
    }

    @Test
    fun `selectedOrDefault resolves a known id`() {
        val floor = LlmModelCatalog.selectedOrDefault(Constants.CAT_TRANSLATION_MODEL_ID)
        assertEquals(Constants.CAT_TRANSLATION_MODEL_ID, floor.id)
        assertEquals(TranslationPromptMode.MODEL_CARD, floor.promptMode)
    }

    @Test
    fun `profileFor carries the supplied generation profile unchanged onto every entry`() {
        val generation = GenerationParams(temperature = 0.7f, topK = 12, topP = 0.5f)

        LlmModelCatalog.ALL.forEach { option ->
            assertEquals(option.displayName, generation, LlmModelCatalog.profileFor(option, File("m"), generation, RuntimeLimits()).generation)
        }
    }

    @Test
    fun `profileFor carries the supplied runtime limits onto the profile`() {
        val runtime = RuntimeLimits(threads = 2, contextTokens = 1024)

        assertEquals(runtime, LlmModelCatalog.profileFor(LlmModelCatalog.DEFAULT, File("m"), GenerationParams(), runtime).runtime)
    }

    @Test
    fun `default is never gated out even on a tiny device`() {
        assertTrue(LlmModelCatalog.canRunOnDevice(LlmModelCatalog.DEFAULT, fit(1L, percent = 30)))
    }

    @Test
    fun `a lower fit budget gates out a model the default budget offers`() {
        val model = LlmModelCatalog.fromId(Constants.CAT_TRANSLATION_14B_MODEL_ID)!!

        assertTrue(LlmModelCatalog.canRunOnDevice(model, fit(fourGb)))
        assertFalse(LlmModelCatalog.canRunOnDevice(model, fit(fourGb, percent = 30)))
    }

    @Test
    fun `a larger context's KV cache counts against the fit budget`() {
        val model = LlmModelCatalog.fromId(Constants.CAT_TRANSLATION_14B_MODEL_ID)!!

        assertTrue(LlmModelCatalog.canRunOnDevice(model, fit(fourGb, percent = 45, contextTokens = 1536)))
        assertFalse(LlmModelCatalog.canRunOnDevice(model, fit(fourGb, percent = 45, contextTokens = 2816)))
    }

    @Test
    fun `every curated entry carries a KV cache cost`() {
        LlmModelCatalog.ALL.forEach { assertTrue(it.displayName, it.kvCacheBytesPerToken > 0) }
    }

    @Test
    fun `a small model fits an 8GB device`() {
        val floor = LlmModelCatalog.selectedOrDefault(Constants.CAT_TRANSLATION_MODEL_ID)
        assertTrue(LlmModelCatalog.canRunOnDevice(floor, fit(eightGb)))
    }

    @Test
    fun `every shortlist model fits an 8GB device`() {
        // The largest curated entry is ~2.5GB GGUF; none reach the 7B footprint that OOMs on 8GB (#84).
        LlmModelCatalog.ALL.forEach { option ->
            assertTrue(option.displayName, LlmModelCatalog.canRunOnDevice(option, fit(eightGb)))
        }
    }

    @Test
    fun `a large model is gated out on a low-RAM 4GB device`() {
        // A ~3GB GGUF plus resident overhead exceeds the usable-RAM budget on 4GB. Synthetic so the
        // test does not depend on a specific large model staying in the shortlist.
        val big = LlmModelCatalog.DEFAULT.copy(id = "synthetic_big", sizeBytes = 3_000_000_000L)
        assertFalse(LlmModelCatalog.canRunOnDevice(big, fit(fourGb)))
    }

    @Test
    fun `the low-storage floor still fits a 4GB device`() {
        val floor = LlmModelCatalog.fromId(Constants.CAT_TRANSLATION_MODEL_ID)!!
        assertTrue(LlmModelCatalog.canRunOnDevice(floor, fit(fourGb)))
    }

    @Test
    fun `the entries curated before ADR-0017 keep today's global sampling`() {
        // AC of #283: a reader already on one of these sees no change when maker defaults arrive.
        listOf(Constants.QWEN25_15B_MODEL_ID, Constants.CAT_TRANSLATION_MODEL_ID, Constants.CAT_TRANSLATION_14B_MODEL_ID)
            .forEach { id ->
                val option = LlmModelCatalog.fromId(id)!!
                assertEquals(option.displayName, GenerationParams(), option.generationDefaults)
            }
    }

    @Test
    fun `Qwen3_5 2B carries its card's sampling, not the shipped floor`() {
        val qwen35 = LlmModelCatalog.fromId(Constants.QWEN35_2B_MODEL_ID)!!

        assertEquals(
            GenerationParams(temperature = 1.0f, topK = 20, topP = 1.0f, penaltyPresent = 2.0f),
            qwen35.generationDefaults
        )
    }

    @Test
    fun `every entry's maker sampling survives the inference boundary`() {
        // LlamaTranslationBridge re-checks the profile through withinBounds(), which replaces a
        // reader-facing field outside its bound with the shipped default. A maker value outside a
        // bound would be dropped there without a word, so it must not reach the catalog.
        LlmModelCatalog.ALL.forEach {
            assertEquals(it.displayName, it.generationDefaults, it.generationDefaults.withinBounds())
        }
    }

    @Test
    fun `every curated entry carries a redistributable licence`() {
        // ADR-0009/ADR-0014: Yomu hosts every curated model, so a licence it cannot redistribute
        // keeps a model out of the catalog entirely.
        val redistributable = setOf("MIT", "Apache-2.0")
        LlmModelCatalog.ALL.forEach {
            assertTrue("${it.displayName}: ${it.licence}", it.licence in redistributable)
        }
    }
}

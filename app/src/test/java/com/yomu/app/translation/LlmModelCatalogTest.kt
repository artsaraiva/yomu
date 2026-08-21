package com.yomu.app.translation

import com.yomu.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmModelCatalogTest {

    private val eightGb = 8L * 1024 * 1024 * 1024
    private val fourGb = 4L * 1024 * 1024 * 1024

    @Test
    fun `default is Qwen2_5 1_5B`() {
        assertEquals(Constants.QWEN25_15B_MODEL_ID, LlmModelCatalog.DEFAULT.id)
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
    }

    @Test
    fun `default is never gated out even on a tiny device`() {
        assertTrue(LlmModelCatalog.canRunOnDevice(LlmModelCatalog.DEFAULT, totalMemBytes = 1L))
    }

    @Test
    fun `a small model fits an 8GB device`() {
        val floor = LlmModelCatalog.selectedOrDefault(Constants.CAT_TRANSLATION_MODEL_ID)
        assertTrue(LlmModelCatalog.canRunOnDevice(floor, eightGb))
    }

    @Test
    fun `every shortlist model fits an 8GB device`() {
        // The largest curated entry is ~2.5GB GGUF; none reach the 7B footprint that OOMs on 8GB (#84).
        LlmModelCatalog.ALL.forEach { option ->
            assertTrue(option.displayName, LlmModelCatalog.canRunOnDevice(option, eightGb))
        }
    }

    @Test
    fun `a large model is gated out on a low-RAM 4GB device`() {
        // A ~3GB GGUF plus resident overhead exceeds the usable-RAM budget on 4GB. Synthetic so the
        // test does not depend on a specific large model staying in the shortlist.
        val big = LlmModelCatalog.DEFAULT.copy(id = "synthetic_big", sizeBytes = 3_000_000_000L)
        assertFalse(LlmModelCatalog.canRunOnDevice(big, fourGb))
    }

    @Test
    fun `the low-storage floor still fits a 4GB device`() {
        val floor = LlmModelCatalog.fromId(Constants.CAT_TRANSLATION_MODEL_ID)!!
        assertTrue(LlmModelCatalog.canRunOnDevice(floor, fourGb))
    }

    @Test
    fun `the shortlist is all hosted today (no usable gated GGUF for the HF-auth candidates)`() {
        assertTrue(LlmModelCatalog.ALL.all { it.tier == LlmModelTier.HOSTED })
    }

    @Test
    fun `every hosted entry carries a redistribution-compatible licence (AC 6)`() {
        // ADR-0009: no entry ships HOSTED until its licence permits Yomu redistribution. Anything
        // else must be HF_AUTH (fetched under the user's own account), never hosted.
        val redistributable = setOf("MIT", "Apache-2.0")
        LlmModelCatalog.ALL.filter { it.tier == LlmModelTier.HOSTED }.forEach {
            assertTrue("${it.displayName}: ${it.licence}", it.licence in redistributable)
        }
        LlmModelCatalog.ALL.filter { it.tier == LlmModelTier.HF_AUTH }.forEach {
            assertFalse("${it.displayName}: ${it.licence}", it.licence in redistributable)
        }
    }
}

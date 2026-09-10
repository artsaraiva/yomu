package com.yomu.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationParamsTest {

    /** The C++ side reads this array by index, so a reordering here is a silent behaviour change. */
    @Test
    fun `sampler array matches the documented index order`() {
        val params = GenerationParams(
            temperature = 1f,
            topK = 2,
            topP = 3f,
            penaltyLastN = 4,
            penaltyRepeat = 5f,
            penaltyFreq = 6f,
            penaltyPresent = 7f
        )
        assertEquals(
            listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f),
            params.samplerArray().toList()
        )
        assertEquals(GenerationParams.SAMPLER_INDEX.size, params.samplerArray().size)
    }

    @Test
    fun `defaults match the values that were hardcoded before they moved here`() {
        val params = GenerationParams()
        assertEquals(0.2f, params.temperature, 0f)
        assertEquals(40, params.topK)
        assertEquals(0.9f, params.topP, 0f)
        assertEquals(1.0f, params.penaltyRepeat, 0f)
        assertEquals(256, params.maxTokens)
        // LLAMA_DEFAULT_SEED is 0xFFFFFFFF; as a signed Int that is -1.
        assertEquals(-1, params.seed)
    }
}

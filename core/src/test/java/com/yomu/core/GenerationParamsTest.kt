package com.yomu.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationParamsTest {

    /**
     * The C++ side reads this array by index against its own SamplerIndex enum, so a reordering
     * here is a silent behaviour change. Indices are looked up through SAMPLER_INDEX rather than
     * written as literals, so that constant is the one place the order is stated on this side.
     */
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
        val array = params.samplerArray()

        assertEquals(GenerationParams.SAMPLER_INDEX.size, array.size)
        assertEquals(1f, array[indexOf("temperature")], 0f)
        assertEquals(2f, array[indexOf("topK")], 0f)
        assertEquals(3f, array[indexOf("topP")], 0f)
        assertEquals(4f, array[indexOf("penaltyLastN")], 0f)
        assertEquals(5f, array[indexOf("penaltyRepeat")], 0f)
        assertEquals(6f, array[indexOf("penaltyFreq")], 0f)
        assertEquals(7f, array[indexOf("penaltyPresent")], 0f)
    }

    @Test
    fun `defaults match the values that were hardcoded before they moved here`() {
        val params = GenerationParams()
        assertEquals(0.2f, params.temperature, 0f)
        assertEquals(40, params.topK)
        assertEquals(0.9f, params.topP, 0f)
        assertEquals(1.0f, params.penaltyRepeat, 0f)
        assertEquals(64, params.penaltyLastN)
        assertEquals(0.0f, params.penaltyFreq, 0f)
        assertEquals(0.0f, params.penaltyPresent, 0f)
        assertEquals(256, params.maxTokens)
        // LLAMA_DEFAULT_SEED is 0xFFFFFFFF; as a signed Int that is -1.
        assertEquals(-1, params.seed)
    }

    private fun indexOf(field: String): Int =
        GenerationParams.SAMPLER_INDEX.indexOf(field).also { require(it >= 0) { "unknown: $field" } }
}

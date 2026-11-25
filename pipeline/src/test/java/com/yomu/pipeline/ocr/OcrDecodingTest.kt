package com.yomu.pipeline.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrDecodingTest {

    private val stop = { id: Int -> id == 3 }

    @Test
    fun `choose takes the highest logit and its softmax probability`() {
        // The second step of a two-step logits buffer: softmax(1, 3, 2) is 0.090, 0.665, 0.245.
        val logits = floatArrayOf(9f, 0f, 0f, 1f, 3f, 2f)

        val choice = chooseToken(logits, start = 3, count = 3)

        assertEquals(1, choice.id)
        assertEquals(0.665f, choice.probability, 0.001f)
    }

    @Test
    fun `greedy confidence is the mean probability of every choice including the stop`() {
        val script = ArrayDeque(listOf(TokenChoice(10, 1.0f), TokenChoice(11, 0.5f), TokenChoice(3, 0.9f)))

        val decoded = decodeGreedy(stop) { script.removeFirst() }

        assertEquals(listOf(10, 11), decoded.tokenIds)
        assertEquals(0.8f, decoded.confidence, 0.0001f)
    }

    @Test
    fun `greedy confidence is zero when the decoder never answers`() {
        assertEquals(0f, decodeGreedy(stop) { null }.confidence)
    }

    @Test
    fun `greedy hands each step the token the previous one chose, none to the first`() {
        val script = ArrayDeque(listOf(10, 11, 3, 12))
        val seen = mutableListOf<Int?>()

        val decoded = decodeGreedy(stop) { previous ->
            seen.add(previous)
            TokenChoice(script.removeFirst(), 1f)
        }

        assertEquals(listOf(10, 11), decoded.tokenIds)
        assertEquals(listOf(null, 10, 11), seen)
    }

    @Test
    fun `greedy stops at its step budget when no stop token comes`() {
        val decoded = decodeGreedy(stop) { TokenChoice(10, 1f) }

        assertEquals(128, decoded.tokenIds.size)
    }
}

package com.yomu.pipeline

import com.yomu.pipeline.translation.TranslatedBubble
import com.yomu.pipeline.typesetting.Typesetter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranslationPipelineTypesettingTest {

    private fun bubble(id: Int, text: String, answered: Boolean = true) = TranslatedBubble(
        bubbleId = id,
        originalText = "",
        translatedText = text,
        answered = answered
    )

    @Test
    fun `long translation cannot grow an answered overlay beyond its checked box`() {
        val renderBox = floatArrayOf(100f, 100f, 140f, 140f)
        val translations = listOf(
            bubble(1, "The quick brown fox jumps over the lazy dog. ".repeat(3)),
            bubble(2, "Unanswered", answered = false)
        )
        val renderBoxes = mapOf(1 to renderBox, 2 to floatArrayOf(140f, 100f, 180f, 140f))
        val grown = Typesetter().typeset(translations.take(1), renderBoxes).single()
        assertTrue(grown.boundingBox[3] > renderBox[3])

        val result = typesetAnsweredBubbles(Typesetter(), translations, renderBoxes)

        assertEquals(1, result.size)
        assertArrayEquals(renderBox, result.single().boundingBox, 0f)
    }

    @Test
    fun `word widening cannot grow an answered overlay beyond its checked box`() {
        val renderBox = floatArrayOf(100f, 100f, 103f, 140f)
        val result = typesetAnsweredBubbles(
            Typesetter(),
            listOf(bubble(1, "Yes!")),
            mapOf(1 to renderBox)
        )

        assertArrayEquals(renderBox, result.single().boundingBox, 0f)
        assertEquals(listOf("Yes!"), result.single().textLines)
    }

    @Test
    fun `translation fitting its checked box keeps its layout`() {
        val renderBox = floatArrayOf(0f, 0f, 200f, 100f)
        val result = typesetAnsweredBubbles(
            Typesetter(),
            listOf(bubble(1, "Hello")),
            mapOf(1 to renderBox)
        )

        assertArrayEquals(renderBox, result.single().boundingBox, 0f)
        assertEquals(listOf("Hello"), result.single().textLines)
    }
}

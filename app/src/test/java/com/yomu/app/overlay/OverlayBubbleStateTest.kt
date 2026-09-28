package com.yomu.app.overlay

import com.yomu.pipeline.typesetting.TypesetBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayBubbleStateTest {

    /** Builds an untranslated preview with fixed bounds for state-update assertions. */
    private fun ocr(bubbleId: Int, text: String) = OverlayBubbleState(
        bubbleId = bubbleId,
        bounds = OverlayBounds(left = 0f, top = 0f, right = 100f, bottom = 50f),
        ocrText = text
    )

    /** Builds a single-line typeset bubble with a chosen ID and translation. */
    private fun typeset(bubbleId: Int, text: String) = TypesetBubble(
        bubbleId = bubbleId,
        translatedText = text,
        originalText = "",
        fontSize = 12f,
        textLines = listOf(text),
        boundingBox = floatArrayOf(0f, 0f, 120f, 60f)
    )

    /** Verifies that an OCR preview has no typeset result before translation arrives. */
    @Test
    fun `a preview bubble starts with no typeset text`() {
        assertNull(ocr(1, "こんにちは").typeset)
    }

    /** Verifies ID-based replacement preserves the other preview and both original OCR texts. */
    @Test
    fun `an arriving bubble switches only the preview with its id`() {
        val hello = typeset(1, "Hello")

        val states = listOf(ocr(1, "こんにちは"), ocr(2, "さようなら")).withTypeset(hello)

        assertEquals(listOf(hello, null), states.map { it.typeset })
        assertEquals(listOf("こんにちは", "さようなら"), states.map { it.ocrText })
    }

    /** Verifies a result for an unknown ID leaves all preview states unchanged. */
    @Test
    fun `a bubble with no preview changes nothing`() {
        val states = listOf(ocr(1, "こんにちは"))

        assertEquals(states, states.withTypeset(typeset(9, "Stray")))
    }
}

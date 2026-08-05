package com.yomu.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayBubbleStateTest {

    @Test
    fun `isTranslated is false when only ocrText is set`() {
        val state = OverlayBubbleState(
            bubbleId = 1,
            bounds = OverlayBounds(left = 10f, top = 20f, right = 110f, bottom = 70f),
            ocrText = "こんにちは",
            translatedText = null
        )

        assertFalse(state.isTranslated)
        assertEquals("こんにちは", state.ocrText)
        assertEquals(null, state.translatedText)
    }

    @Test
    fun `isTranslated is true when translatedText is set`() {
        val state = OverlayBubbleState(
            bubbleId = 2,
            bounds = OverlayBounds(left = 10f, top = 20f, right = 110f, bottom = 70f),
            ocrText = "こんにちは",
            translatedText = "Hello"
        )

        assertTrue(state.isTranslated)
        assertEquals("Hello", state.translatedText)
    }

    @Test
    fun `states with same content are equal`() {
        val first = OverlayBubbleState(
            bubbleId = 3,
            bounds = OverlayBounds(left = 0f, top = 0f, right = 100f, bottom = 50f),
            ocrText = "テスト",
            translatedText = "Test"
        )
        val second = OverlayBubbleState(
            bubbleId = 3,
            bounds = OverlayBounds(left = 0f, top = 0f, right = 100f, bottom = 50f),
            ocrText = "テスト",
            translatedText = "Test"
        )

        assertEquals(first, second)
    }

    @Test
    fun `list updates replace existing state by bubbleId`() {
        val states = mutableListOf(
            OverlayBubbleState(
                bubbleId = 1,
                bounds = OverlayBounds(left = 0f, top = 0f, right = 100f, bottom = 50f),
                ocrText = "元のテキスト",
                translatedText = null
            ),
            OverlayBubbleState(
                bubbleId = 2,
                bounds = OverlayBounds(left = 200f, top = 0f, right = 300f, bottom = 50f),
                ocrText = "別のテキスト",
                translatedText = null
            )
        )

        states[0] = states[0].copy(translatedText = "Original text")

        assertEquals(2, states.size)
        assertTrue(states[0].isTranslated)
        assertEquals("Original text", states[0].translatedText)
        assertFalse(states[1].isTranslated)
    }

    @Test
    fun `copy preserves bubbleId and bounds`() {
        val original = OverlayBubbleState(
            bubbleId = 5,
            bounds = OverlayBounds(left = 10f, top = 10f, right = 100f, bottom = 100f),
            ocrText = "日本語",
            translatedText = null
        )
        val updated = original.copy(translatedText = "Japanese")

        assertEquals(original.bubbleId, updated.bubbleId)
        assertEquals(original.bounds, updated.bounds)
        assertEquals(original.ocrText, updated.ocrText)
        assertTrue(updated.isTranslated)
    }
}

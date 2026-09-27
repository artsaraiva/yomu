package com.yomu.app.overlay

import com.yomu.app.overlay.TranslatedPageGestures.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslatedPageGesturesTest {

    private val speech = OverlayBounds(100f, 100f, 300f, 200f)
    private val narration = OverlayBounds(250f, 150f, 400f, 300f)

    private fun tap(x: Float, y: Float, bubbles: List<OverlayBounds>, cardOpen: Boolean = false): Outcome {
        val gestures = TranslatedPageGestures(touchSlop = 12f)
        gestures.onDown(x, y)
        return gestures.onUp(x + 2f, y + 2f, bubbles, cardOpen)
    }

    @Test
    fun `tap inside a bubble opens its card`() {
        assertEquals(Outcome.OpenCard(0), tap(150f, 150f, listOf(speech)))
    }

    @Test
    fun `tap outside every bubble dismisses the page`() {
        assertEquals(Outcome.DismissPage, tap(50f, 500f, listOf(speech, narration)))
    }

    @Test
    fun `tap where bubbles overlap opens the one drawn on top`() {
        assertEquals(Outcome.OpenCard(1), tap(275f, 175f, listOf(speech, narration)))
    }

    @Test
    fun `tap while a card is open closes only the card`() {
        assertEquals(Outcome.CloseCard, tap(150f, 150f, listOf(speech), cardOpen = true))
        assertEquals(Outcome.CloseCard, tap(50f, 500f, listOf(speech), cardOpen = true))
    }

    @Test
    fun `swipe dismisses the page as soon as it passes the touch slop`() {
        val gestures = TranslatedPageGestures(touchSlop = 12f)

        gestures.onDown(150f, 150f)

        assertEquals(Outcome.None, gestures.onMove(155f, 155f))
        assertEquals(Outcome.DismissPage, gestures.onMove(150f, 180f))
    }

    @Test
    fun `swipe that starts on a bubble still dismisses the page`() {
        val gestures = TranslatedPageGestures(touchSlop = 12f)

        gestures.onDown(150f, 150f)

        assertEquals(Outcome.DismissPage, gestures.onUp(150f, 400f, listOf(speech), cardOpen = true))
    }
}

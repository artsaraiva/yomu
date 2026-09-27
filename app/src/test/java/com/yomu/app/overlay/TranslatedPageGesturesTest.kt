package com.yomu.app.overlay

import android.view.MotionEvent
import com.yomu.app.overlay.TranslatedPageGestures.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslatedPageGesturesTest {

    private val speech = OverlayBounds(100f, 100f, 300f, 200f)
    private val narration = OverlayBounds(250f, 150f, 400f, 300f)
    private val card = OverlayBounds(16f, 1000f, 1064f, 1400f)

    private fun tap(x: Float, y: Float, bubbles: List<OverlayBounds>, openCard: OverlayBounds? = null): Outcome {
        val gestures = TranslatedPageGestures(touchSlop = 12f)
        gestures.onTouch(MotionEvent.ACTION_DOWN, x, y, bubbles, openCard)
        return gestures.onTouch(MotionEvent.ACTION_UP, x + 2f, y + 2f, bubbles, openCard)
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
    fun `tap outside an open card closes only the card`() {
        assertEquals(Outcome.CloseCard, tap(150f, 150f, listOf(speech), openCard = card))
        assertEquals(Outcome.CloseCard, tap(50f, 500f, listOf(speech), openCard = card))
    }

    @Test
    fun `tap on an open card keeps it open`() {
        assertEquals(Outcome.None, tap(500f, 1200f, listOf(speech), openCard = card))
    }

    @Test
    fun `swipe that starts on an open card dismisses the page`() {
        val gestures = TranslatedPageGestures(touchSlop = 12f)

        gestures.onTouch(MotionEvent.ACTION_DOWN, 500f, 1200f, listOf(speech), card)

        assertEquals(Outcome.DismissPage, gestures.onTouch(MotionEvent.ACTION_MOVE, 500f, 1100f, listOf(speech), card))
    }

    @Test
    fun `swipe dismisses the page as soon as it passes the touch slop`() {
        val gestures = TranslatedPageGestures(touchSlop = 12f)

        assertEquals(Outcome.None, gestures.onTouch(MotionEvent.ACTION_DOWN, 150f, 150f, listOf(speech), null))
        assertEquals(Outcome.None, gestures.onTouch(MotionEvent.ACTION_MOVE, 155f, 155f, listOf(speech), null))
        assertEquals(Outcome.DismissPage, gestures.onTouch(MotionEvent.ACTION_MOVE, 150f, 180f, listOf(speech), null))
    }

    @Test
    fun `swipe that starts on a bubble still dismisses the page`() {
        val gestures = TranslatedPageGestures(touchSlop = 12f)

        gestures.onTouch(MotionEvent.ACTION_DOWN, 150f, 150f, listOf(speech), card)

        assertEquals(Outcome.DismissPage, gestures.onTouch(MotionEvent.ACTION_UP, 150f, 400f, listOf(speech), card))
    }
}

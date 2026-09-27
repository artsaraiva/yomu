package com.yomu.app.overlay

import android.view.MotionEvent

class TranslatedPageGestures(touchSlop: Float) {

    sealed interface Outcome {
        data object None : Outcome
        data object DismissPage : Outcome
        data object CloseCard : Outcome
        data class OpenCard(val bubbleIndex: Int) : Outcome
    }

    private val classifier = TouchGestureClassifier(touchSlop)

    /** [drawnBubbles] in draw order, so a later bubble covers an earlier one. */
    fun onTouch(action: Int, x: Float, y: Float, drawnBubbles: List<OverlayBounds>, cardOpen: Boolean): Outcome =
        when (action) {
            MotionEvent.ACTION_DOWN -> Outcome.None.also { classifier.onActionDown(x, y) }
            MotionEvent.ACTION_MOVE -> if (classifier.onActionMove(x, y)) Outcome.DismissPage else Outcome.None
            MotionEvent.ACTION_UP -> onUp(x, y, drawnBubbles, cardOpen)
            else -> Outcome.None
        }

    private fun onUp(x: Float, y: Float, drawnBubbles: List<OverlayBounds>, cardOpen: Boolean): Outcome {
        if (classifier.onActionUp(x, y) == TouchGestureClassifier.Gesture.DRAG) return Outcome.DismissPage
        if (cardOpen) return Outcome.CloseCard
        val hit = drawnBubbles.indexOfLast { x in it.left..it.right && y in it.top..it.bottom }
        return if (hit >= 0) Outcome.OpenCard(hit) else Outcome.DismissPage
    }
}

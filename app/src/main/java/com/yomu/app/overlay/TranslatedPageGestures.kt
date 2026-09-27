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
    fun onTouch(action: Int, x: Float, y: Float, drawnBubbles: List<OverlayBounds>, openCard: OverlayBounds?): Outcome =
        when (action) {
            MotionEvent.ACTION_DOWN -> Outcome.None.also { classifier.onActionDown(x, y) }
            MotionEvent.ACTION_MOVE -> if (classifier.onActionMove(x, y)) Outcome.DismissPage else Outcome.None
            MotionEvent.ACTION_UP -> onUp(x, y, drawnBubbles, openCard)
            else -> Outcome.None
        }

    private fun onUp(x: Float, y: Float, drawnBubbles: List<OverlayBounds>, openCard: OverlayBounds?): Outcome {
        if (classifier.onActionUp(x, y) == TouchGestureClassifier.Gesture.DRAG) return Outcome.DismissPage
        if (openCard != null) return if (openCard.contains(x, y)) Outcome.None else Outcome.CloseCard
        val hit = drawnBubbles.indexOfLast { it.contains(x, y) }
        return if (hit >= 0) Outcome.OpenCard(hit) else Outcome.DismissPage
    }
}

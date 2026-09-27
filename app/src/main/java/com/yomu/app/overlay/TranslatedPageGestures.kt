package com.yomu.app.overlay

class TranslatedPageGestures(touchSlop: Float) {

    sealed interface Outcome {
        data object None : Outcome
        data object DismissPage : Outcome
        data object CloseCard : Outcome
        data class OpenCard(val bubbleIndex: Int) : Outcome
    }

    private val classifier = TouchGestureClassifier(touchSlop)

    fun onDown(x: Float, y: Float) = classifier.onActionDown(x, y)

    fun onMove(x: Float, y: Float): Outcome =
        if (classifier.onActionMove(x, y)) Outcome.DismissPage else Outcome.None

    /** [drawnBubbles] in draw order, so a later bubble covers an earlier one. */
    fun onUp(x: Float, y: Float, drawnBubbles: List<OverlayBounds>, cardOpen: Boolean): Outcome {
        if (classifier.onActionUp(x, y) == TouchGestureClassifier.Gesture.DRAG) return Outcome.DismissPage
        if (cardOpen) return Outcome.CloseCard
        val hit = drawnBubbles.indexOfLast { x in it.left..it.right && y in it.top..it.bottom }
        return if (hit >= 0) Outcome.OpenCard(hit) else Outcome.DismissPage
    }
}

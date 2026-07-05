package com.yomu.app.overlay

import kotlin.math.hypot

class TouchGestureClassifier(private val touchSlop: Float) {

    enum class Gesture {
        TAP,
        DRAG
    }

    private var downX: Float = 0f
    private var downY: Float = 0f
    private var dragging: Boolean = false

    fun onActionDown(rawX: Float, rawY: Float) {
        downX = rawX
        downY = rawY
        dragging = false
    }

    fun onActionMove(rawX: Float, rawY: Float): Boolean {
        if (!dragging) {
            val distance = hypot(rawX - downX, rawY - downY)
            dragging = distance > touchSlop
        }
        return dragging
    }

    fun onActionUp(rawX: Float, rawY: Float): Gesture {
        onActionMove(rawX, rawY)
        return if (dragging) Gesture.DRAG else Gesture.TAP
    }
}

package com.yomu.app.overlay

import kotlin.math.hypot

class TouchGestureClassifier(
    private val touchSlop: Float,
    private val longPressThresholdMs: Long = DEFAULT_LONG_PRESS_THRESHOLD_MS
) {

    enum class Gesture {
        TAP,
        DRAG,
        LONG_PRESS
    }

    private var downX: Float = 0f
    private var downY: Float = 0f
    private var downTime: Long = 0
    private var dragging: Boolean = false

    fun onActionDown(rawX: Float, rawY: Float) {
        downX = rawX
        downY = rawY
        downTime = System.currentTimeMillis()
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
        if (dragging) return Gesture.DRAG
        val duration = System.currentTimeMillis() - downTime
        return if (duration >= longPressThresholdMs) Gesture.LONG_PRESS else Gesture.TAP
    }

    companion object {
        private const val DEFAULT_LONG_PRESS_THRESHOLD_MS = 500L
    }
}

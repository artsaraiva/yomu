package com.yomu.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchGestureClassifierLongPressTest {

    @Test
    fun `quick tap returns tap`() {
        val classifier = TouchGestureClassifier(touchSlop = 12f)

        classifier.onActionDown(100f, 100f)

        assertEquals(TouchGestureClassifier.Gesture.TAP, classifier.onActionUp(101f, 101f))
    }

    @Test
    fun `long press without movement returns long press`() {
        val classifier = TouchGestureClassifier(touchSlop = 12f, longPressThresholdMs = 100L)

        classifier.onActionDown(100f, 100f)
        Thread.sleep(150)

        assertEquals(TouchGestureClassifier.Gesture.LONG_PRESS, classifier.onActionUp(100f, 100f))
    }

    @Test
    fun `long press with movement returns drag`() {
        val classifier = TouchGestureClassifier(touchSlop = 12f, longPressThresholdMs = 100L)

        classifier.onActionDown(100f, 100f)
        classifier.onActionMove(120f, 120f)
        Thread.sleep(150)

        assertEquals(TouchGestureClassifier.Gesture.DRAG, classifier.onActionUp(120f, 120f))
    }
}

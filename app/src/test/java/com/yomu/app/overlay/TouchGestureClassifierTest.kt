package com.yomu.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchGestureClassifierTest {

    @Test
    fun `up without drag is tap`() {
        val classifier = TouchGestureClassifier(touchSlop = 12f)

        classifier.onActionDown(100f, 100f)
        classifier.onActionMove(105f, 106f)

        assertEquals(TouchGestureClassifier.Gesture.TAP, classifier.onActionUp(106f, 107f))
    }

    @Test
    fun `move past slop becomes drag`() {
        val classifier = TouchGestureClassifier(touchSlop = 12f)

        classifier.onActionDown(100f, 100f)
        val isDragging = classifier.onActionMove(120f, 121f)

        assertEquals(true, isDragging)
        assertEquals(TouchGestureClassifier.Gesture.DRAG, classifier.onActionUp(120f, 121f))
    }

    @Test
    fun `resets between gestures`() {
        val classifier = TouchGestureClassifier(touchSlop = 10f)

        classifier.onActionDown(0f, 0f)
        classifier.onActionMove(20f, 0f)
        assertEquals(TouchGestureClassifier.Gesture.DRAG, classifier.onActionUp(20f, 0f))

        classifier.onActionDown(0f, 0f)
        assertEquals(TouchGestureClassifier.Gesture.TAP, classifier.onActionUp(1f, 1f))
    }
}

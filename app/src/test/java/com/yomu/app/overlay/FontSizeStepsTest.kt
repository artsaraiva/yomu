package com.yomu.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class FontSizeStepsTest {
    @Test
    fun aStepMovesTenPercent() {
        assertEquals(1.1f, FontSizeSteps.larger(1.0f), 1e-6f)
        assertEquals(0.9f, FontSizeSteps.smaller(1.0f), 1e-6f)
    }

    /** The Typesetting slider stores values between the steps; a step lands on the next ten, not ten further. */
    @Test
    fun aScaleBetweenStepsMovesToTheNextTen() {
        assertEquals(1.4f, FontSizeSteps.larger(1.37f), 1e-6f)
        assertEquals(1.3f, FontSizeSteps.smaller(1.37f), 1e-6f)
    }

    @Test
    fun stepsStopAtTheEndsOfTheRange() {
        assertEquals(2.0f, FontSizeSteps.larger(2.0f), 1e-6f)
        assertEquals(0.5f, FontSizeSteps.smaller(0.5f), 1e-6f)
    }

    @Test
    fun theLabelIsAWholePercent() {
        assertEquals("130%", FontSizeSteps.label(1.3f))
    }
}

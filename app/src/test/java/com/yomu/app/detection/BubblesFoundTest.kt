package com.yomu.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubblesFoundTest {
    @Test
    fun defaultIsTheStoresDefaultThreshold() {
        assertEquals(DetectionThresholdStore.DEFAULT, BubblesFound.DEFAULT.threshold)
    }

    /** A lower threshold keeps fainter boxes, so "More" sits below the default and "Fewer" above it. */
    @Test
    fun moreLowersTheThresholdAndFewerRaisesIt() {
        assertTrue(BubblesFound.MORE.threshold < BubblesFound.DEFAULT.threshold)
        assertTrue(BubblesFound.FEWER.threshold > BubblesFound.DEFAULT.threshold)
    }

    @Test
    fun everyChoiceIsAThresholdTheStoreAccepts() {
        BubblesFound.entries.forEach { assertTrue(it.label, DetectionThresholdStore.accepts(it.threshold)) }
    }

    @Test
    fun aStoredThresholdMapsBackToItsChoice() {
        BubblesFound.entries.forEach { assertEquals(it, BubblesFound.of(it.threshold)) }
    }

    @Test
    fun aThresholdSetOnTheSliderThatMatchesNoChoiceIsCustom() {
        assertNull(BubblesFound.of(0.30f))
    }
}

package com.yomu.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPanelFrameTest {
    private val margin = 8
    private val sideWidth = 360

    @Test
    fun inPortraitThePanelSpansTheWidthAndSitsOnTheBottomEdge() {
        val frame = readingPanelFrame(1080, 2200, buttonX = 0, margin, sideWidth) { 700 }

        assertEquals(PanelFrame(x = 8, y = 2200 - 700 - 8, width = 1064, height = 700), frame)
    }

    /** The page whose text size the reader is judging stays visible above the panel. */
    @Test
    fun inPortraitATallPanelStopsAtHalfTheScreenAndScrolls() {
        val frame = readingPanelFrame(1080, 2200, buttonX = 0, margin, sideWidth) { 1900 }

        assertTrue(frame.y >= 1100)
        assertEquals(2200 - 8, frame.y + frame.height)
    }

    @Test
    fun inLandscapeThePanelDocksToTheSideTheButtonIsOn() {
        val left = readingPanelFrame(2200, 1080, buttonX = 100, margin, sideWidth) { 700 }
        val right = readingPanelFrame(2200, 1080, buttonX = 1900, margin, sideWidth) { 700 }

        assertEquals(PanelFrame(x = 8, y = 1080 - 700 - 8, width = 360, height = 700), left)
        assertEquals(2200 - 360 - 8, right.x)
    }

    @Test
    fun inLandscapeATallPanelKeepsItsMarginsAndScrolls() {
        val frame = readingPanelFrame(2200, 1080, buttonX = 100, margin, sideWidth) { 1900 }

        assertEquals(8, frame.y)
        assertEquals(1080 - 16, frame.height)
    }

    @Test
    fun theContentIsMeasuredAtThePanelsWidth() {
        var measuredAt = 0
        readingPanelFrame(1080, 2200, buttonX = 0, margin, sideWidth) { measuredAt = it; 700 }

        assertEquals(1064, measuredAt)
    }
}

package com.yomu.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone is 1080 × 2400, with a 63 px status bar and, under 3-button navigation, a 126 px navigation bar. */
class OverlayCoordinateMapperTest {
    /** The overlay window fits the status bar, so it starts 63 px down while the capture starts at the top. */
    @Test
    fun `a portrait page lands where it was captured, below the status bar`() {
        val params = OverlayCoordinateMapper.params(
            captureWidth = 1080,
            captureHeight = 2400,
            displayWidth = 1080,
            displayHeight = 2400,
            overlayScreenX = 0,
            overlayScreenY = 63
        )

        val mapped = OverlayCoordinateMapper.map(floatArrayOf(100f, 600f, 300f, 900f), params)

        assertEquals(OverlayBounds(100f, 537f, 300f, 837f), mapped)
    }

    /** After a turn the capture is landscape like the display; this reader hides the status bar. */
    @Test
    fun `a landscape page lands where it was captured`() {
        val params = OverlayCoordinateMapper.params(
            captureWidth = 2400,
            captureHeight = 1080,
            displayWidth = 2400,
            displayHeight = 1080,
            overlayScreenX = 0,
            overlayScreenY = 0
        )

        val mapped = OverlayCoordinateMapper.map(floatArrayOf(1800f, 200f, 2100f, 500f), params)

        assertEquals(OverlayBounds(1800f, 200f, 2100f, 500f), mapped)
    }

    /**
     * In landscape, 3-button navigation puts its bar beside the page, here on the left. The overlay window fits it, so
     * the window is 126 px narrower and starts 126 px in; the page under it is still the display's full width.
     */
    @Test
    fun `a side navigation bar shifts the window without squeezing the page`() {
        val params = OverlayCoordinateMapper.params(
            captureWidth = 2400,
            captureHeight = 1080,
            displayWidth = 2400,
            displayHeight = 1080,
            overlayScreenX = 126,
            overlayScreenY = 0
        )

        val mapped = OverlayCoordinateMapper.map(floatArrayOf(1800f, 200f, 2100f, 500f), params)

        assertEquals(OverlayBounds(1674f, 200f, 1974f, 500f), mapped)
    }

    /** The offset is in screen pixels, so it comes off after the box is scaled from capture pixels to the display. */
    @Test
    fun `a capture smaller than the display is stretched over it`() {
        val params = OverlayCoordinateMapper.params(
            captureWidth = 1200,
            captureHeight = 540,
            displayWidth = 2400,
            displayHeight = 1080,
            overlayScreenX = 0,
            overlayScreenY = 63
        )

        val mapped = OverlayCoordinateMapper.map(floatArrayOf(100f, 100f, 200f, 150f), params)

        assertEquals(OverlayBounds(200f, 137f, 400f, 237f), mapped)
    }

    @Test
    fun `a box grown past the bottom slides back on screen`() {
        val clamped = OverlayCoordinateMapper.clampToCanvas(
            OverlayBounds(10f, 900f, 110f, 1100f),
            1000f,
            1000f
        )

        assertEquals(800f, clamped.top, 0.01f)
        assertEquals(1000f, clamped.bottom, 0.01f)
        assertEquals(10f, clamped.left, 0.01f)
    }

    @Test
    fun `a box grown past the top slides back on screen`() {
        val clamped = OverlayCoordinateMapper.clampToCanvas(
            OverlayBounds(10f, -60f, 110f, 140f),
            1000f,
            1000f
        )

        assertEquals(0f, clamped.top, 0.01f)
        assertEquals(200f, clamped.bottom, 0.01f)
    }

    @Test
    fun `a box taller than the canvas is pinned to the top`() {
        val clamped = OverlayCoordinateMapper.clampToCanvas(
            OverlayBounds(0f, -50f, 100f, 1200f),
            1000f,
            1000f
        )

        assertEquals(0f, clamped.top, 0.01f)
    }

    @Test
    fun `a box widened past the right edge slides back on screen`() {
        val clamped = OverlayCoordinateMapper.clampToCanvas(
            OverlayBounds(950f, 10f, 1100f, 110f),
            1000f,
            1000f
        )

        assertEquals(850f, clamped.left, 0.01f)
        assertEquals(1000f, clamped.right, 0.01f)
        assertEquals(10f, clamped.top, 0.01f)
    }
}

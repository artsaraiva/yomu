package com.yomu.app.overlay

import android.graphics.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Where the progress capsule sits beside the floating button, wherever the button was dragged (#552). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProgressCapsulePlacementTest {
    private val portrait = Point(1080, 2000)
    private val landscape = Point(2000, 1080)
    private val button = 150
    private val margin = 20

    /** A capsule that wants 600 x 150 and gives way to the room it is offered. */
    private fun place(x: Int, y: Int, screen: Point = portrait, wanted: Point = Point(600, 150)) =
        capsulePlacement(Point(x, y), button, screen, margin) { room -> Point(minOf(room, wanted.x), wanted.y) }

    @Test
    fun `a button on the right grows its capsule to the left and stays its end`() {
        val placement = place(x = 910, y = 800)

        assertTrue(placement.growsLeft)
        assertEquals(460, placement.x)
        assertEquals(600, placement.width)
        assertEquals(800, placement.y)
        assertEquals(150, placement.height)
    }

    @Test
    fun `a button on the left grows its capsule to the right from its own edge`() {
        val placement = place(x = 20, y = 800)

        assertFalse(placement.growsLeft)
        assertEquals(20, placement.x)
        assertEquals(600, placement.width)
    }

    @Test
    fun `a capsule wider than the room is cut to the room on the roomier side`() {
        val placement = place(x = 400, y = 800, wanted = Point(5000, 150))

        assertFalse(placement.growsLeft)
        assertEquals(400, placement.x)
        assertEquals(660, placement.width)
    }

    @Test
    fun `a capsule taller than the button is centred on it`() {
        val placement = place(x = 20, y = 800, wanted = Point(600, 250))

        assertEquals(750, placement.y)
        assertEquals(250, placement.height)
    }

    @Test
    fun `the capsule stays on screen with the button in any corner, upright or sideways`() {
        for (screen in listOf(portrait, landscape)) {
            for (x in listOf(margin, screen.x - button - margin)) {
                for (y in listOf(margin, screen.y - button - margin)) {
                    val placement = place(x, y, screen, wanted = Point(5000, 250))
                    val at = "button at $x,$y on ${screen.x}x${screen.y}"

                    assertTrue(at, placement.x >= margin && placement.x + placement.width <= screen.x - margin)
                    assertTrue(at, placement.y >= margin && placement.y + placement.height <= screen.y - margin)
                    assertTrue(at, placement.x <= x && placement.x + placement.width >= x + button)
                }
            }
        }
    }
}

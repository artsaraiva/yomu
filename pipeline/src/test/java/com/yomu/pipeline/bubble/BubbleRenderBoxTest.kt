package com.yomu.pipeline.bubble

import android.graphics.Bitmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BubbleRenderBoxTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val grey = 0xFFDCDCDC.toInt()

    // An elliptical bubble outline centred at (cx, cy), radii 80x120, with a stripe of vertical
    // glyphs down its middle, on a [background] page.
    private fun page(
        width: Int = 200,
        height: Int = 300,
        cx: Int = 100,
        cy: Int = 150,
        outline: Boolean = true,
        gapAt: Int? = null,
        background: Int = white,
        glyphAt: (x: Int, y: Int) -> Boolean = { x, y -> x in cx - 5..cx + 5 && y in cy - 40..cy + 40 && y % 10 < 5 }
    ): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val nx = (x - cx) / 80.0
        val ny = (y - cy) / 120.0
        val r = nx * nx + ny * ny
        val onOutline = outline && r in 0.95..1.05 && (gapAt == null || kotlin.math.abs(y - gapAt) > 3)
        val glyph = glyphAt(x, y)
        when {
            onOutline || glyph -> black
            r < 1 -> white
            else -> background
        }
    }

    private val textBox = floatArrayOf(92f, 105f, 108f, 195f)

    @Test
    fun `enclosed bubble widens the render box past the thin glyph box`() {
        val box = findBubbleInterior(page(), 200, 300, textBox)!!.renderBox
        assertTrue("wider than glyphs: ${box.toList()}", box[2] - box[0] > 90f)
        assertTrue("stays inside the bubble: ${box.toList()}", box[0] >= 20f && box[2] <= 180f)
    }

    @Test
    fun `no outline means no interior`() {
        assertNull(findBubbleInterior(page(outline = false), 200, 300, textBox))
    }

    @Test
    fun `a gap wider than the closing radius leaks and falls back`() {
        assertNull(findBubbleInterior(page(gapAt = 150), 200, 300, textBox))
    }

    @Test
    fun `render box never shrinks below the glyph box`() {
        val big = floatArrayOf(40f, 60f, 160f, 240f)
        val box = findBubbleInterior(page(), 200, 300, big)!!.renderBox
        assertTrue(box[0] <= 40f && box[1] <= 60f && box[2] >= 160f && box[3] >= 240f)
    }

    @Test
    fun `glyphs whose box pokes past the outline still pick the bubble they mostly sit in`() {
        // The bubble's top edge is at y=30; this box starts above it, out on the page.
        val poking = floatArrayOf(92f, 25f, 108f, 195f)
        val box = findBubbleInterior(page(), 200, 300, poking)!!.renderBox
        assertTrue("wider than glyphs: ${box.toList()}", box[2] - box[0] > 90f)
    }

    @Test
    fun `text on a light grey panel does not fill across the art`() {
        // Enclosed grey region, no white bubble: grey must not count as fillable.
        val panel = IntArray(200 * 300) { i ->
            val x = i % 200
            val y = i / 200
            if (x < 3 || y < 3 || x > 196 || y > 296) black else grey
        }
        assertNull(findBubbleInterior(panel, 200, 300, textBox))
    }

    private fun bitmap(width: Int, height: Int, pixels: IntArray): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }

    @Test
    fun `page coordinates round trip through the search window`() {
        // Bubble placed away from the page origin so a window-offset bug shows.
        val pixels = page(width = 600, height = 800, cx = 400, cy = 500)
        val glyphs = floatArrayOf(392f, 455f, 408f, 545f)
        val box = bubbleRenderAreas(bitmap(600, 800, pixels), mapOf(1 to glyphs)).getValue(1).box

        assertTrue("wider than glyphs: ${box.toList()}", box[2] - box[0] > 90f)
        assertTrue("inside the bubble: ${box.toList()}", box[0] >= 320f && box[2] <= 480f && box[1] >= 380f && box[3] <= 620f)
    }

    @Test
    fun `a grown box reaching another bubble's glyphs keeps its glyph box`() {
        val pixels = page(width = 600, height = 800, cx = 400, cy = 500)
        val glyphs = floatArrayOf(392f, 455f, 408f, 545f)
        val neighbour = floatArrayOf(430f, 400f, 440f, 420f)
        val areas = bubbleRenderAreas(bitmap(600, 800, pixels), mapOf(1 to glyphs, 2 to neighbour))

        assertArrayEquals(glyphs, areas.getValue(1).box, 0f)
        assertNull("no shape that would cover the neighbour", areas.getValue(1).shape)
    }

    @Test
    fun `without a balloon box a glyph box outside the render box leaves the shape alone`() {
        // Free text in a panel: the panel's interior ellipse holds a neighbour's glyph box, which
        // the render box inside it misses. Main draws the lines clear of it, so the shape stays.
        val pixels = page(width = 600, height = 800, cx = 400, cy = 500)
        val glyphs = floatArrayOf(392f, 455f, 408f, 545f)
        val neighbour = floatArrayOf(462f, 490f, 470f, 510f)

        val area = bubbleRenderAreas(bitmap(600, 800, pixels), mapOf(1 to glyphs, 2 to neighbour)).getValue(1)

        assertTrue("render box misses the neighbour: ${area.box.toList()}", area.box[2] < 462f)
        assertTrue("shape kept", area.shape != null)
    }

    @Test
    fun `a round bubble's shape is the ellipse of its interior`() {
        val shape = findBubbleInterior(page(), 200, 300, textBox)!!.shape

        assertTrue("ellipse", shape.ellipse)
        // The outline ring is 0.95..1.05 of radii 80x120, and the close radius eats 2 px more.
        assertTrue("spans the interior: ${shape.bounds.toList()}", shape.bounds[0] in 18f..30f && shape.bounds[2] in 170f..182f)
        assertTrue("spans the interior: ${shape.bounds.toList()}", shape.bounds[1] in 28f..42f && shape.bounds[3] in 258f..272f)
    }

    @Test
    fun `a caption box's shape is its whole rectangle`() {
        // A rectangular outline from (40,60) to (160,240), glyphs down the middle.
        val caption = IntArray(200 * 300) { i ->
            val x = i % 200
            val y = i / 200
            val onOutline = (x in 40..42 || x in 158..160) && y in 60..240 || (y in 60..62 || y in 238..240) && x in 40..160
            val glyph = x in 95..105 && y in 110..190 && y % 10 < 5
            if (onOutline || glyph) black else white
        }

        val shape = findBubbleInterior(caption, 200, 300, textBox)!!.shape

        assertTrue("rectangle", !shape.ellipse)
    }

    @Test
    fun `a bubble with no interior has no shape and keeps its glyph box`() {
        val pixels = page(width = 600, height = 800, cx = 400, cy = 500, outline = false)
        val glyphs = floatArrayOf(392f, 455f, 408f, 545f)

        val area = bubbleRenderAreas(bitmap(600, 800, pixels), mapOf(1 to glyphs)).getValue(1)

        assertNull(area.shape)
        assertArrayEquals(glyphs, area.box, 0f)
    }

    @Test
    fun `a shape comes back in page coordinates`() {
        val pixels = page(width = 600, height = 800, cx = 400, cy = 500)
        val glyphs = floatArrayOf(392f, 455f, 408f, 545f)

        val area = bubbleRenderAreas(bitmap(600, 800, pixels), mapOf(1 to glyphs)).getValue(1)

        val bounds = area.shape!!.bounds
        assertTrue("around the bubble: ${bounds.toList()}", bounds[0] in 318f..330f && bounds[2] in 470f..482f && bounds[1] in 378f..392f && bounds[3] in 608f..622f)
        assertArrayEquals("glyph box rides along", glyphs, area.glyphBox, 0f)
    }

    // A column of three 口 at (400, 500), 20 px squares with 2 px strokes: each counter is an
    // enclosed region, far smaller than the column's glyph box.
    private fun counters(x: Int, y: Int): Boolean {
        if (x !in 390..410) return false
        return listOf(460, 490, 520).any { top ->
            y in top..top + 20 && (x <= 391 || x >= 409 || y <= top + 1 || y >= top + 19)
        }
    }
    private val counterGlyphs = floatArrayOf(390f, 460f, 411f, 541f)
    // The detector's balloon box hugs the outline's outer edge: radii 80x120 at up to 1.05.
    private val balloonBox = floatArrayOf(317f, 376f, 483f, 624f)

    @Test
    fun `an open balloon whose only enclosed region is a glyph counter falls back to the balloon ellipse`() {
        val pixels = page(width = 600, height = 800, cx = 400, cy = 500, gapAt = 500, glyphAt = ::counters)

        val area = bubbleRenderAreas(bitmap(600, 800, pixels), mapOf(1 to counterGlyphs), mapOf(1 to balloonBox)).getValue(1)

        val shape = area.shape!!
        assertTrue("ellipse", shape.ellipse)
        // The balloon box inset 6% on each side, inside its outline stroke.
        assertArrayEquals(floatArrayOf(326.96f, 390.88f, 473.04f, 609.12f), shape.bounds, 0.01f)
        assertTrue("render box is wider than the glyphs: ${area.box.toList()}", area.box[2] - area.box[0] > 90f)
        assertTrue("render box stays in the ellipse: ${area.box.toList()}", area.box[0] >= 326f && area.box[2] <= 474f)
    }

    @Test
    fun `a closed balloon keeps the interior the fill finds inside its balloon box`() {
        val pixels = page(width = 600, height = 800, cx = 400, cy = 500)
        val glyphs = floatArrayOf(392f, 455f, 408f, 545f)

        val bounds = bubbleRenderAreas(bitmap(600, 800, pixels), mapOf(1 to glyphs), mapOf(1 to balloonBox))
            .getValue(1).shape!!.bounds

        assertTrue("around the interior: ${bounds.toList()}", bounds[0] in 318f..330f && bounds[2] in 470f..482f && bounds[1] in 378f..392f && bounds[3] in 608f..622f)
    }

    @Test
    fun `a balloon ellipse meeting another bubble's glyphs keeps the glyph box`() {
        val pixels = page(width = 600, height = 800, cx = 400, cy = 500, gapAt = 500, glyphAt = ::counters)
        val neighbour = floatArrayOf(440f, 480f, 450f, 520f)

        val area = bubbleRenderAreas(
            bitmap(600, 800, pixels),
            mapOf(1 to counterGlyphs, 2 to neighbour),
            mapOf(1 to balloonBox)
        ).getValue(1)

        assertNull(area.shape)
        assertArrayEquals(counterGlyphs, area.box, 0f)
    }
}

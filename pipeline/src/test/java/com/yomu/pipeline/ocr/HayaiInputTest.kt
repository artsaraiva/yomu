package com.yomu.pipeline.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HayaiInputTest {

    // A 2 × 2 table of 1-value positions, so a grid's positions are easy to predict.
    private val table = PositionTable(NpyFloats(listOf(4, 1), floatArrayOf(10f, 20f, 30f, 40f)))

    @Test
    fun `a crop already on the patch grid is not resized and its patches are padded to a multiple of eight`() {
        val pixels = IntArray(16 * 32) { 0xFFFFFFFF.toInt() }

        val input = HayaiInput.of(pixels, width = 32, height = 16, positions = table, grid = PatchGrid(rows = 1, columns = 2))

        assertEquals(8, input.slots)
        assertEquals(8 * 768, input.pixelValues.size)
        assertEquals(1f, input.pixelValues[0], 0f)
        assertEquals(1f, input.pixelValues[2 * 768 - 1], 0f)
        assertEquals(0f, input.pixelValues[2 * 768], 0f)
    }

    @Test
    fun `each patch holds its sixteen rows of RGB pixels, scaled to minus one to one, patches row by row`() {
        // Left patch black, right patch pure red.
        val pixels = IntArray(16 * 32) { if (it % 32 < 16) 0xFF000000.toInt() else 0xFFFF0000.toInt() }

        val input = HayaiInput.of(pixels, width = 32, height = 16, positions = table, grid = PatchGrid(rows = 1, columns = 2))

        assertArrayEquals(floatArrayOf(-1f, -1f, -1f), input.pixelValues.copyOfRange(0, 3), 0f)
        assertArrayEquals(floatArrayOf(1f, -1f, -1f), input.pixelValues.copyOfRange(768, 771), 0f)
        assertArrayEquals(floatArrayOf(1f, -1f, -1f), input.pixelValues.copyOfRange(2 * 768 - 3, 2 * 768), 0f)
    }

    @Test
    fun `positions follow the patches and the padding gets none`() {
        val pixels = IntArray(32 * 32) { 0xFF808080.toInt() }

        val input = HayaiInput.of(pixels, width = 32, height = 32, positions = table, grid = PatchGrid(rows = 2, columns = 2))

        assertArrayEquals(floatArrayOf(10f, 20f, 30f, 40f, 0f, 0f, 0f, 0f), input.positions, 1e-4f)
    }

    @Test
    fun `a crop off the grid is resized to it`() {
        val pixels = IntArray(10 * 20) { 0xFF000000.toInt() }

        val input = HayaiInput.of(pixels, width = 20, height = 10, positions = table, grid = PatchGrid(rows = 1, columns = 2))

        assertEquals(-1f, input.pixelValues[2 * 768 - 1], 0f)
    }
}

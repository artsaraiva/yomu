package com.yomu.pipeline.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** Expected values come from PIL's Image.resize(BILINEAR) and the #470 replica's position-table resample. */
class TriangleResampleTest {

    @Test
    fun `upsampling interpolates between neighbouring samples`() {
        val weights = triangleWeights(outputs = 3, inputs = 2)

        assertArrayEquals(floatArrayOf(1f, 0f), weights[0], 1e-4f)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), weights[1], 1e-4f)
        assertArrayEquals(floatArrayOf(0f, 1f), weights[2], 1e-4f)
    }

    @Test
    fun `resizing gray pixels matches PIL bilinear both down and up`() {
        val gray = intArrayOf(0, 100, 200, 250, 50, 60, 70, 80)
        val pixels = IntArray(gray.size) { rgb(gray[it]) }

        assertArrayEquals(intArrayOf(64, 140), resizeRgb(pixels, width = 4, height = 2, toWidth = 2, toHeight = 1).map(::red).toIntArray())
        assertArrayEquals(
            intArrayOf(0, 50, 117, 183, 225, 250, 25, 53, 90, 126, 150, 165, 50, 55, 62, 68, 75, 80),
            resizeRgb(pixels, width = 4, height = 2, toWidth = 6, toHeight = 3).map(::red).toIntArray()
        )
    }

    @Test
    fun `resizing keeps each colour channel apart`() {
        val pixel = 0xFF102030.toInt()

        assertArrayEquals(intArrayOf(pixel), resizeRgb(intArrayOf(pixel, pixel), width = 2, height = 1, toWidth = 1, toHeight = 1))
    }

    @Test
    fun `the position table is resampled to the patch grid`() {
        val table = FloatArray(4 * 4 * 2) { it.toFloat() }

        assertArrayEquals(
            floatArrayOf(6.3143f, 7.3143f, 8.7143f, 9.7143f, 11.1143f, 12.1143f, 18.8857f, 19.8857f, 21.2857f, 22.2857f, 23.6857f, 24.6857f),
            resamplePositions(table, side = 4, dimension = 2, rows = 2, columns = 3),
            1e-3f
        )
    }

    private fun rgb(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun red(pixel: Int) = (pixel shr 16) and 0xFF
}

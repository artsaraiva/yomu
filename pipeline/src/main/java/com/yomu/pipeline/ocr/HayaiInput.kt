package com.yomu.pipeline.ocr

import kotlin.math.sqrt

/**
 * One crop as Hayai's prefill takes it: [slots] rows of patch pixels and of positions, the [grid]'s patches first in
 * row order and zero padding after.
 */
internal class HayaiInput(val grid: PatchGrid, val slots: Int, val pixelValues: FloatArray, val positions: FloatArray) {

    companion object {
        private const val PATCH_VALUES = PatchGrid.PATCH * PatchGrid.PATCH * 3
        // The prefill graph takes its patch rows padded to a multiple of this.
        private const val SLOT_MULTIPLE = 8

        /**
         * [pixels], ARGB of [width] × [height], resized to [grid] (PIL bilinear) and cut into patches of 16 rows of
         * RGB scaled to [-1, 1], with [positions], Hayai's square table, resampled to the same grid.
         */
        fun of(pixels: IntArray, width: Int, height: Int, positions: NpyFloats, grid: PatchGrid): HayaiInput {
            val resized = if (width == grid.width && height == grid.height) pixels
            else resizeRgb(pixels, width, height, grid.width, grid.height)
            val slots = maxOf(SLOT_MULTIPLE, (grid.patches + SLOT_MULTIPLE - 1) / SLOT_MULTIPLE * SLOT_MULTIPLE)
            val values = FloatArray(slots * PATCH_VALUES)
            var i = 0
            for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
                for (y in 0 until PatchGrid.PATCH) for (x in 0 until PatchGrid.PATCH) {
                    val pixel = resized[(row * PatchGrid.PATCH + y) * grid.width + column * PatchGrid.PATCH + x]
                    values[i++] = scale((pixel shr 16) and 0xFF)
                    values[i++] = scale((pixel shr 8) and 0xFF)
                    values[i++] = scale(pixel and 0xFF)
                }
            }
            val dimension = positions.shape[1]
            val side = sqrt(positions.shape[0].toDouble()).toInt()
            val table = FloatArray(slots * dimension)
            resamplePositions(positions.values, side, dimension, grid.rows, grid.columns).copyInto(table)
            return HayaiInput(grid, slots, values, table)
        }

        private fun scale(byte: Int): Float = byte * (2f / 255f) - 1f
    }
}

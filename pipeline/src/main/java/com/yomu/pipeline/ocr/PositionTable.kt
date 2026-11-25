package com.yomu.pipeline.ocr

import kotlin.math.sqrt

/**
 * Hayai's square table of learned patch positions, resampled to each [PatchGrid] it is asked for. Pages repeat grid
 * shapes, so each shape is resampled once and kept.
 */
internal class PositionTable(private val table: NpyFloats) {
    val dimension: Int get() = table.shape[1]
    private val side = sqrt(table.shape[0].toDouble()).toInt()
    private val resampled = mutableMapOf<PatchGrid, FloatArray>()

    /** The table resampled to [grid], one row of [dimension] values per patch, row-major. */
    fun at(grid: PatchGrid): FloatArray = synchronized(resampled) {
        resampled.getOrPut(grid) { resamplePositions(table.values, side, dimension, grid.rows, grid.columns) }
    }
}

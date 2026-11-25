package com.yomu.pipeline.ocr

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Triangle-filter weights taking [inputs] samples to [outputs], one row per output, each summing to 1. Shrinking
 * widens the filter to the scale, so every input contributes: PIL's BILINEAR resize, which Hayai was scored with.
 */
internal fun triangleWeights(outputs: Int, inputs: Int): Array<FloatArray> {
    val scale = inputs.toDouble() / outputs
    val inverse = if (scale > 1.0) 1.0 / scale else 1.0
    return Array(outputs) { out ->
        val centre = scale * (out + 0.5)
        val row = DoubleArray(inputs) { j -> maxOf(0.0, 1.0 - abs(j + 0.5 - centre) * inverse) }
        val sum = row.sum().coerceAtLeast(1e-12)
        FloatArray(inputs) { (row[it] / sum).toFloat() }
    }
}

/**
 * [pixels], ARGB of [width] × [height], resized to [toWidth] × [toHeight] with [triangleWeights]: across, rounded to
 * bytes, then down, as PIL does. Alpha is dropped to opaque.
 */
internal fun resizeRgb(pixels: IntArray, width: Int, height: Int, toWidth: Int, toHeight: Int): IntArray {
    val across = triangleWeights(toWidth, width)
    val down = triangleWeights(toHeight, height)
    val channels = intArrayOf(16, 8, 0)
    val wide = Array(channels.size) { IntArray(toWidth * height) }
    for ((c, shift) in channels.withIndex()) {
        for (y in 0 until height) for (x in 0 until toWidth) {
            var sum = 0f
            for (j in 0 until width) sum += across[x][j] * ((pixels[y * width + j] shr shift) and 0xFF)
            wide[c][y * toWidth + x] = sum.roundToInt().coerceIn(0, 255)
        }
    }
    return IntArray(toWidth * toHeight) { index ->
        val x = index % toWidth
        val y = index / toWidth
        var argb = 0xFF shl 24
        for ((c, shift) in channels.withIndex()) {
            var sum = 0f
            for (j in 0 until height) sum += down[y][j] * wide[c][j * toWidth + x]
            argb = argb or (sum.roundToInt().coerceIn(0, 255) shl shift)
        }
        argb
    }
}

/**
 * Hayai's learned position table, [side] × [side] rows of [dimension] values, resampled to [rows] × [columns] patches
 * with [triangleWeights], row-major: one table serves every grid shape.
 */
internal fun resamplePositions(table: FloatArray, side: Int, dimension: Int, rows: Int, columns: Int): FloatArray {
    val down = triangleWeights(rows, side)
    val across = triangleWeights(columns, side)
    val out = FloatArray(rows * columns * dimension)
    for (i in 0 until rows) for (j in 0 until columns) {
        val target = (i * columns + j) * dimension
        for (h in 0 until side) {
            val wh = down[i][h]
            if (wh == 0f) continue
            for (w in 0 until side) {
                val weight = wh * across[j][w]
                if (weight == 0f) continue
                val source = (h * side + w) * dimension
                for (d in 0 until dimension) out[target + d] += weight * table[source + d]
            }
        }
    }
    return out
}

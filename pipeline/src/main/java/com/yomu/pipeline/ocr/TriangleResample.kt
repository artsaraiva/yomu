package com.yomu.pipeline.ocr

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** One output sample's weights over inputs `first until first + weights.size`; the rest weigh nothing. */
internal class TriangleTap(val first: Int, val weights: FloatArray)

/**
 * Triangle-filter taps taking [inputs] samples to [outputs], each tap's weights summing to 1. Shrinking widens the
 * filter to the scale, so every input contributes: PIL's BILINEAR resize, which Hayai was scored with.
 */
internal fun triangleTaps(outputs: Int, inputs: Int): Array<TriangleTap> {
    val scale = inputs.toDouble() / outputs
    val support = if (scale > 1.0) scale else 1.0
    return Array(outputs) { out ->
        val centre = scale * (out + 0.5)
        val first = maxOf(0, floor(centre - support - 0.5).toInt())
        val last = minOf(inputs - 1, ceil(centre + support - 0.5).toInt())
        val row = DoubleArray(last - first + 1) { maxOf(0.0, 1.0 - abs(first + it + 0.5 - centre) / support) }
        val sum = row.sum().coerceAtLeast(1e-12)
        TriangleTap(first, FloatArray(row.size) { (row[it] / sum).toFloat() })
    }
}

/** [triangleTaps] spread to full rows, one per output, for reading the weights whole. */
internal fun triangleWeights(outputs: Int, inputs: Int): Array<FloatArray> = triangleTaps(outputs, inputs).map { tap ->
    FloatArray(inputs).also { tap.weights.copyInto(it, tap.first) }
}.toTypedArray()

/**
 * [pixels], ARGB of [width] × [height], resized to [toWidth] × [toHeight] with [triangleTaps]: across, rounded to
 * bytes, then down, as PIL does. Alpha is dropped to opaque.
 */
internal fun resizeRgb(pixels: IntArray, width: Int, height: Int, toWidth: Int, toHeight: Int): IntArray {
    val across = triangleTaps(toWidth, width)
    val down = triangleTaps(toHeight, height)
    val channels = intArrayOf(16, 8, 0)
    val wide = Array(channels.size) { IntArray(toWidth * height) }
    for ((c, shift) in channels.withIndex()) {
        for (y in 0 until height) for (x in 0 until toWidth) {
            val tap = across[x]
            var sum = 0f
            for (k in tap.weights.indices) sum += tap.weights[k] * ((pixels[y * width + tap.first + k] shr shift) and 0xFF)
            wide[c][y * toWidth + x] = sum.roundToInt().coerceIn(0, 255)
        }
    }
    return IntArray(toWidth * toHeight) { index ->
        val x = index % toWidth
        val tap = down[index / toWidth]
        var argb = 0xFF shl 24
        for ((c, shift) in channels.withIndex()) {
            var sum = 0f
            for (k in tap.weights.indices) sum += tap.weights[k] * wide[c][(tap.first + k) * toWidth + x]
            argb = argb or (sum.roundToInt().coerceIn(0, 255) shl shift)
        }
        argb
    }
}

/**
 * Hayai's learned position table, [side] × [side] rows of [dimension] values, resampled to [rows] × [columns] patches
 * with [triangleTaps], down then across, row-major: one table serves every grid shape.
 */
internal fun resamplePositions(table: FloatArray, side: Int, dimension: Int, rows: Int, columns: Int): FloatArray {
    val tall = FloatArray(rows * side * dimension)
    for ((i, tap) in triangleTaps(rows, side).withIndex()) for (k in tap.weights.indices) {
        val weight = tap.weights[k]
        val source = (tap.first + k) * side * dimension
        val target = i * side * dimension
        for (v in 0 until side * dimension) tall[target + v] += weight * table[source + v]
    }
    val out = FloatArray(rows * columns * dimension)
    val across = triangleTaps(columns, side)
    for (i in 0 until rows) for ((j, tap) in across.withIndex()) for (k in tap.weights.indices) {
        val weight = tap.weights[k]
        val source = (i * side + tap.first + k) * dimension
        val target = (i * columns + j) * dimension
        for (d in 0 until dimension) out[target + d] += weight * tall[source + d]
    }
    return out
}

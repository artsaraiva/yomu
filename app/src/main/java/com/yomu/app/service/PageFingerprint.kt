package com.yomu.app.service

import android.graphics.Bitmap
import com.yomu.app.overlay.OverlayBounds
import kotlin.math.abs
import kotlin.math.ceil

/**
 * A coarse picture of a captured frame for [PageRecall]: the mean luminance of each cell of a
 * [GRID] × [GRID] grid. A cell touching an ignored region is left out, and two frames are compared
 * only on the cells both kept, so the status bar clock or a moved floating button never decides a match.
 */
class PageFingerprint private constructor(
    private val width: Int,
    private val height: Int,
    private val cells: IntArray
) {

    fun matches(other: PageFingerprint): Boolean =
        width == other.width && height == other.height && cells.indices.all { cell ->
            cells[cell] == IGNORED || other.cells[cell] == IGNORED || abs(cells[cell] - other.cells[cell]) <= TOLERANCE
        }

    companion object {
        private const val GRID = 32
        private const val TOLERANCE = 4
        private const val IGNORED = -1

        fun of(bitmap: Bitmap, ignored: List<OverlayBounds>): PageFingerprint {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return of(pixels, bitmap.width, bitmap.height, ignored)
        }

        /** [pixels] are ARGB, row by row, as `Bitmap.getPixels` fills them; [ignored] is in the same pixel space. */
        fun of(pixels: IntArray, width: Int, height: Int, ignored: List<OverlayBounds>): PageFingerprint {
            val sums = LongArray(GRID * GRID)
            val counts = IntArray(GRID * GRID)
            for (y in 0 until height) {
                val row = y * GRID / height * GRID
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val cell = row + x * GRID / width
                    val luminance = (77 * (pixel shr 16 and 0xFF) + 150 * (pixel shr 8 and 0xFF) + 29 * (pixel and 0xFF)) shr 8
                    sums[cell] += luminance.toLong()
                    counts[cell]++
                }
            }
            val cells = IntArray(GRID * GRID) { cell -> if (counts[cell] == 0) IGNORED else (sums[cell] / counts[cell]).toInt() }
            for (region in ignored) {
                for (row in cellsCovering(region.top, region.bottom, height)) {
                    for (column in cellsCovering(region.left, region.right, width)) cells[row * GRID + column] = IGNORED
                }
            }
            return PageFingerprint(width, height, cells)
        }

        private fun cellsCovering(start: Float, end: Float, size: Int): IntRange {
            val first = start.toInt().coerceAtLeast(0)
            val last = ceil(end).toInt().coerceAtMost(size) - 1
            return if (first > last) IntRange.EMPTY else first * GRID / size..last * GRID / size
        }
    }
}

package com.yomu.pipeline.context

import android.graphics.Bitmap
import android.graphics.Rect
import com.yomu.pipeline.bubble.luminance

// Gutters are paper white; art and screentone fall below it. Anything from 180 to 235 split the
// three fixture pages identically (#367).
private const val PAPER_LUMINANCE = 200
// Rows and columns that are mostly paper belong to the page. The reader's letterbox does not, nor
// does the floating button drawn over it, whose rows would otherwise stop every column gutter.
private const val PAGE_PAPER_FRACTION = 0.5f

/** The paper-white pixels of a captured screen: the page's margins, gutters and blank art. */
internal class PaperMask(screen: Bitmap) {
    private val width = screen.width
    private val height = screen.height
    private val paper = BooleanArray(width * height).also { mask ->
        val row = IntArray(width)
        for (y in 0 until height) {
            screen.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) mask[y * width + x] = luminance(row[x]) >= PAPER_LUMINANCE
        }
    }

    fun isPaperRow(y: Int, span: Rect): Boolean =
        paperCount(y * width + span.left, 1, span.width()) == span.width()

    fun isPaperColumn(x: Int, span: Rect): Boolean =
        paperCount(span.top * width + x, width, span.height()) == span.height()

    /** The page without the reader's letterbox; empty when no row is mostly paper. */
    fun pageBounds(): Rect {
        val rows = (0 until height).filter { y -> paperCount(y * width, 1, width) > width * PAGE_PAPER_FRACTION }
        if (rows.isEmpty()) return Rect()
        val top = rows.first()
        val bottom = rows.last() + 1
        val columns = (0 until width).filter { x ->
            paperCount(top * width + x, width, bottom - top) > (bottom - top) * PAGE_PAPER_FRACTION
        }
        if (columns.isEmpty()) return Rect()
        return Rect(columns.first(), top, columns.last() + 1, bottom)
    }

    private fun paperCount(first: Int, step: Int, length: Int): Int {
        var count = 0
        for (i in 0 until length) if (paper[first + i * step]) count++
        return count
    }
}

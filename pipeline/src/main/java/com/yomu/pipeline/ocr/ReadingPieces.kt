package com.yomu.pipeline.ocr

import android.graphics.Bitmap

private const val INK_LUMINANCE = 128
// Up to three columns manga-ocr reads whole as well as it ever did.
private const val MIN_COLUMNS = 4
private const val COLUMNS_PER_PIECE = 2
// A vertical column's ink is several times taller than wide; a horizontal line's characters are about square.
private const val MIN_COLUMN_TALLNESS = 2.5f
private const val MIN_LINE_ASPECT = 8f
// A piece is about this many line heights wide, cut at the widest gap within a quarter of that.
private const val PIECE_ASPECT = 5f
private const val PIECE_SLACK = 0.25f
// A tail shorter than half a piece stays on the last piece.
private const val MAX_LAST_PIECE = 1.5f

/**
 * The x ranges to read [crop] in, in reading order. They lie side by side across the whole crop, each cut
 * halfway between two pieces' ink. Fewer than two when the crop is read whole.
 */
internal fun readingPieces(crop: Bitmap): List<IntRange> {
    val ink = Ink(crop)
    val pieces = columnPairs(ink).ifEmpty { linePieces(ink) }
    return if (pieces.size < 2) emptyList() else sideBySide(pieces, crop.width)
}

/** Two columns at a time, right to left, when [ink] is a block of [MIN_COLUMNS] or more tall columns. */
private fun columnPairs(ink: Ink): List<IntRange> {
    val columns = bands(ink.columns())
    if (columns.size < MIN_COLUMNS) return emptyList()
    val tallness = columns.map { column -> ink.rowsIn(column).length().toFloat() / column.length() }.sorted()
    if (tallness[tallness.size / 2] < MIN_COLUMN_TALLNESS) return emptyList()
    return columns.reversed().chunked(COLUMNS_PER_PIECE).map { group -> group.last().first..group.first().last }
}

/** Stretches about [PIECE_ASPECT] line heights wide, left to right, when [ink] is one line many times wider than tall. */
private fun linePieces(ink: Ink): List<IntRange> {
    val line = bands(ink.rows()).singleOrNull() ?: return emptyList()
    val inked = ink.columns(line)
    val left = inked.indexOf(true)
    val right = inked.lastIndexOf(true)
    if (right - left + 1 < MIN_LINE_ASPECT * line.length()) return emptyList()

    val gaps = bands(inked.map { !it }).filter { it.first > left && it.last < right }
    val target = PIECE_ASPECT * line.length()
    val reach = target * (1 - PIECE_SLACK)..target * (1 + PIECE_SLACK)
    val cuts = mutableListOf(left)
    while (right + 1 - cuts.last() > target * MAX_LAST_PIECE) {
        val from = cuts.last()
        val gap = gaps.filter { (it.first + it.last) / 2f - from in reach }.maxByOrNull { it.length() } ?: break
        cuts += (gap.first + gap.last + 1) / 2
    }
    return (cuts + (right + 1)).zipWithNext { start, end -> start until end }
}

/** [pieces]' ink ranges widened to meet halfway between neighbours and reach both edges of a [width] crop. */
private fun sideBySide(pieces: List<IntRange>, width: Int): List<IntRange> {
    val byX = pieces.sortedBy { it.first }
    val cuts = byX.zipWithNext { left, right -> (left.last + right.first + 1) / 2 }
    val starts = listOf(0) + cuts
    val ends = cuts.map { it - 1 } + (width - 1)
    val widened = byX.indices.associate { byX[it] to starts[it]..ends[it] }
    return pieces.map(widened::getValue)
}

/** The runs of true in [flags]. */
private fun bands(flags: List<Boolean>): List<IntRange> {
    val bands = mutableListOf<IntRange>()
    var start = -1
    for ((index, flag) in flags.withIndex()) {
        if (flag && start < 0) start = index
        if (!flag && start >= 0) {
            bands += start until index
            start = -1
        }
    }
    if (start >= 0) bands += start until flags.size
    return bands
}

private fun IntRange.length(): Int = last - first + 1

/** A crop's ink: its dark pixels. */
private class Ink(crop: Bitmap) {
    private val width = crop.width
    private val height = crop.height
    private val dark: BooleanArray = run {
        val pixels = IntArray(width * height).also { crop.getPixels(it, 0, width, 0, 0, width, height) }
        BooleanArray(pixels.size) { luminance(pixels[it]) < INK_LUMINANCE }
    }

    /** Per column, whether any of its pixels in [rows] is ink. */
    fun columns(rows: IntRange = 0 until height): List<Boolean> = (0 until width).map { x -> rows.any { y -> at(x, y) } }

    /** Per row, whether any of its pixels is ink. */
    fun rows(): List<Boolean> = (0 until height).map { y -> (0 until width).any { x -> at(x, y) } }

    /** The rows from the first to the last that hold ink within [columns]. */
    fun rowsIn(columns: IntRange): IntRange {
        val inked = (0 until height).filter { y -> columns.any { x -> at(x, y) } }
        return inked.first()..inked.last()
    }

    private fun at(x: Int, y: Int): Boolean = dark[y * width + x]

    private fun luminance(pixel: Int): Int =
        (((pixel shr 16) and 0xFF) * 299 + ((pixel shr 8) and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
}

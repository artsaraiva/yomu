package com.yomu.pipeline.ocr

import android.graphics.Bitmap

private const val INK_LUMINANCE = 128
// manga-ocr reads a speech bubble of up to seven columns whole: split into pairs, 42 of OpenMantra's 171
// four-to-six-column bubbles fell under MIN_CONFIDENCE. Rendered vertical text read whole garbled from eight
// or nine columns and read cleanly in pairs at every width (#453).
private const val MIN_COLUMNS = 8
private const val COLUMNS_PER_PIECE = 2
// Narrower than a stroke: a speck or a sliver of balloon outline, not a column of text.
private const val MIN_COLUMN_WIDTH = 3
// A vertical column's ink is several times taller than wide; a horizontal line's characters are about square.
private const val MIN_COLUMN_TALLNESS = 2.5f
// Furigana stands beside its column at about half the width. Counted as a column, it pairs a column with
// its own reading and the next column with the wrong neighbour.
private const val MAX_FURIGANA_WIDTH = 0.6f
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
    val columns = withFuriganaMerged(bands(ink.columns()).filter { it.length() >= MIN_COLUMN_WIDTH })
    if (columns.size < MIN_COLUMNS) return emptyList()
    val tallness = columns.map { column -> ink.rowsIn(column).length().toFloat() / column.length() }.sorted()
    if (tallness[tallness.size / 2] < MIN_COLUMN_TALLNESS) return emptyList()
    // Lines of horizontal text stand their characters in columns too, but their widest gaps run between rows.
    val columnGaps = gaps(columns).sorted()
    val widestRowGap = gaps(bands(ink.rows())).maxOrNull() ?: 0
    if (columnGaps[columnGaps.size / 2] <= widestRowGap) return emptyList()
    return columns.reversed().chunked(COLUMNS_PER_PIECE).map { group -> group.last().first..group.first().last }
}

/**
 * [columns], left to right, with each column much thinner than its neighbours, furigana, merged into the
 * nearer neighbour: the left one on a tie, since a reading stands to the right of its column.
 */
private fun withFuriganaMerged(columns: List<IntRange>): List<IntRange> {
    val owners = columns.indices.map { i ->
        val neighbours = listOfNotNull(columns.getOrNull(i - 1), columns.getOrNull(i + 1))
        val furigana = neighbours.isNotEmpty() && neighbours.all { columns[i].length() < MAX_FURIGANA_WIDTH * it.length() }
        val leftGap = columns.getOrNull(i - 1)?.let { columns[i].first - it.last } ?: Int.MAX_VALUE
        val rightGap = columns.getOrNull(i + 1)?.let { it.first - columns[i].last } ?: Int.MAX_VALUE
        when {
            !furigana -> i
            leftGap <= rightGap -> i - 1
            else -> i + 1
        }
    }
    return columns.indices.groupBy { owners[it] }.values.map { merged -> columns[merged.first()].first..columns[merged.last()].last }
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

/** The widths of the blank runs between consecutive [bands]. */
private fun gaps(bands: List<IntRange>): List<Int> = bands.zipWithNext { before, after -> after.first - before.last - 1 }

private fun IntRange.length(): Int = last - first + 1

/** A crop's ink: its dark pixels, or its light ones when the crop is mostly dark, as light text on a dark box. */
private class Ink(crop: Bitmap) {
    private val width = crop.width
    private val height = crop.height
    private val inked: BooleanArray = run {
        val pixels = IntArray(width * height).also { crop.getPixels(it, 0, width, 0, 0, width, height) }
        val levels = IntArray(pixels.size) { luminance(pixels[it]) }
        val inverted = levels.average() < INK_LUMINANCE
        BooleanArray(pixels.size) { (levels[it] < INK_LUMINANCE) != inverted }
    }

    /** Per column, whether any of its pixels in [rows] is ink. */
    fun columns(rows: IntRange = 0 until height): List<Boolean> = (0 until width).map { x -> rows.any { y -> at(x, y) } }

    /** Per row, whether any of its pixels is ink. */
    fun rows(): List<Boolean> = (0 until height).map { y -> (0 until width).any { x -> at(x, y) } }

    /** The rows from the first to the last that hold ink within [columns]. */
    fun rowsIn(columns: IntRange): IntRange {
        val rows = (0 until height).filter { y -> columns.any { x -> at(x, y) } }
        return rows.first()..rows.last()
    }

    private fun at(x: Int, y: Int): Boolean = inked[y * width + x]

    private fun luminance(pixel: Int): Int =
        (((pixel shr 16) and 0xFF) * 299 + ((pixel shr 8) and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
}

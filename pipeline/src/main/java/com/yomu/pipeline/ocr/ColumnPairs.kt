package com.yomu.pipeline.ocr

// No speech bubble among OpenMantra's 1,592 annotated text boxes has more than seven columns, and splitting
// its 4-6 column bubbles raised the character error rate from 0.011 to 0.232. Rendered vertical text read
// whole turns to garbage from eight or nine columns, while pairs read cleanly at every size (#453).
private const val MIN_COLUMNS = 8
private const val COLUMNS_PER_PIECE = 2
// A column of vertical text stands several glyphs tall; a row of glyph-wide strips this squat is not one.
private const val MIN_TALLNESS = 2.5
// Furigana kana are set at about half the size of the text they gloss.
private const val FURIGANA_WIDTH = 0.6

/**
 * The x ranges of many-column vertical text two columns at a time, in reading order (right to left); the
 * leftmost piece holds one column when the count is odd. Empty unless the crop has at least [MIN_COLUMNS]
 * columns once furigana joins them, the columns are mostly tall, and the blanks between them are wider than
 * any blank between rows.
 */
internal fun columnPairs(ink: InkMask): List<IntRange> {
    val columns = joinFurigana(ink.columnStrokes())
    if (columns.size < MIN_COLUMNS) return emptyList()
    if (upperMedian(columns.map { tallness(ink, it) }) < MIN_TALLNESS) return emptyList()
    if (upperMedian(gapsBetween(columns)) <= widestRowGap(ink)) return emptyList()
    return columns.reversed().chunked(COLUMNS_PER_PIECE) { piece -> piece.last().first..piece.first().last }
}

/**
 * [columns], left to right, with each run of neighbouring columns narrower than [FURIGANA_WIDTH] of the
 * typical width joined into the column beside it as one column spanning both.
 */
private fun joinFurigana(columns: List<IntRange>): List<IntRange> {
    if (columns.isEmpty()) return columns
    // The upper quartile, so a box with a reading beside every column still finds the columns' own width.
    val typical = columns.map { it.length }.sorted()[columns.size * 3 / 4]
    val narrow = columns.map { it.length < FURIGANA_WIDTH * typical }
    val owner = IntArray(columns.size) { it }
    for (strip in runs(columns.indices) { narrow[it] }) {
        val joined = columnBeside(columns, strip)
        for (index in strip) owner[index] = joined
    }
    return columns.indices.groupBy { owner[it] }.values.map { members ->
        columns[members.first()].first..columns[members.last()].last
    }
}

/**
 * The index of the column a run of furigana [strip] (indices into [columns]) joins: the neighbour with the
 * smaller blank between them, or the only neighbour it has.
 */
private fun columnBeside(columns: List<IntRange>, strip: IntRange): Int {
    val left = strip.first - 1
    val right = strip.last + 1
    return when {
        left < 0 -> right
        right > columns.lastIndex -> left
        // A reading stands to the right of its column, so an even split goes to the column on the left.
        blankBetween(columns[left], columns[strip.first]) <= blankBetween(columns[strip.last], columns[right]) -> left
        else -> right
    }
}

/** How many times taller than wide the ink within [column] stands, first inked row to last. */
private fun tallness(ink: InkMask, column: IntRange): Double = ink.inkRows(column).length.toDouble() / column.length

/**
 * The widest blank between two rows of text across the crop, or 0 with fewer than two. Horizontal text set in
 * a grid shows columns too, but its blanks run between rows.
 */
private fun widestRowGap(ink: InkMask): Int = gapsBetween(ink.rowStrokes()).maxOrNull() ?: 0

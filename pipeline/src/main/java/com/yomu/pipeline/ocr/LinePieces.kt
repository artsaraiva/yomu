package com.yomu.pipeline.ocr

// Lengths in line heights. Checked on long one-line captions, white on black included, in a desktop
// replica of the OCR step (#453).
private const val MIN_LINE_LENGTH = 12
private const val PIECE_LENGTH = 5
// A cut may land up to a quarter piece either side of the target, so it can pick the widest gap nearby.
private const val NEAREST_CUT = 0.75
private const val FARTHEST_CUT = 1.25
// What is left after the last cut reads as one piece up to a piece and a half, and is never cut below half.
private const val LONGEST_LAST_PIECE = 1.5
private const val SHORTEST_LAST_PIECE = 0.5

/**
 * The x ranges of a single line of text at least [MIN_LINE_LENGTH] line heights long, cut at gaps into
 * pieces of about [PIECE_LENGTH] line heights, left to right. Empty unless the crop holds exactly one row of
 * text that long.
 */
internal fun linePieces(ink: InkMask): List<IntRange> {
    val line = ink.rowStrokes().singleOrNull() ?: return emptyList()
    val inked = (0 until ink.width).filter { x -> ink.columnHasInk(x, line) }
    val left = inked.firstOrNull() ?: return emptyList()
    val right = inked.last()
    if (right - left + 1 < MIN_LINE_LENGTH * line.length) return emptyList()
    val gaps = runs(left..right) { x -> !ink.columnHasInk(x, line) }
    val cuts = cuts(left, right, gaps, PIECE_LENGTH * line.length)
    return cuts.mapIndexed { index, cut -> cut until (cuts.getOrNull(index + 1) ?: (right + 1)) }
}

/**
 * Where the pieces of ink from [left] to [right] start, the first at [left]. Each further cut is at the widest of
 * [gaps] whose middle lies [NEAREST_CUT] to [FARTHEST_CUT] of [pieceLength] past the previous cut, the leftmost
 * on a tie, and that leaves at least [SHORTEST_LAST_PIECE] of [pieceLength] after it. Cutting stops once no
 * more than [LONGEST_LAST_PIECE] of [pieceLength] is left, or no gap qualifies.
 */
private fun cuts(left: Int, right: Int, gaps: List<IntRange>, pieceLength: Int): List<Int> {
    val cuts = mutableListOf(left)
    while (right + 1 - cuts.last() > LONGEST_LAST_PIECE * pieceLength) {
        val previous = cuts.last()
        val candidates = gaps.filter { gap ->
            val reach = (gap.first + gap.last) / 2.0 - previous
            val rest = right + 1 - cutIn(gap)
            reach in NEAREST_CUT * pieceLength..FARTHEST_CUT * pieceLength && rest >= SHORTEST_LAST_PIECE * pieceLength
        }
        // maxByOrNull keeps the first of equally wide gaps, and gaps run left to right.
        val widest = candidates.maxByOrNull { it.length } ?: break
        cuts += cutIn(widest)
    }
    return cuts
}

/** The first x of [gap]'s right half. */
private fun cutIn(gap: IntRange): Int = (gap.first + gap.last + 1) / 2

package com.yomu.pipeline.ocr

import android.graphics.Bitmap
import com.yomu.pipeline.bubble.luminance

// Mid-grey: manga text is near black on near-white balloons, or near white on near-black boxes.
private const val INK_LEVEL = 128
// Specks and slivers of balloon outline are narrower than any stroke of text.
private const val MIN_STROKE = 3

/**
 * Which pixels of a crop are text. Ink is dark on a light crop and light on a crop whose mean level is
 * below mid-grey, so white text on a black box is found like black text on a white one.
 */
internal class InkMask(crop: Bitmap) {
    val width: Int = crop.width
    val height: Int = crop.height
    private val ink = BooleanArray(width * height)

    init {
        val pixels = IntArray(width * height)
        crop.getPixels(pixels, 0, width, 0, 0, width, height)
        val levels = IntArray(pixels.size) { luminance(pixels[it]) }
        val darkCrop = levels.sumOf { it.toLong() } < INK_LEVEL.toLong() * levels.size
        for (i in levels.indices) ink[i] = if (darkCrop) levels[i] >= INK_LEVEL else levels[i] < INK_LEVEL
    }

    /** Whether column [x] has ink in any of [rows], the full height by default. */
    fun columnHasInk(x: Int, rows: IntRange = 0 until height): Boolean = rows.any { y -> ink[y * width + x] }

    /** Whether row [y] has ink anywhere across the crop. */
    fun rowHasInk(y: Int): Boolean = (0 until width).any { x -> ink[y * width + x] }

    /** The rows from the first to the last with ink within [columns]; empty when none has any. */
    fun inkRows(columns: IntRange): IntRange {
        val rows = (0 until height).filter { y -> columns.any { x -> ink[y * width + x] } }
        return if (rows.isEmpty()) IntRange.EMPTY else rows.first()..rows.last()
    }

    /** The [strokes] of the columns with ink over the full height, left to right. */
    fun columnStrokes(): List<IntRange> = strokes(0 until width) { x -> columnHasInk(x) }

    /** The [strokes] of the rows with ink anywhere across the crop, top to bottom. */
    fun rowStrokes(): List<IntRange> = strokes(0 until height) { y -> rowHasInk(y) }
}

/** How many positions the range covers. */
internal val IntRange.length: Int get() = last - first + 1

/** The maximal stretches of consecutive [positions] where [holds] is true, in order. */
internal fun runs(positions: IntRange, holds: (Int) -> Boolean): List<IntRange> {
    val found = mutableListOf<IntRange>()
    var start: Int? = null
    for (position in positions) {
        if (holds(position)) {
            if (start == null) start = position
        } else if (start != null) {
            found += start until position
            start = null
        }
    }
    start?.let { found += it..positions.last }
    return found
}

/** The [runs] at least [MIN_STROKE] long: text, without the specks beside it. */
internal fun strokes(positions: IntRange, holds: (Int) -> Boolean): List<IntRange> =
    runs(positions, holds).filter { it.length >= MIN_STROKE }

/** How many positions lie strictly between [left] and [right], which starts after it. */
internal fun blankBetween(left: IntRange, right: IntRange): Int = right.first - left.last - 1

/** The [blankBetween] each pair of neighbouring [ranges], which are in order. */
internal fun gapsBetween(ranges: List<IntRange>): List<Int> = ranges.zipWithNext(::blankBetween)

/** The element at index `size / 2` of [values] sorted ascending, the upper of the two middles when the count is even. */
internal fun <T : Comparable<T>> upperMedian(values: List<T>): T = values.sorted()[values.size / 2]

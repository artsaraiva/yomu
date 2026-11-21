package com.yomu.pipeline.ocr

import android.graphics.Bitmap

private const val INK_LUMINANCE = 128
// Up to three columns manga-ocr reads whole as well as it ever did.
private const val MIN_COLUMNS = 4
private const val COLUMNS_PER_PIECE = 2

/**
 * The x ranges to read [crop] in, in reading order. They lie side by side across the whole crop, each cut
 * halfway between two pieces' ink. Fewer than two when the crop is read whole.
 */
internal fun readingPieces(crop: Bitmap): List<IntRange> {
    val ink = Ink(crop)
    val columns = bands(ink.columns())
    if (columns.size < MIN_COLUMNS) return emptyList()
    val groups = columns.reversed().chunked(COLUMNS_PER_PIECE).map { group -> group.last().first..group.first().last }
    return sideBySide(groups, crop.width)
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

/** A crop's ink: its dark pixels. */
private class Ink(crop: Bitmap) {
    private val width = crop.width
    private val height = crop.height
    private val dark: BooleanArray = run {
        val pixels = IntArray(width * height).also { crop.getPixels(it, 0, width, 0, 0, width, height) }
        BooleanArray(pixels.size) { luminance(pixels[it]) < INK_LUMINANCE }
    }

    /** Per column, whether any of its pixels is ink. */
    fun columns(): List<Boolean> = (0 until width).map { x -> (0 until height).any { y -> dark[y * width + x] } }

    private fun luminance(pixel: Int): Int =
        (((pixel shr 16) and 0xFF) * 299 + ((pixel shr 8) and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
}

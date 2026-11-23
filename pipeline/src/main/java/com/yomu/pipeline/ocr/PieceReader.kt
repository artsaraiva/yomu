package com.yomu.pipeline.ocr

import android.graphics.Bitmap

/**
 * Reads a bubble crop with [readWhole], in pieces when its text is too wide for one read: manga-ocr squeezes
 * every crop to 224 × 224, so text in a crop far from square shrinks past reading (#453).
 */
internal class PieceReader(private val readWhole: (Bitmap) -> OcrResult?) {

    /**
     * The text of [crop]. Vertical text of many columns is read two columns at a time, right to left ([columnPairs]);
     * failing that, one long line is read in pieces left to right ([linePieces]); anything else is read whole and
     * returned as [readWhole] gave it. Piece reads are joined in reading order with the lowest piece confidence,
     * boxed as the whole crop. If any piece fails to read or reads blank, the crop is read whole instead.
     */
    fun read(crop: Bitmap): OcrResult? {
        val ink = InkMask(crop)
        val pieces = columnPairs(ink).ifEmpty { linePieces(ink) }
        if (pieces.size < 2) return readWhole(crop)
        val reads = mutableListOf<OcrResult>()
        for (piece in tile(pieces, crop.width)) {
            val read = readWhole(Bitmap.createBitmap(crop, piece.first, 0, piece.length, crop.height))
            if (read == null || read.text.isBlank()) return readWhole(crop)
            reads += read
        }
        return OcrResult(
            text = reads.joinToString("") { it.text },
            confidence = reads.minOf { it.confidence },
            boundingBox = floatArrayOf(crop.width.toFloat(), crop.height.toFloat(), 0f, 0f)
        )
    }
}

/**
 * [pieces], in their given order, widened to tile `0 until width`: neighbours meet halfway across the blank
 * between them, and the outermost reach the crop's edges.
 */
private fun tile(pieces: List<IntRange>, width: Int): List<IntRange> {
    val byX = pieces.sortedBy { it.first }
    val starts = listOf(0) + byX.zipWithNext { left, right -> (left.last + right.first + 1) / 2 }
    val ends = starts.drop(1).map { it - 1 } + (width - 1)
    val tiled = byX.indices.associate { byX[it] to starts[it]..ends[it] }
    return pieces.map { tiled.getValue(it) }
}

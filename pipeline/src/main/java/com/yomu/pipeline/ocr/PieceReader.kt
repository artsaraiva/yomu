package com.yomu.pipeline.ocr

import android.graphics.Bitmap

/**
 * Reads a bubble's crop with [readWhole], in pieces when the crop is too far from square for one read:
 * manga-ocr squeezes every crop to 224 × 224, so a wide narration box or a long caption shrinks past
 * reading (#429).
 */
internal class PieceReader(private val readWhole: (Bitmap) -> OcrResult?) {

    /**
     * [crop]'s text, read whole or in [readingPieces] joined in reading order. A joined read is as sure as its
     * least sure piece, so the skip of unsure reads (#366) still applies. When a piece fails to read or reads as
     * nothing, as a failed decode does, the crop is read whole instead. Null only when the whole read fails.
     */
    fun read(crop: Bitmap): OcrResult? {
        val pieces = readingPieces(crop)
        if (pieces.size < 2) return readWhole(crop)
        val reads = pieces.map { piece ->
            readWhole(Bitmap.createBitmap(crop, piece.first, 0, piece.last - piece.first + 1, crop.height))
                ?.takeIf { it.text.isNotBlank() }
                ?: return readWhole(crop)
        }
        return OcrResult(
            text = reads.joinToString("") { it.text },
            confidence = reads.minOf { it.confidence },
            // The box OcrEngine gives a whole read of the crop.
            boundingBox = floatArrayOf(crop.width.toFloat(), crop.height.toFloat(), 0f, 0f)
        )
    }
}

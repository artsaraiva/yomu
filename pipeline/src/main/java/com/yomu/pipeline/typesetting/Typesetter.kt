package com.yomu.pipeline.typesetting

import android.graphics.Paint
import android.graphics.Typeface
import com.yomu.pipeline.translation.TranslatedBubble

data class TypesetBubble(
    val bubbleId: Int,
    val translatedText: String,
    val originalText: String,
    val fontSize: Float,
    val textLines: List<String>,
    val boundingBox: FloatArray,
    val backgroundColor: Int = 0xF0FFFFFF.toInt(),
    val textColor: Int = 0xFF000000.toInt(),
    /** Spike #471: the balloon shape the lines follow; the overlay paints it as an oval (or rectangle) plus [coverBox]. */
    val shapeBounds: FloatArray? = null,
    val shapeIsEllipse: Boolean = false,
    /** Spike #471: the glyph box, painted too so the Japanese stays covered where the oval is narrower. */
    val coverBox: FloatArray? = null
) {
    // Typesetting fits the box against this height, so rendering has to draw with it too.
    val lineHeight: Float get() = fontSize * LINE_HEIGHT_RATIO

    companion object {
        const val LINE_HEIGHT_RATIO = 1.5f
    }
}

class Typesetter(var fontSizeScale: Float = 1.0f) {

    companion object {
        private const val MIN_FONT_SIZE = 10f
        private const val MAX_FONT_SIZE = 36f
        // A box narrower than its longest word at this size is widened rather than breaking the
        // word letter by letter (a tall text box around vertical はい rendered "Y / e / s").
        private const val MIN_WORD_FONT_SIZE = 20f
        // CJK punctuation, kana and ideographs all sit at or above U+3000.
        private const val CJK_START = 0x3000
        private const val PADDING = 0.1f
        // Spike #471: the balloon's ellipse is its interior, not a box inside it, so the margin is smaller.
        private const val SHAPE_PADDING = 0.06f
        // Fixed growth budget, take page bounds into the Typesetter if bubbles need
        // to grow against real page room rather than a multiple of their own box.
        private const val MAX_BOX_GROWTH = 3f
        private const val ELLIPSIS = "\u2026"
        private val OCR_TOKEN_REGEX = Regex("""\[(CLS|SEP|PAD)\]""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }

    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.DEFAULT
    }

    fun typeset(
        translations: List<TranslatedBubble>,
        bubbleBounds: Map<Int, FloatArray>
    ): List<TypesetBubble> {
        return translations.map { translation ->
            val bounds = bubbleBounds[translation.bubbleId]
                ?: floatArrayOf(0f, 0f, 100f, 50f)

            val cleaned = cleanText(translation.translatedText)
            val boxWidth = (bounds[2] - bounds[0]) * (1 - PADDING * 2)
            val maxHeight = (bounds[3] - bounds[1]) * (1 - PADDING * 2)
            val searchWidth = maxOf(boxWidth, longestWordWidth(cleaned, MIN_WORD_FONT_SIZE))
            val fontSize = calculateFontSize(cleaned, searchWidth, maxHeight)
            // fontSizeScale can push a word past the searched width, so widen to the final size too.
            val maxWidth = maxOf(searchWidth, longestWordWidth(cleaned, fontSize))
            val lines = fitLines(wrapText(cleaned, maxWidth, fontSize), fontSize, maxWidth, maxHeight)
            val textHeight = lineHeight(fontSize) * lines.size

            TypesetBubble(
                bubbleId = translation.bubbleId,
                translatedText = cleaned,
                originalText = translation.originalText,
                fontSize = fontSize,
                textLines = lines,
                boundingBox = grownBounds(bounds, maxWidth, textHeight)
            )
        }
    }

    /**
     * Spike #471: typesets [translation] inside [shape] ({left, top, right, bottom}). An ellipse gives
     * each line the chord at its band edge farther from the centre; a rectangle gives every line its
     * full padded width. Tries font sizes from the largest down and, per size, line counts from one up,
     * keeping the first fit (BallonsTranslator's search). Null when nothing fits at the smallest size.
     */
    fun typesetShaped(
        translation: TranslatedBubble,
        shape: FloatArray,
        ellipse: Boolean,
        coverBox: FloatArray
    ): TypesetBubble? {
        val cleaned = cleanText(translation.translatedText)
        if (cleaned.isEmpty()) return null
        val cx = (shape[0] + shape[2]) / 2f
        val cy = (shape[1] + shape[3]) / 2f
        val pad = if (ellipse) SHAPE_PADDING else PADDING
        val a = (shape[2] - shape[0]) / 2f * (1 - pad * 2)
        val b = (shape[3] - shape[1]) / 2f * (1 - pad * 2)
        var size = MAX_FONT_SIZE
        while (size >= MIN_FONT_SIZE) {
            val lh = lineHeight(size)
            val maxLines = (2 * b / lh).toInt()
            for (n in 1..maxLines) {
                val widths = FloatArray(n) { i ->
                    if (!ellipse) return@FloatArray 2 * a
                    val top = cy - n * lh / 2f + i * lh
                    val d = maxOf(kotlin.math.abs(top - cy), kotlin.math.abs(top + lh - cy))
                    if (d >= b) 0f else 2 * a * kotlin.math.sqrt(1 - (d / b) * (d / b))
                }
                val lines = wrapInto(cleaned, widths, size) ?: continue
                val scaled = (size * fontSizeScale).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE * 2f)
                if (scaled != size) return null // the reader's scale would need its own wrap; out of spike scope
                return TypesetBubble(
                    bubbleId = translation.bubbleId,
                    translatedText = cleaned,
                    originalText = translation.originalText,
                    fontSize = size,
                    textLines = lines,
                    boundingBox = shape,
                    shapeBounds = shape,
                    shapeIsEllipse = ellipse,
                    coverBox = coverBox
                )
            }
            size -= 1f
        }
        return null
    }

    /** Greedy wrap into exactly the given line widths; null when a word does not fit its line or lines run out. */
    private fun wrapInto(text: String, widths: FloatArray, fontSize: Float): List<String>? {
        paint.textSize = fontSize
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(" ")) {
            val test = if (current.isEmpty()) word else "$current $word"
            val line = lines.size
            if (line >= widths.size) return null
            if (paint.measureText(test) <= widths[line]) {
                current = test
            } else {
                if (current.isEmpty()) return null
                lines.add(current)
                if (lines.size >= widths.size || paint.measureText(word) > widths[lines.size]) return null
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    private fun cleanText(text: String): String =
        OCR_TOKEN_REGEX.replace(text, "")
            .let { WHITESPACE_REGEX.replace(it.trim(), " ") }

    private fun calculateFontSize(text: String, maxWidth: Float, maxHeight: Float): Float {
        var low = MIN_FONT_SIZE
        var high = MAX_FONT_SIZE
        var bestSize = MIN_FONT_SIZE

        while (low <= high) {
            val mid = (low + high) / 2
            val lines = wrapText(text, maxWidth, mid)
            val totalHeight = lineHeight(mid) * lines.size

            if (totalHeight <= maxHeight && longestWordWidth(text, mid) <= maxWidth) {
                bestSize = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return (bestSize * fontSizeScale).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE * 2f)
    }

    private fun lineHeight(fontSize: Float): Float = fontSize * TypesetBubble.LINE_HEIGHT_RATIO

    // Japanese has no spaces and breaks anywhere, so untranslated source text (the per-bubble
    // fallback) is left to wrap by character instead of widening the box to one long line.
    private fun longestWordWidth(text: String, fontSize: Float): Float {
        paint.textSize = fontSize
        return text.split(" ")
            .filter { word -> word.none { it.code >= CJK_START } }
            .maxOfOrNull { paint.measureText(it) } ?: 0f
    }

    private fun grownBounds(bounds: FloatArray, textWidth: Float, textHeight: Float): FloatArray {
        val width = maxOf(bounds[2] - bounds[0], textWidth / (1 - PADDING * 2))
        val height = maxOf(bounds[3] - bounds[1], textHeight / (1 - PADDING * 2))
        val centerX = (bounds[0] + bounds[2]) / 2f
        val centerY = (bounds[1] + bounds[3]) / 2f
        return floatArrayOf(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
    }

    private fun wrapText(text: String, maxWidth: Float, fontSize: Float): List<String> {
        paint.textSize = fontSize
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (paint.measureText(word) > maxWidth) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
                var chunk = StringBuilder()
                for (ch in word) {
                    val test = chunk.toString() + ch
                    if (paint.measureText(test) <= maxWidth) {
                        chunk.append(ch)
                    } else {
                        if (chunk.isNotEmpty()) lines.add(chunk.toString())
                        chunk = StringBuilder(ch.toString())
                    }
                }
                if (chunk.isNotEmpty()) currentLine = chunk
            } else {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) <= maxWidth) {
                    currentLine.append(if (currentLine.isNotEmpty()) " " else "").append(word)
                } else {
                    if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                }
            }
        }

        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }

    // Past the growth budget the cut is marked, so lost text is visible instead of silently dropped.
    private fun fitLines(
        lines: List<String>,
        fontSize: Float,
        maxWidth: Float,
        maxHeight: Float
    ): List<String> {
        val maxLines = maxOf(1, (maxHeight * MAX_BOX_GROWTH / lineHeight(fontSize)).toInt())
        if (lines.size <= maxLines) return lines
        return lines.take(maxLines).toMutableList().also {
            it[it.lastIndex] = ellipsize(it.last(), fontSize, maxWidth)
        }
    }

    private fun ellipsize(line: String, fontSize: Float, maxWidth: Float): String {
        paint.textSize = fontSize
        var kept = line.trimEnd()
        while (kept.isNotEmpty() && paint.measureText(kept + ELLIPSIS) > maxWidth) {
            kept = kept.dropLast(1).trimEnd()
        }
        return kept + ELLIPSIS
    }
}

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
    val textColor: Int = 0xFF000000.toInt()
)

class Typesetter(var fontSizeScale: Float = 1.0f) {

    companion object {
        private const val MIN_FONT_SIZE = 10f
        private const val MAX_FONT_SIZE = 36f
        private const val LINE_SPACING = 1.3f
        private const val PADDING = 0.1f
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
            val maxWidth = (bounds[2] - bounds[0]) * (1 - PADDING * 2)
            val maxHeight = (bounds[3] - bounds[1]) * (1 - PADDING * 2)
            val fontSize = calculateFontSize(cleaned, maxWidth, maxHeight)
            val lines = fitLines(wrapText(cleaned, maxWidth, fontSize), fontSize, maxHeight)

            TypesetBubble(
                bubbleId = translation.bubbleId,
                translatedText = cleaned,
                originalText = translation.originalText,
                fontSize = fontSize,
                textLines = lines,
                boundingBox = bounds
            )
        }
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
            paint.textSize = mid
            val lines = wrapText(text, maxWidth, mid)
            val totalHeight = paint.fontSpacing * LINE_SPACING * lines.size

            if (totalHeight <= maxHeight) {
                bestSize = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return (bestSize * fontSizeScale).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE * 2f)
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

    private fun fitLines(lines: List<String>, fontSize: Float, maxHeight: Float): List<String> {
        paint.textSize = fontSize
        val lineHeight = paint.fontSpacing * LINE_SPACING
        val maxLines = maxOf(1, (maxHeight / lineHeight).toInt())
        return lines.take(maxLines)
    }
}

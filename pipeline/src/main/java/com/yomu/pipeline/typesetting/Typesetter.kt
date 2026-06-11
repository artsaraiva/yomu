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
    val backgroundColor: Int = 0xCC000000.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt()
)

class Typesetter {

    companion object {
        private const val MIN_FONT_SIZE = 10f
        private const val MAX_FONT_SIZE = 36f
        private const val TARGET_LINE_LENGTH = 12
        private const val LINE_SPACING = 1.3f
        private const val PADDING = 0.1f
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

            val fontSize = calculateFontSize(
                translation.translatedText,
                bounds
            )

            val textLines = wrapText(
                translation.translatedText,
                bounds,
                fontSize
            )

            TypesetBubble(
                bubbleId = translation.bubbleId,
                translatedText = translation.translatedText,
                originalText = translation.originalText,
                fontSize = fontSize,
                textLines = textLines,
                boundingBox = bounds
            )
        }
    }

    private fun calculateFontSize(text: String, bounds: FloatArray): Float {
        val bubbleWidth = (bounds[2] - bounds[0]) * (1 - PADDING * 2)
        val bubbleHeight = (bounds[3] - bounds[1]) * (1 - PADDING * 2)

        var low = MIN_FONT_SIZE
        var high = MAX_FONT_SIZE
        var bestSize = low

        while (low <= high) {
            val mid = (low + high) / 2
            paint.textSize = mid

            val textHeight = paint.fontSpacing * LINE_SPACING *
                ((text.length / TARGET_LINE_LENGTH) + 1)

            if (textHeight <= bubbleHeight) {
                bestSize = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return bestSize
    }

    private fun wrapText(text: String, bounds: FloatArray, fontSize: Float): List<String> {
        val bubbleWidth = (bounds[2] - bounds[0]) * (1 - PADDING * 2)
        paint.textSize = fontSize

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val lineWidth = paint.measureText(testLine)

            if (lineWidth <= bubbleWidth) {
                currentLine.append(if (currentLine.isNotEmpty()) " " else "").append(word)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }
}

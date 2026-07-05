package com.yomu.pipeline.typesetting

import com.yomu.pipeline.translation.TranslatedBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TypesetterTest {

    private val typesetter = Typesetter()

    private fun bounds(w: Float, h: Float) = floatArrayOf(0f, 0f, w, h)

    private fun bubble(id: Int, text: String) = TranslatedBubble(
        bubbleId = id,
        originalText = "",
        translatedText = text,
        confidence = 1f
    )

    @Test
    fun `text lines fit within bubble height`() {
        val result = typesetter.typeset(
            listOf(bubble(1, "Hello world this is a test")),
            mapOf(1 to bounds(200f, 80f))
        )
        val b = result.first()
        val lineHeight = b.fontSize * 1.5f
        assertTrue(lineHeight * b.textLines.size <= 80f * 1.1f)
    }

    @Test
    fun `no line exceeds bubble width`() {
        val result = typesetter.typeset(
            listOf(bubble(1, "Hello world this is a longer translation test")),
            mapOf(1 to bounds(150f, 100f))
        )
        val b = result.first()
        val maxWidth = 150f
        for (line in b.textLines) {
            assertTrue("Line '$line' must fit in width", line.length <= maxWidth.toInt() + 30)
        }
    }

    @Test
    fun `ocr special tokens are stripped`() {
        val result = typesetter.typeset(
            listOf(bubble(1, "[CLS] Hello world [SEP] [PAD]")),
            mapOf(1 to bounds(200f, 100f))
        )
        val cleaned = result.first().translatedText
        assertTrue(!cleaned.contains("[CLS]"))
        assertTrue(!cleaned.contains("[SEP]"))
        assertTrue(!cleaned.contains("[PAD]"))
        assertEquals("Hello world", cleaned)
    }

    @Test
    fun `long word without spaces produces at least one line`() {
        val longWord = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val result = typesetter.typeset(
            listOf(bubble(1, longWord)),
            mapOf(1 to bounds(80f, 200f))
        )
        val b = result.first()
        assertTrue("Should produce at least one line", b.textLines.isNotEmpty())
        assertTrue("All lines should be non-empty", b.textLines.all { it.isNotEmpty() })
    }

    @Test
    fun `default background is white and text is black`() {
        val result = typesetter.typeset(
            listOf(bubble(1, "Test")),
            mapOf(1 to bounds(100f, 60f))
        )
        val b = result.first()
        assertEquals(0xF0FFFFFF.toInt(), b.backgroundColor)
        assertEquals(0xFF000000.toInt(), b.textColor)
    }

    @Test
    fun `repeated whitespace is collapsed`() {
        val result = typesetter.typeset(
            listOf(bubble(1, "Hello   world\n\ntest")),
            mapOf(1 to bounds(200f, 100f))
        )
        assertTrue(!result.first().translatedText.contains("  "))
        assertTrue(!result.first().translatedText.contains("\n"))
    }
}

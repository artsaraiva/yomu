package com.yomu.pipeline.typesetting

import com.yomu.pipeline.translation.TranslatedBubble
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spike #471: lines shaped to the balloon's ellipse against today's 0.707 rectangle. */
@RunWith(RobolectricTestRunner::class)
class ShapedTypesetTest {

    private val text = "I told you already, I can see them. The dead ones, I mean."
    private val translated = TranslatedBubble(bubbleId = 1, originalText = "x", translatedText = text, answered = true)

    @Test
    fun `an ellipse reaches at least the font size of its inscribed rectangle`() {
        val interior = floatArrayOf(0f, 0f, 200f, 300f)
        val inset = 0.707f
        val rectBox = floatArrayOf(100 - 100 * inset, 150 - 150 * inset, 100 + 100 * inset, 150 + 150 * inset)
        val typesetter = Typesetter()

        val rect = typesetter.typeset(listOf(translated), mapOf(1 to rectBox)).single()
        val shaped = typesetter.typesetShaped(translated, interior, ellipse = true, coverBox = rectBox)

        assertNotNull(shaped)
        assertTrue("shaped ${shaped!!.fontSize} vs rect ${rect.fontSize}", shaped.fontSize >= rect.fontSize)
        assertTrue(shaped.textLines.joinToString(" ") == text)
    }
}

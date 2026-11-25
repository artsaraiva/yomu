package com.yomu.pipeline.typesetting

import android.graphics.Paint
import android.graphics.Typeface
import com.yomu.pipeline.bubble.BalloonShape
import com.yomu.pipeline.bubble.RenderArea
import com.yomu.pipeline.translation.TranslatedBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.sqrt

// Real text widths: the legacy shadow Paint measures one pixel per character at any size, and
// Robolectric 4.18 has no native graphics for SDK 36 yet.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class ShapedTypesetTest {

    private val text = "I told you already, I can see them. The dead ones, I mean."
    private val translated = TranslatedBubble(bubbleId = 1, originalText = "x", translatedText = text, answered = true)

    // A 200x300 interior and the 0.707 rectangle inside it, as bubbleRenderAreas builds them.
    private val interior = floatArrayOf(0f, 0f, 200f, 300f)
    private val inscribed = floatArrayOf(29.3f, 43.95f, 170.7f, 256.05f)
    private val glyphs = floatArrayOf(90f, 60f, 110f, 240f)

    private fun area(shape: BalloonShape?) = RenderArea(box = inscribed, shape = shape, glyphBox = glyphs)

    @Test
    fun `a round balloon gets a larger font than its inscribed rectangle`() {
        val typesetter = Typesetter()
        val rect = typesetter.typeset(listOf(translated), mapOf(1 to inscribed)).single()

        val shaped = typesetter.typeset(translated, area(BalloonShape(interior, ellipse = true)))

        assertTrue("shaped ${shaped.fontSize} vs rect ${rect.fontSize}", shaped.fontSize > rect.fontSize)
        assertEquals(text, shaped.textLines.joinToString(" "))
        assertNotNull(shaped.shape)
        assertTrue(shaped.boundingBox.contentEquals(interior))
        assertTrue(shaped.coverBox.contentEquals(glyphs))
    }

    @Test
    fun `each line fits the chord of the ellipse at its band`() {
        val shaped = Typesetter().typeset(translated, area(BalloonShape(interior, ellipse = true)))

        val paint = Paint().apply { typeface = Typeface.DEFAULT; textSize = shaped.fontSize }
        val a = 100f
        val b = 150f
        val top = 150f - shaped.lineHeight * shaped.textLines.size / 2f
        shaped.textLines.forEachIndexed { i, line ->
            val bandTop = top + i * shaped.lineHeight
            val d = maxOf(abs(bandTop - 150f), abs(bandTop + shaped.lineHeight - 150f))
            val chord = 2 * a * sqrt(1 - (d / b) * (d / b))
            assertTrue("'$line' ${paint.measureText(line)} > chord $chord", paint.measureText(line) <= chord)
        }
    }

    @Test
    fun `a caption box gets full-width lines`() {
        val shaped = Typesetter().typeset(translated, area(BalloonShape(interior, ellipse = false)))

        assertEquals(false, shaped.shape?.ellipse)
    }

    @Test
    fun `no shape keeps today's typeset`() {
        val typesetter = Typesetter()
        val rect = typesetter.typeset(listOf(translated), mapOf(1 to inscribed)).single()

        val result = typesetter.typeset(translated, area(null))

        assertEquals(rect, result.copy(boundingBox = rect.boundingBox))
        assertTrue(result.boundingBox.contentEquals(rect.boundingBox))
        assertNull(result.shape)
    }

    @Test
    fun `a shape that would shrink the font keeps today's typeset`() {
        // A balloon narrower than the box it is supposed to hold: its chords lose to the rectangle.
        val sliver = BalloonShape(floatArrayOf(80f, 40f, 120f, 260f), ellipse = true)

        val result = Typesetter().typeset(translated, area(sliver))

        assertNull(result.shape)
    }

    @Test
    fun `the shaped typeset wins only with a strictly larger font`() {
        val rect = TypesetBubble(1, "a", "x", fontSize = 20f, textLines = listOf("a"), boundingBox = inscribed)
        val same = rect.copy(shape = BalloonShape(interior, ellipse = true))
        val larger = same.copy(fontSize = 21f)

        assertSame(rect, largerFont(rect, null))
        assertSame(rect, largerFont(rect, same))
        assertSame(rect, largerFont(rect, same.copy(fontSize = 19f)))
        assertSame(larger, largerFont(rect, larger))
    }

    @Test
    fun `the reader's font scale applies to shaped lines`() {
        val plain = Typesetter().typeset(translated, area(BalloonShape(interior, ellipse = true)))

        val smaller = Typesetter(fontSizeScale = 0.5f).typeset(translated, area(BalloonShape(interior, ellipse = true)))

        assertTrue("scaled ${smaller.fontSize} vs ${plain.fontSize}", smaller.fontSize < plain.fontSize)
    }
}

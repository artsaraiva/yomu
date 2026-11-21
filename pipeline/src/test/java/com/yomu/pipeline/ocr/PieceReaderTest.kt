package com.yomu.pipeline.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PieceReaderTest {

    /** A box of ink standing in for one character, coloured by its [label] so the fake reader can name it. */
    private data class Glyph(val label: Char, val left: Int, val top: Int, val width: Int, val height: Int)

    private val labels = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun darkInk(label: Char): Int = Color.rgb(8 + 8 * labels.indexOf(label), 0, 0)

    /**
     * Vertical text: one column per label, [labels] in reading order from the right edge of a
     * [width] × 160 crop. Each column is six [columnWidth]-wide glyphs, 16 px tall with 4 px between them.
     */
    private fun columns(labels: String, width: Int, columnWidth: Int = 16, gap: Int = 10): List<Glyph> =
        labels.flatMapIndexed { column, label ->
            val left = width - 10 - columnWidth - column * (columnWidth + gap)
            (0 until 6).map { row -> Glyph(label, left, 10 + row * 20, columnWidth, 16) }
        }

    /**
     * One horizontal line, 16 px tall: a square glyph per label, [labels] in reading order from x = 10, 4 px apart
     * unless [gapsAfter] widens the gap after a label, in a crop 10 px wider than the line.
     */
    private fun line(labels: String, gapsAfter: Map<Char, Int> = emptyMap()): Bitmap {
        var left = 10
        val glyphs = labels.map { label -> Glyph(label, left, 12, 16, 16).also { left += 16 + (gapsAfter[label] ?: 4) } }
        return crop(glyphs.last().left + 16 + 10, 40, glyphs)
    }

    private fun crop(width: Int, height: Int, glyphs: List<Glyph>, paper: Int = Color.WHITE, ink: (Char) -> Int = ::darkInk): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(paper)
            val canvas = Canvas(this)
            val paint = Paint()
            for (glyph in glyphs) {
                paint.color = ink(glyph.label)
                canvas.drawRect(
                    glyph.left.toFloat(),
                    glyph.top.toFloat(),
                    (glyph.left + glyph.width).toFloat(),
                    (glyph.top + glyph.height).toFloat(),
                    paint
                )
            }
        }

    /**
     * Reads a crop as the labels of every glyph with any ink in it, in label order, and records what each crop
     * it was asked for held. A crop listed in [unreadable] fails to read.
     */
    private class FakeWholeReader(
        private val labelsByColor: Map<Int, Char>,
        private val confidences: Map<String, Float> = emptyMap(),
        private val unreadable: Set<String> = emptySet()
    ) {
        val asked = mutableListOf<String>()

        fun read(crop: Bitmap): OcrResult? {
            val pixels = IntArray(crop.width * crop.height)
            crop.getPixels(pixels, 0, crop.width, 0, 0, crop.width, crop.height)
            val text = pixels.asList().mapNotNull(labelsByColor::get).toSortedSet().joinToString("")
            asked += text
            if (text in unreadable) return null
            return OcrResult(text, confidences[text] ?: 0.99f, floatArrayOf())
        }
    }

    private fun fakeReader(
        confidences: Map<String, Float> = emptyMap(),
        unreadable: Set<String> = emptySet(),
        ink: (Char) -> Int = ::darkInk
    ) = FakeWholeReader(labels.associateBy(ink), confidences, unreadable)

    @Test
    fun `four columns are read two at a time, right to left, and joined`() {
        val reader = fakeReader()

        val read = PieceReader(reader::read).read(crop(124, 160, columns("ABCD", width = 124)))

        assertEquals(listOf("AB", "CD"), reader.asked)
        assertEquals("ABCD", read?.text)
    }

    @Test
    fun `a joined read is as sure as its least sure piece`() {
        val reader = fakeReader(confidences = mapOf("AB" to 0.98f, "CD" to 0.91f))

        val read = PieceReader(reader::read).read(crop(124, 160, columns("ABCD", width = 124)))

        assertEquals(0.91f, read?.confidence)
    }

    @Test
    fun `three columns are read whole`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(crop(98, 160, columns("ABC", width = 98)))

        assertEquals(listOf("ABC"), reader.asked)
    }

    @Test
    fun `a long line is cut between characters into pieces about five line heights wide`() {
        val reader = fakeReader()

        val read = PieceReader(reader::read).read(line("ABCDEFGHIJKLMNOPQRST"))

        assertEquals(listOf("ABCD", "EFGH", "IJKL", "MNOP", "QRST"), reader.asked)
        assertEquals("ABCDEFGHIJKLMNOPQRST", read?.text)
    }

    @Test
    fun `a short line is read whole`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(line("ABCDEF"))

        assertEquals(listOf("ABCDEF"), reader.asked)
    }
}

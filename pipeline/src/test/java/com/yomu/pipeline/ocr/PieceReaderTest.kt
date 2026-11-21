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

    private val labels = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    private fun darkInk(label: Char): Int = Color.rgb(4 + 4 * labels.indexOf(label), 0, 0)

    private fun lightInk(label: Char): Int = Color.rgb(255, 255, 251 - 4 * labels.indexOf(label))

    /**
     * Vertical text in a crop 160 px tall: one column per label, [labels] in reading order from 10 px off the
     * right edge, 26 px apart. Each column is six 16 px glyphs with 4 px between them. [extra] glyphs are drawn too.
     */
    private fun columns(
        labels: String,
        extra: List<Glyph> = emptyList(),
        paper: Int = Color.WHITE,
        ink: (Char) -> Int = ::darkInk
    ): Bitmap {
        val width = 20 + 26 * labels.length
        val glyphs = labels.flatMapIndexed { column, label ->
            (0 until 6).map { row -> Glyph(label, width - 26 - column * 26, 10 + row * 20, 16, 16) }
        }
        return crop(width, 160, glyphs + extra, paper, ink)
    }

    /**
     * One horizontal line, 16 px tall: a square glyph per label, [labels] in reading order from x = 10, 4 px apart
     * unless [gapsAfter] widens the gap after a label, in a crop 10 px wider than the line. [extra] glyphs are drawn too.
     */
    private fun line(labels: String, gapsAfter: Map<Char, Int> = emptyMap(), extra: List<Glyph> = emptyList()): Bitmap {
        var left = 10
        val glyphs = labels.map { label -> Glyph(label, left, 12, 16, 16).also { left += 16 + (gapsAfter[label] ?: 4) } }
        return crop(glyphs.last().left + 16 + 10, 40, glyphs + extra)
    }

    /**
     * Horizontal text: one line per label, [labels] from the top, each eight 16 px square glyphs 4 px apart and
     * 10 px below the line above, so the glyphs of every line stand in the same columns.
     */
    private fun lines(labels: String): List<Glyph> = labels.flatMapIndexed { line, label ->
        (0 until 8).map { glyph -> Glyph(label, 10 + glyph * 20, 10 + line * 26, 16, 16) }
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
     * it was asked for held. A crop listed in [unreadable] fails to read; one in [blank] reads as nothing, as a
     * failed decode does.
     */
    private class FakeWholeReader(
        private val labelsByColor: Map<Int, Char>,
        private val confidences: Map<String, Float> = emptyMap(),
        private val unreadable: Set<String> = emptySet(),
        private val blank: Set<String> = emptySet()
    ) {
        val asked = mutableListOf<String>()

        fun read(crop: Bitmap): OcrResult? {
            val pixels = IntArray(crop.width * crop.height)
            crop.getPixels(pixels, 0, crop.width, 0, 0, crop.width, crop.height)
            val text = pixels.asList().mapNotNull(labelsByColor::get).toSortedSet().joinToString("")
            asked += text
            if (text in unreadable) return null
            if (text in blank) return OcrResult("", 0f, floatArrayOf())
            return OcrResult(text, confidences[text] ?: 0.99f, floatArrayOf())
        }
    }

    private fun fakeReader(
        confidences: Map<String, Float> = emptyMap(),
        unreadable: Set<String> = emptySet(),
        blank: Set<String> = emptySet(),
        ink: (Char) -> Int = ::darkInk
    ) = FakeWholeReader(labels.associateBy(ink), confidences, unreadable, blank)

    @Test
    fun `eight columns are read two at a time, right to left, and joined`() {
        val reader = fakeReader()

        val read = PieceReader(reader::read).read(columns("ABCDEFGH"))

        assertEquals(listOf("AB", "CD", "EF", "GH"), reader.asked)
        assertEquals("ABCDEFGH", read?.text)
    }

    @Test
    fun `a joined read is as sure as its least sure piece`() {
        val reader = fakeReader(confidences = mapOf("AB" to 0.98f, "CD" to 0.91f))

        val read = PieceReader(reader::read).read(columns("ABCDEFGH"))

        assertEquals(0.91f, read?.confidence)
    }

    @Test
    fun `a piece that fails to read sends the crop to a whole read`() {
        val reader = fakeReader(confidences = mapOf("ABCDEFGH" to 0.9f), unreadable = setOf("CD"))

        val read = PieceReader(reader::read).read(columns("ABCDEFGH"))

        assertEquals(listOf("AB", "CD", "ABCDEFGH"), reader.asked)
        assertEquals("ABCDEFGH" to 0.9f, read?.let { it.text to it.confidence })
    }

    @Test
    fun `a piece that reads as nothing sends the crop to a whole read`() {
        val reader = fakeReader(blank = setOf("CD"))

        val read = PieceReader(reader::read).read(columns("ABCDEFGH"))

        assertEquals(listOf("AB", "CD", "ABCDEFGH"), reader.asked)
        assertEquals("ABCDEFGH", read?.text)
    }

    @Test
    fun `light columns on a dark box are read in pairs like dark columns on light`() {
        val reader = fakeReader(ink = ::lightInk)

        PieceReader(reader::read).read(columns("ABCDEFGH", paper = Color.BLACK, ink = ::lightInk))

        assertEquals(listOf("AB", "CD", "EF", "GH"), reader.asked)
    }

    @Test
    fun `furigana is read with the column it sits beside`() {
        val reader = fakeReader()
        // B's reading, three 6 px kana in the gap to B's right, 1 px from B and 3 px from A.
        val furigana = (0 until 3).map { kana -> Glyph('b', 193, 12 + kana * 10, 6, 8) }

        PieceReader(reader::read).read(columns("ABCDEFGH", extra = furigana))

        assertEquals(listOf("ABb", "CD", "EF", "GH"), reader.asked)
    }

    @Test
    fun `furigana that breaks into two strips is read with its column`() {
        val reader = fakeReader()
        // B's reading as two 3 px strips side by side in the gap to B's right, as kana of uneven widths leave it.
        val furigana = (0 until 3).map { kana -> Glyph('b', 193, 12 + kana * 10, 3, 8) } +
            (0 until 2).map { kana -> Glyph('b', 197, 14 + kana * 10, 3, 8) }

        PieceReader(reader::read).read(columns("ABCDEFGH", extra = furigana))

        assertEquals(listOf("ABb", "CD", "EF", "GH"), reader.asked)
    }

    @Test
    fun `specks narrower than a stroke are not columns`() {
        val reader = fakeReader()
        // Two 1 px specks side by side in the gap between A and B, which would make seven columns nine.
        val specks = listOf(167, 170).map { x -> Glyph('z', x, 40, 1, 12) }

        PieceReader(reader::read).read(columns("ABCDEFG", extra = specks))

        assertEquals(listOf("ABCDEFGz"), reader.asked)
    }

    @Test
    fun `seven columns are read whole`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(columns("ABCDEFG"))

        assertEquals(listOf("ABCDEFG"), reader.asked)
    }

    @Test
    fun `three lines of horizontal text are read whole, not in vertical strips`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(crop(176, 88, lines("ABC")))

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
    fun `a long line is cut at the widest gap within reach`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(line("ABCDEFGHIJKLMNOPQRST", gapsAfter = mapOf('E' to 8)))

        assertEquals("ABCDE", reader.asked.first())
    }

    @Test
    fun `a short tail stays on the last piece`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(line("ABCDEFGHIJKLMNOPQRSTUV"))

        assertEquals(listOf("ABCD", "EFGH", "IJKL", "MNOP", "QRSTUV"), reader.asked)
    }

    @Test
    fun `a cut never leaves a tail shorter than half a piece`() {
        val reader = fakeReader()

        // The wider gap after U is in reach, but cutting there would leave VW, 39 px, to be read alone.
        PieceReader(reader::read).read(line("ABCDEFGHIJKLMNOPQRSTUVW", gapsAfter = mapOf('U' to 5)))

        assertEquals(listOf("ABCD", "EFGH", "IJKL", "MNOP", "QRST", "UVW"), reader.asked)
    }

    @Test
    fun `a speck above a long line does not keep it whole`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(line("ABCDEFGHIJKLMNOPQRST", extra = listOf(Glyph('z', 50, 2, 2, 2))))

        assertEquals(listOf("ABCDz", "EFGH", "IJKL", "MNOP", "QRST"), reader.asked)
    }

    @Test
    fun `a line under twelve line heights is read whole`() {
        val reader = fakeReader()

        // Nine glyphs and their gaps, 176 px of ink on a 16 px line: eleven line heights.
        PieceReader(reader::read).read(line("ABCDEFGHI"))

        assertEquals(listOf("ABCDEFGHI"), reader.asked)
    }
}

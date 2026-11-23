package com.yomu.pipeline.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    private val sixGlyphTops = listOf(10, 30, 50, 70, 90, 110)

    private fun darkInk(label: Char): Int = Color.rgb(4 + 4 * alphabet.indexOf(label), 0, 0)

    private fun lightInk(label: Char): Int = Color.rgb(255, 255, 251 - 4 * alphabet.indexOf(label))

    /**
     * Vertical text in a crop 160 px tall: one 16 px wide column per label, [labels] in reading order from 10 px off
     * the right edge, 26 px apart. A column holds a glyph [glyphHeight] tall at each of [glyphTops] for its label:
     * by default six 16 px glyphs with 4 px between them. [extra] glyphs are drawn too.
     */
    private fun columns(
        labels: String,
        extra: List<Glyph> = emptyList(),
        paper: Int = Color.WHITE,
        ink: (Char) -> Int = ::darkInk,
        glyphHeight: Int = 16,
        glyphTops: (Char) -> List<Int> = { sixGlyphTops }
    ): Bitmap {
        val width = 20 + 26 * labels.length
        val glyphs = labels.flatMapIndexed { column, label ->
            glyphTops(label).map { top -> Glyph(label, width - 26 - column * 26, top, 16, glyphHeight) }
        }
        return crop(width, 160, glyphs + extra, paper, ink)
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
    ) = FakeWholeReader(alphabet.associateBy(ink), confidences, unreadable, blank)

    @Test
    fun `narration of eight columns reaches the reader as four pairs, rightmost first`() {
        val reader = fakeReader()

        val read = PieceReader(reader::read).read(columns("ABCDEFGH"))

        assertEquals(listOf("AB", "CD", "EF", "GH"), reader.asked)
        assertEquals("ABCDEFGH", read?.text)
    }

    @Test
    fun `the joined text carries the lowest confidence among its pairs`() {
        val reader = fakeReader(confidences = mapOf("AB" to 0.98f, "CD" to 0.91f))

        val read = PieceReader(reader::read).read(columns("ABCDEFGH"))

        assertEquals(0.91f, read?.confidence)
    }

    @Test
    fun `a joined read is boxed as the whole crop`() {
        val read = PieceReader(fakeReader()::read).read(columns("ABCDEFGH"))

        assertArrayEquals(floatArrayOf(228f, 160f, 0f, 0f), read?.boundingBox, 0f)
    }

    @Test
    fun `one unreadable pair falls back to reading the box in one go`() {
        val reader = fakeReader(confidences = mapOf("ABCDEFGH" to 0.9f), unreadable = setOf("CD"))

        val read = PieceReader(reader::read).read(columns("ABCDEFGH"))

        assertEquals(listOf("AB", "CD", "ABCDEFGH"), reader.asked)
        assertEquals("ABCDEFGH" to 0.9f, read?.let { it.text to it.confidence })
    }

    @Test
    fun `one pair that decodes to nothing falls back to reading the box in one go`() {
        val reader = fakeReader(blank = setOf("CD"))

        val read = PieceReader(reader::read).read(columns("ABCDEFGH"))

        assertEquals(listOf("AB", "CD", "ABCDEFGH"), reader.asked)
        assertEquals("ABCDEFGH", read?.text)
    }

    @Test
    fun `a box read in one go comes back exactly as the reader returned it`() {
        val whole = OcrResult("ABCDEFG", 0.5f, floatArrayOf(1f))

        val read = PieceReader { whole }.read(columns("ABCDEFG"))

        assertSame(whole, read)
    }

    @Test
    fun `white columns on a black box split the same way as black on white`() {
        val reader = fakeReader(ink = ::lightInk)

        PieceReader(reader::read).read(columns("ABCDEFGH", paper = Color.BLACK, ink = ::lightInk))

        assertEquals(listOf("AB", "CD", "EF", "GH"), reader.asked)
    }

    @Test
    fun `an odd column out at the left is read on its own`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(columns("ABCDEFGHI"))

        assertEquals(listOf("AB", "CD", "EF", "GH", "I"), reader.asked)
    }

    @Test
    fun `a reading squeezed beside a column travels with that column`() {
        val reader = fakeReader()
        // B's reading, three 6 px kana in the gap to B's right, 1 px from B and 3 px from A.
        val furigana = (0 until 3).map { kana -> Glyph('b', 193, 12 + kana * 10, 6, 8) }

        PieceReader(reader::read).read(columns("ABCDEFGH", extra = furigana))

        assertEquals(listOf("ABb", "CD", "EF", "GH"), reader.asked)
    }

    @Test
    fun `a reading broken into two thin strips still travels with its column`() {
        val reader = fakeReader()
        // B's reading as two 3 px strips side by side in the gap to B's right, as kana of uneven widths leave it.
        val furigana = (0 until 3).map { kana -> Glyph('b', 193, 12 + kana * 10, 3, 8) } +
            (0 until 2).map { kana -> Glyph('b', 197, 14 + kana * 10, 3, 8) }

        PieceReader(reader::read).read(columns("ABCDEFGH", extra = furigana))

        assertEquals(listOf("ABb", "CD", "EF", "GH"), reader.asked)
    }

    @Test
    fun `a reading midway between two columns goes with the column on its left`() {
        val reader = fakeReader()
        // Three 4 px kana 3 px from C on their left and 3 px from B on their right.
        val furigana = (0 until 3).map { kana -> Glyph('c', 169, 12 + kana * 10, 4, 8) }

        PieceReader(reader::read).read(columns("ABCDEFGH", extra = furigana))

        assertEquals(listOf("AB", "CDc", "EF", "GH"), reader.asked)
    }

    @Test
    fun `a reading past the outermost column goes with that column`() {
        val reader = fakeReader()
        // A's reading in the margin to A's right, where no column stands beyond it.
        val furigana = (0 until 3).map { kana -> Glyph('a', 219, 12 + kana * 10, 4, 8) }

        PieceReader(reader::read).read(columns("ABCDEFGH", extra = furigana))

        assertEquals(listOf("ABa", "CD", "EF", "GH"), reader.asked)
    }

    @Test
    fun `readings beside every column, each broken in two, still join their own columns`() {
        val reader = fakeReader()
        // Two 3 px strips 1 px apart to the right of each column, so sixteen of the twenty-four strokes are narrow.
        val furigana = "ABCDEFGH".flatMapIndexed { column, label ->
            val gutter = 228 - 26 - column * 26 + 16
            listOf(Glyph(label.lowercaseChar(), gutter + 1, 10, 3, 116), Glyph(label.lowercaseChar(), gutter + 5, 10, 3, 116))
        }

        PieceReader(reader::read).read(columns("ABCDEFGH", extra = furigana))

        assertEquals(listOf("ABab", "CDcd", "EFef", "GHgh"), reader.asked)
    }

    @Test
    fun `a strip over three fifths as wide as the columns counts as a column of its own`() {
        val reader = fakeReader()
        // A 10 px strip in the margin left of G makes an eighth column.
        val strip = Glyph('g', 2, 10, 10, 116)

        PieceReader(reader::read).read(columns("ABCDEFG", extra = listOf(strip)))

        assertEquals(listOf("AB", "CD", "EF", "Gg"), reader.asked)
    }

    @Test
    fun `a strip under three fifths as wide as the columns is folded into the column beside it`() {
        val reader = fakeReader()
        // A 9 px strip in the margin left of G, which leaves seven columns once it joins G.
        val strip = Glyph('g', 3, 10, 9, 116)

        PieceReader(reader::read).read(columns("ABCDEFG", extra = listOf(strip)))

        assertEquals(listOf("ABCDEFGg"), reader.asked)
    }

    @Test
    fun `stray dots in a gutter do not count toward the column total`() {
        val reader = fakeReader()
        // Two 1 px specks side by side in the gap between A and B, which would make seven columns nine.
        val specks = listOf(167, 170).map { x -> Glyph('z', x, 40, 1, 12) }

        PieceReader(reader::read).read(columns("ABCDEFG", extra = specks))

        assertEquals(listOf("ABCDEFGz"), reader.asked)
    }

    @Test
    fun `a box of seven columns goes to the reader in one go`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(columns("ABCDEFG"))

        assertEquals(listOf("ABCDEFG"), reader.asked)
    }

    @Test
    fun `columns two and a half times as tall as they are wide are read in pairs`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(columns("ABCDEFGH", glyphHeight = 40, glyphTops = { listOf(10) }))

        assertEquals(listOf("AB", "CD", "EF", "GH"), reader.asked)
    }

    @Test
    fun `columns a pixel squatter than two and a half widths go to the reader in one go`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(columns("ABCDEFGH", glyphHeight = 39, glyphTops = { listOf(10) }))

        assertEquals(listOf("ABCDEFGH"), reader.asked)
    }

    @Test
    fun `eight columns of which half are tall are still read in pairs`() {
        val reader = fakeReader()
        val squat = "BDFH"

        PieceReader(reader::read).read(columns("ABCDEFGH", glyphTops = { if (it in squat) listOf(10) else sixGlyphTops }))

        assertEquals(listOf("AB", "CD", "EF", "GH"), reader.asked)
    }

    @Test
    fun `eight columns of which most are a single glyph tall go to the reader in one go`() {
        val reader = fakeReader()
        val squat = "ABDFH"

        PieceReader(reader::read).read(columns("ABCDEFGH", glyphTops = { if (it in squat) listOf(10) else sixGlyphTops }))

        assertEquals(listOf("ABCDEFGH"), reader.asked)
    }

    @Test
    fun `three rows of horizontal text are never sliced into vertical strips`() {
        val reader = fakeReader()

        PieceReader(reader::read).read(crop(176, 88, lines("ABC")))

        assertEquals(listOf("ABC"), reader.asked)
    }

    @Test
    fun `a blank band across the box as wide as the column gaps rules out column pairs`() {
        val reader = fakeReader()
        // The third and fourth glyphs of every column stand 10 px apart, as wide as the gaps between columns.
        val tops = listOf(10, 30, 50, 76, 96, 116)

        PieceReader(reader::read).read(columns("ABCDEFGH", glyphTops = { tops }))

        assertEquals(listOf("ABCDEFGH"), reader.asked)
    }
}

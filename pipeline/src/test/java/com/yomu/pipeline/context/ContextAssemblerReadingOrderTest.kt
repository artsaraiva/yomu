package com.yomu.pipeline.context

import android.graphics.RectF
import com.yomu.pipeline.bubble.Bubble
import com.yomu.pipeline.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContextAssemblerReadingOrderTest {

    private val assembler = ContextAssembler()

    @Test
    fun panelsSortedRightToLeft() {
        val leftPanelBubble = bubble(1, left = 0f, top = 0f, right = 100f, bottom = 100f)
        val rightPanelBubble = bubble(2, left = 200f, top = 0f, right = 300f, bottom = 100f)

        val result = assembler.assemble(
            bubbles = listOf(leftPanelBubble, rightPanelBubble),
            ocrResults = emptyMap(),
            pageWidth = 1000,
            pageHeight = 1000
        )

        assertEquals(listOf(2, 1), result.orderedBubbleIds())
    }

    @Test
    fun bubblesWithinPanelSortedTopToBottom() {
        val top = bubble(1, left = 0f, top = 0f, right = 100f, bottom = 30f)
        val middle = bubble(2, left = 0f, top = 40f, right = 100f, bottom = 70f)
        val bottom = bubble(3, left = 0f, top = 80f, right = 100f, bottom = 110f)

        val result = assembler.assemble(
            bubbles = listOf(bottom, top, middle),
            ocrResults = emptyMap(),
            pageWidth = 1000,
            pageHeight = 1000
        )

        assertEquals(listOf(1, 2, 3), result.orderedBubbleIds())
    }

    @Test
    fun rightHalfBubblesBeforeLeftHalfWithinPanel() {
        val leftHalf = bubble(1, left = 0f, top = 0f, right = 40f, bottom = 40f)
        val rightHalf = bubble(2, left = 60f, top = 100f, right = 100f, bottom = 140f)

        val result = assembler.assemble(
            bubbles = listOf(leftHalf, rightHalf),
            ocrResults = emptyMap(),
            pageWidth = 1000,
            pageHeight = 1000
        )

        assertEquals(listOf(2, 1), result.orderedBubbleIds())
    }

    @Test
    fun emptyBubblesReturnsEmpty() {
        val result = assembler.assemble(
            bubbles = emptyList(),
            ocrResults = emptyMap(),
            pageWidth = 1000,
            pageHeight = 1000
        )

        assertEquals(emptyList<Int>(), result.orderedBubbleIds())
    }

    @Test
    fun singleBubbleUnchanged() {
        val only = bubble(1, left = 10f, top = 10f, right = 50f, bottom = 50f)

        val result = assembler.assemble(
            bubbles = listOf(only),
            ocrResults = emptyMap(),
            pageWidth = 1000,
            pageHeight = 1000
        )

        assertEquals(listOf(1), result.orderedBubbleIds())
    }

    @Test
    fun textByBubbleIdPreservesOcrMappingWhenReadingOrderDiffers() {
        val leftTop = bubble(1, left = 0f, top = 0f, right = 40f, bottom = 40f)
        val rightBottom = bubble(2, left = 60f, top = 100f, right = 100f, bottom = 140f)

        val result = assembler.assemble(
            bubbles = listOf(leftTop, rightBottom),
            ocrResults = mapOf(
                1 to ocr("left text"),
                2 to ocr("right text")
            ),
            pageWidth = 1000,
            pageHeight = 1000
        )

        val block = result.blocks.single()
        assertEquals(listOf(2, 1), block.bubbles.map { it.id })
        assertEquals(listOf(1, 2), block.readingOrder)
        assertEquals("left text", block.textByBubbleId[1]?.text)
        assertEquals("right text", block.textByBubbleId[2]?.text)
    }

    private fun bubble(
        id: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Bubble = Bubble(
        id = id,
        boundingBox = RectF(left, top, right, bottom),
        confidence = 0.9f
    )

    private fun ocr(text: String): OcrResult = OcrResult(
        text = text,
        confidence = 1f,
        boundingBox = floatArrayOf(0f, 0f, 1f, 1f)
    )

    private fun PageContext.orderedBubbleIds(): List<Int> =
        blocks.flatMap { it.bubbles }.map { it.id }
}

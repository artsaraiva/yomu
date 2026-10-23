package com.yomu.pipeline.context

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import com.yomu.pipeline.bubble.Bubble
import com.yomu.pipeline.ocr.OcrResult
import kotlin.math.ceil

data class ConversationBlock(
    val blockId: Int,
    val bubbles: List<Bubble>,
    val texts: List<OcrResult>,
    val readingOrder: List<Int>,
    val textByBubbleId: Map<Int, OcrResult> = emptyMap()
)

data class PageContext(
    val blocks: List<ConversationBlock>,
    val pageWidth: Int,
    val pageHeight: Int
)

/**
 * Groups a page's bubbles into panels in manga reading order; the engine renders panel
 * boundaries as prompt markers (ADR-0002), never as call boundaries.
 *
 * Panels come from the drawn gutters. The page is cut along paper rows into tiers, read top to
 * bottom, then along paper columns into panels, read right to left, recursively. Bubble boxes
 * alone cannot find them: on balloon-dense-dialogue two bubbles of one panel sit further apart
 * than the gutters between tiers, and grouping by box gaps merged the whole page into one panel.
 *
 * Straight gutters only. A slanted gutter, or art crossing one, keeps those panels
 * together and their bubbles fall back to row order; trace panel borders if that misreads pages.
 */
class ContextAssembler {

    fun assemble(
        bubbles: List<Bubble>,
        ocrResults: Map<Int, OcrResult>,
        page: Bitmap
    ): PageContext {
        val paper = PaperMask(page)
        val panels = if (bubbles.isEmpty()) emptyList() else panelsInReadingOrder(paper, readingArea(paper, bubbles, page), bubbles)

        return PageContext(
            blocks = panels.mapIndexed { index, panel -> createBlock(index + 1, panel, ocrResults) },
            pageWidth = page.width,
            pageHeight = page.height
        )
    }

    /** The page, grown to hold every bubble when a panel bleeds past the page's paper margin. */
    private fun readingArea(paper: PaperMask, bubbles: List<Bubble>, page: Bitmap): Rect {
        val area = paper.pageBounds()
        val box = Rect()
        for (bubble in bubbles) {
            bubble.boundingBox.roundOut(box)
            area.union(box)
        }
        area.intersect(0, 0, page.width, page.height)
        return area
    }

    private fun panelsInReadingOrder(paper: PaperMask, region: Rect, bubbles: List<Bubble>): List<List<Bubble>> {
        if (bubbles.size < 2) return listOf(bubbles)

        val tiers = splitAtGutters(bubbles, { it.top }, { it.bottom }, region.top, region.bottom) { y ->
            paper.isPaperRow(y, region)
        }
        if (tiers.size > 1) {
            return tiers.flatMap { tier ->
                panelsInReadingOrder(paper, Rect(region.left, tier.from, region.right, tier.to), tier.bubbles)
            }
        }

        val columns = splitAtGutters(bubbles, { it.left }, { it.right }, region.left, region.right) { x ->
            paper.isPaperColumn(x, region)
        }
        if (columns.size > 1) {
            return columns.asReversed().flatMap { column ->
                panelsInReadingOrder(paper, Rect(column.from, region.top, column.to, region.bottom), column.bubbles)
            }
        }

        return listOf(inPanelOrder(bubbles))
    }

    /** Rows of vertically overlapping bubbles, top to bottom, each read right to left. */
    private fun inPanelOrder(bubbles: List<Bubble>): List<Bubble> =
        splitAtGutters(bubbles, { it.top }, { it.bottom }, 0, Int.MAX_VALUE, isGutter = { true })
            .flatMap { row -> row.bubbles.sortedByDescending { it.boundingBox.centerX() } }

    private class Strip(val from: Int, val to: Int, val bubbles: List<Bubble>)

    /**
     * Cuts [bubbles] along one axis wherever a [isGutter] line in [from, to) falls in the gap
     * between them. Each strip runs from the previous cut to the next.
     */
    private fun splitAtGutters(
        bubbles: List<Bubble>,
        start: (RectF) -> Float,
        end: (RectF) -> Float,
        from: Int,
        to: Int,
        isGutter: (Int) -> Boolean
    ): List<Strip> {
        val sorted = bubbles.sortedBy { start(it.boundingBox) }
        val strips = mutableListOf<Strip>()
        var stripFrom = from
        var group = mutableListOf(sorted.first())
        var reach = end(sorted.first().boundingBox)

        for (bubble in sorted.drop(1)) {
            val gap = maxOf(ceil(reach).toInt(), from) until minOf(start(bubble.boundingBox).toInt(), to)
            val cut = gap.firstOrNull(isGutter)
            if (cut != null) {
                strips.add(Strip(stripFrom, cut, group))
                stripFrom = cut
                group = mutableListOf()
            }
            group.add(bubble)
            reach = maxOf(reach, end(bubble.boundingBox))
        }
        strips.add(Strip(stripFrom, to, group))
        return strips
    }

    private fun createBlock(
        blockId: Int,
        bubbles: List<Bubble>,
        ocrResults: Map<Int, OcrResult>
    ): ConversationBlock {
        val textByBubbleId = bubbles.mapNotNull { bubble ->
            ocrResults[bubble.id]?.let { result -> bubble.id to result }
        }.toMap()
        val texts = bubbles.mapNotNull { textByBubbleId[it.id] }
        // bubbles already arrive in reading order from panelsInReadingOrder.
        val readingOrder = bubbles.map { it.id }

        return ConversationBlock(
            blockId = blockId,
            bubbles = bubbles,
            texts = texts,
            readingOrder = readingOrder,
            textByBubbleId = textByBubbleId
        )
    }
}

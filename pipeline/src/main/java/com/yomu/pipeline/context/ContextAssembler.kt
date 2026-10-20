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

// Gutters are paper white; art and screentone fall below it. Anything from 180 to 235 split the
// three fixture pages identically (#367).
private const val PAPER_LUMINANCE = 200
// Rows and columns that are mostly paper belong to the page. The reader's letterbox does not, nor
// does the floating button drawn over it, whose rows would otherwise stop every column gutter.
private const val PAGE_PAPER_FRACTION = 0.5f

/**
 * Groups a page's bubbles into panels in manga reading order; the engine renders panel
 * boundaries as prompt markers (ADR-0002), never as call boundaries.
 *
 * Panels come from the drawn gutters. The page is cut along paper rows into tiers, read top to
 * bottom, then along paper columns into panels, read right to left, recursively. Bubble boxes
 * alone cannot find them: on balloon-dense-dialogue two bubbles of one panel sit further apart
 * than the gutters between tiers, and grouping by box gaps merged the whole page into one panel.
 *
 * ponytail: straight gutters only. A slanted gutter, or art crossing one, keeps those panels
 * together and their bubbles fall back to row order; trace panel borders if that misreads pages.
 */
class ContextAssembler {

    fun assemble(
        bubbles: List<Bubble>,
        ocrResults: Map<Int, OcrResult>,
        page: Bitmap
    ): PageContext {
        val paper = PaperMask(page)
        val panels = if (bubbles.isEmpty()) emptyList() else panelsInReadingOrder(paper, paper.pageBounds(bubbles), bubbles)

        return PageContext(
            blocks = panels.mapIndexed { index, panel -> createBlock(index + 1, panel, ocrResults) },
            pageWidth = page.width,
            pageHeight = page.height
        )
    }

    private fun panelsInReadingOrder(paper: PaperMask, region: Rect, bubbles: List<Bubble>): List<List<Bubble>> {
        if (bubbles.size < 2) return listOf(bubbles)

        val tiers = split(bubbles, { it.top }, { it.bottom }, region.top, region.bottom) { y ->
            paper.isPaperRow(y, region.left, region.right)
        }
        if (tiers.size > 1) {
            return tiers.flatMap { tier ->
                panelsInReadingOrder(paper, Rect(region.left, tier.from, region.right, tier.to), tier.bubbles)
            }
        }

        val columns = split(bubbles, { it.left }, { it.right }, region.left, region.right) { x ->
            paper.isPaperColumn(x, region.top, region.bottom)
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
        split(bubbles, { it.top }, { it.bottom }, 0, Int.MAX_VALUE) { true }
            .flatMap { row -> row.bubbles.sortedByDescending { it.boundingBox.centerX() } }

    private class Strip(val from: Int, val to: Int, val bubbles: List<Bubble>)

    /**
     * Cuts [bubbles] along one axis wherever a [isGutter] line in [from, to) falls in the gap
     * between them. Each strip runs from the previous cut to the next.
     */
    private fun split(
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

private class PaperMask(page: Bitmap) {
    private val width = page.width
    private val height = page.height
    private val paper = BooleanArray(width * height).also { mask ->
        val row = IntArray(width)
        for (y in 0 until height) {
            page.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val p = row[x]
                val luminance = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                mask[y * width + x] = luminance >= PAPER_LUMINANCE
            }
        }
    }

    fun isPaperRow(y: Int, left: Int, right: Int): Boolean =
        paperCount(y * width + left, 1, right - left) == right - left

    fun isPaperColumn(x: Int, top: Int, bottom: Int): Boolean =
        paperCount(top * width + x, width, bottom - top) == bottom - top

    /** The page inside the frame, grown to hold every bubble when a panel bleeds off its edge. */
    fun pageBounds(bubbles: List<Bubble>): Rect {
        val bounds = mostlyPaperBounds()
        for (bubble in bubbles) {
            val box = bubble.boundingBox
            bounds.union(box.left.toInt(), box.top.toInt(), ceil(box.right).toInt(), ceil(box.bottom).toInt())
        }
        bounds.intersect(0, 0, width, height)
        return bounds
    }

    private fun mostlyPaperBounds(): Rect {
        val rows = (0 until height).filter { y -> paperCount(y * width, 1, width) > width * PAGE_PAPER_FRACTION }
        if (rows.isEmpty()) return Rect()
        val top = rows.first()
        val bottom = rows.last() + 1
        val columns = (0 until width).filter { x ->
            paperCount(top * width + x, width, bottom - top) > (bottom - top) * PAGE_PAPER_FRACTION
        }
        if (columns.isEmpty()) return Rect()
        return Rect(columns.first(), top, columns.last() + 1, bottom)
    }

    private fun paperCount(first: Int, step: Int, length: Int): Int {
        var count = 0
        for (i in 0 until length) if (paper[first + i * step]) count++
        return count
    }
}

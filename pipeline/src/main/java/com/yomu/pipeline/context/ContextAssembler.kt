package com.yomu.pipeline.context

import android.graphics.RectF
import com.yomu.pipeline.bubble.Bubble
import com.yomu.pipeline.ocr.OcrResult

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

class ContextAssembler {

    companion object {
        private const val PANEL_GAP_THRESHOLD = 0.1f
        private const val VERTICAL_GAP_THRESHOLD = 0.05f
    }

    internal val panels = mutableListOf<RectF>()

    /**
     * The bubbles each panel was grown from, parallel to [panels].
     *
     * [detectPanels] already partitions the page: it walks bubbles left to right and starts a new
     * panel whenever one does not join the current run, so every bubble belongs to exactly one run.
     * Re-deriving membership from the panel rectangles afterwards does not reproduce that partition
     * — panels grow along X and a wide panel's span can contain a lower, narrower panel's bubbles —
     * so a bubble landed in several panels, and the page-level prompt carried duplicate `[id]` lines
     * for it. Keeping the partition is the fix; the rectangles stay for ordering and mid-line splits.
     */
    private val panelBubbles = mutableListOf<List<Bubble>>()

    fun assemble(
        bubbles: List<Bubble>,
        ocrResults: Map<Int, OcrResult>,
        pageWidth: Int,
        pageHeight: Int
    ): PageContext {
        panels.clear()
        panelBubbles.clear()

        detectPanels(bubbles, pageWidth, pageHeight)

        val panelGroups = groupBubblesByPanel()

        val blocks = panelGroups.mapIndexed { index, group ->
            createBlock(index + 1, group, ocrResults)
        }

        return PageContext(
            blocks = blocks,
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )
    }

    internal fun detectPanels(bubbles: List<Bubble>, pageWidth: Int, pageHeight: Int) {
        if (bubbles.isEmpty()) return

        val panelGap = (pageWidth * PANEL_GAP_THRESHOLD).toInt()
        val verticalGap = (pageHeight * VERTICAL_GAP_THRESHOLD).toInt()

        val sortedByX = bubbles.sortedBy { it.boundingBox.left }
        var currentPanel = RectF(sortedByX.first().boundingBox)
        var currentBubbles = mutableListOf(sortedByX.first())

        for (bubble in sortedByX.drop(1)) {
            val gap = bubble.boundingBox.left - currentPanel.right
            val vertOverlap = bubble.boundingBox.top < currentPanel.bottom + verticalGap

            if (gap < panelGap && vertOverlap) {
                currentPanel = RectF(
                    minOf(currentPanel.left, bubble.boundingBox.left),
                    minOf(currentPanel.top, bubble.boundingBox.top),
                    maxOf(currentPanel.right, bubble.boundingBox.right),
                    maxOf(currentPanel.bottom, bubble.boundingBox.bottom)
                )
                currentBubbles.add(bubble)
            } else {
                panels.add(currentPanel)
                panelBubbles.add(currentBubbles)
                currentPanel = RectF(bubble.boundingBox)
                currentBubbles = mutableListOf(bubble)
            }
        }
        panels.add(currentPanel)
        panelBubbles.add(currentBubbles)
    }

    /**
     * Group bubbles into panels in manga reading order: panels right-to-left, and within a panel
     * top-to-bottom with the right half read before the left. One group per non-empty panel; the
     * engine renders panel boundaries as prompt markers (ADR-0002), never as call boundaries.
     */
    internal fun groupBubblesByPanel(): List<List<Bubble>> =
        panels.indices.sortedByDescending { panels[it].left }.mapNotNull { index ->
            val panel = panels[index]
            val bubblesInPanel = panelBubbles[index].sortedBy { it.boundingBox.top }

            val midX = panel.centerX()
            val rightBubbles = bubblesInPanel.filter { it.boundingBox.centerX() >= midX }
            val leftBubbles = bubblesInPanel.filter { it.boundingBox.centerX() < midX }

            (rightBubbles + leftBubbles).takeIf { it.isNotEmpty() }
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
        // bubbles already arrive in reading order from groupBubblesByPanel.
        val readingOrder = bubbles.map { it.id }

        return ConversationBlock(
            blockId = blockId,
            bubbles = bubbles,
            texts = texts,
            readingOrder = readingOrder,
            textByBubbleId = textByBubbleId
        )
    }

    fun reset() {
        panels.clear()
        panelBubbles.clear()
    }
}

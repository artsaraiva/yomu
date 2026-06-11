package com.yomu.pipeline.context

import android.graphics.RectF
import com.yomu.pipeline.bubble.Bubble
import com.yomu.pipeline.ocr.OcrResult

data class ConversationBlock(
    val blockId: Int,
    val bubbles: List<Bubble>,
    val texts: List<OcrResult>,
    val readingOrder: List<Int>
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

    private val panels = mutableListOf<RectF>()

    fun assemble(
        bubbles: List<Bubble>,
        ocrResults: Map<Int, OcrResult>,
        pageWidth: Int,
        pageHeight: Int
    ): PageContext {
        panels.clear()

        detectPanels(bubbles, pageWidth, pageHeight)

        val panelBubbles = assignBubblesToPanels(bubbles)

        val orderedBubbles = determineReadingOrder(panelBubbles)

        val blocks = buildConversationBlocks(orderedBubbles, ocrResults)

        return PageContext(
            blocks = blocks,
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )
    }

    private fun detectPanels(bubbles: List<Bubble>, pageWidth: Int, pageHeight: Int) {
        if (bubbles.isEmpty()) return

        val panelGap = (pageWidth * PANEL_GAP_THRESHOLD).toInt()
        val verticalGap = (pageHeight * VERTICAL_GAP_THRESHOLD).toInt()

        val sortedByX = bubbles.sortedBy { it.boundingBox.left }
        var currentPanel = RectF(sortedByX.first().boundingBox)

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
            } else {
                panels.add(currentPanel)
                currentPanel = RectF(bubble.boundingBox)
            }
        }
        panels.add(currentPanel)
    }

    private fun assignBubblesToPanels(bubbles: List<Bubble>): List<Bubble> {
        val rightToLeftPanels = panels.sortedByDescending { it.left }

        val assigned = mutableListOf<Bubble>()
        for (panel in rightToLeftPanels) {
            val bubblesInPanel = bubbles
                .filter { it.boundingBox.centerX() >= panel.left && it.boundingBox.centerX() <= panel.right }
                .sortedBy { it.boundingBox.top }

            val midX = panel.centerX()
            val rightBubbles = bubblesInPanel.filter { it.boundingBox.centerX() >= midX }
            val leftBubbles = bubblesInPanel.filter { it.boundingBox.centerX() < midX }

            assigned.addAll(rightBubbles)
            assigned.addAll(leftBubbles)
        }

        return assigned
    }

    private fun determineReadingOrder(bubbles: List<Bubble>): List<Bubble> {
        return bubbles
    }

    private fun buildConversationBlocks(
        bubbles: List<Bubble>,
        ocrResults: Map<Int, OcrResult>
    ): List<ConversationBlock> {
        val blocks = mutableListOf<ConversationBlock>()

        var currentBlock = mutableListOf<Bubble>()
        var blockId = 1

        for (bubble in bubbles) {
            currentBlock.add(bubble)

            if (currentBlock.size >= 4) {
                blocks.add(createBlock(blockId++, currentBlock, ocrResults))
                currentBlock = mutableListOf()
            }
        }

        if (currentBlock.isNotEmpty()) {
            blocks.add(createBlock(blockId, currentBlock, ocrResults))
        }

        return blocks
    }

    private fun createBlock(
        blockId: Int,
        bubbles: List<Bubble>,
        ocrResults: Map<Int, OcrResult>
    ): ConversationBlock {
        val texts = bubbles.mapNotNull { ocrResults[it.id] }
        val readingOrder = bubbles.sortedBy { it.boundingBox.top }
            .map { it.id }

        return ConversationBlock(
            blockId = blockId,
            bubbles = bubbles,
            texts = texts,
            readingOrder = readingOrder
        )
    }

    fun reset() {
        panels.clear()
    }
}

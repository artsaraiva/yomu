package com.yomu.app.overlay

import com.yomu.pipeline.typesetting.TypesetBubble

data class OverlayBubbleState(
    val bubbleId: Int,
    val bounds: OverlayBounds,
    val ocrText: String,
    val typeset: TypesetBubble? = null
)

fun List<OverlayBubbleState>.withTypeset(bubble: TypesetBubble): List<OverlayBubbleState> =
    map { if (it.bubbleId == bubble.bubbleId) it.copy(typeset = bubble) else it }

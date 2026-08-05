package com.yomu.app.overlay

data class OverlayBubbleState(
    val bubbleId: Int,
    val bounds: OverlayBounds,
    val ocrText: String,
    val translatedText: String? = null
) {
    val isTranslated: Boolean get() = translatedText != null
}

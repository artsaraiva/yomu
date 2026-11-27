package com.yomu.app.overlay

/** A window's position and size in the pixels [overlayControlSize] measures. */
internal data class PanelFrame(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Where the reading panel sits (#551). In portrait it spans the bottom edge and never rises past half the
 * screen, so the page stays visible while its text size changes. In landscape it takes [sideWidth] on the
 * side the floating button is on, at [buttonX]. A panel taller than its space scrolls.
 * [contentHeight] measures the panel's content at the width it is given.
 */
internal fun readingPanelFrame(
    availableWidth: Int,
    availableHeight: Int,
    buttonX: Int,
    margin: Int,
    sideWidth: Int,
    contentHeight: (width: Int) -> Int
): PanelFrame {
    val portrait = availableHeight >= availableWidth
    val width = (if (portrait) availableWidth - margin * 2 else sideWidth.coerceAtMost(availableWidth / 2 - margin)).coerceAtLeast(1)
    val room = if (portrait) availableHeight / 2 - margin else availableHeight - margin * 2
    val height = contentHeight(width).coerceAtMost(room).coerceAtLeast(1)
    val x = if (portrait || buttonX < availableWidth / 2) margin else availableWidth - width - margin
    return PanelFrame(x, availableHeight - height - margin, width, height)
}

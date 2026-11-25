package com.yomu.app.overlay

import android.graphics.Path
import com.yomu.pipeline.typesetting.TypesetBubble

private const val CORNER_RADIUS_FRACTION = 0.12f

/**
 * The area a typeset bubble paints and clips its text to, in canvas space: a rounded box, or for a
 * bubble shaped to its balloon, the balloon's oval (or rectangle) plus its glyph box, so the
 * Japanese stays hidden where the oval is narrower than the glyphs.
 */
internal fun bubbleOutline(
    bubble: TypesetBubble,
    bounds: OverlayBounds,
    params: OverlayCoordinateMapper.MapParams
): Path {
    val shape = bubble.shape
    val cover = bubble.coverBox
    if (shape == null || cover == null) return roundedBox(bounds)
    return Path().apply {
        if (shape.ellipse) addOval(bounds.left, bounds.top, bounds.right, bounds.bottom, Path.Direction.CW)
        else addRect(bounds.left, bounds.top, bounds.right, bounds.bottom, Path.Direction.CW)
        op(roundedBox(OverlayCoordinateMapper.map(cover, params)), Path.Op.UNION)
    }
}

private fun roundedBox(bounds: OverlayBounds): Path {
    val radius = minOf(bounds.width(), bounds.height()) * CORNER_RADIUS_FRACTION
    return Path().apply {
        addRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius, radius, Path.Direction.CW)
    }
}

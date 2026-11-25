package com.yomu.pipeline.bubble

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

// Spike #471: the balloon box as a second opinion on the render box and as the shape lines follow.

/** Where a text box's render area came from. */
enum class ShapeSource {
    /** Flood fill inside the balloon box found a closed, round interior: lines follow its ellipse. */
    FILL_ELLIPSE,
    /** Flood fill found a closed interior that fills its bounds (a caption box): lines use the whole rectangle. */
    FILL_RECT,
    /** No closed interior inside the balloon box: the ellipse inscribed in it (comic-translate's fallback). */
    BALLOON_ELLIPSE
}

/** Today's flood fill, labelled: what [bubbleRenderBoxes] did for one text box. */
enum class TodayOutcome { GROWN, UNCHANGED, NONE, REACH }

/** An axis-aligned ellipse or rectangle in page pixels, {left, top, right, bottom}. */
data class BalloonShape(val bounds: FloatArray, val ellipse: Boolean, val source: ShapeSource)

/** What the spike decided for one text box. [shape] is null when it keeps today's render box. */
data class RenderPlan(
    val today: TodayOutcome,
    val todayBox: FloatArray,
    val shape: BalloonShape?,
    /** Why no shape: "no-host", "reach" (the shape meets another glyph box). Null when [shape] is set. */
    val noShapeReason: String?,
    /** Fill area over its bounding box, 0.785 for an ellipse and 1 for a rectangle; null without a fill. */
    val fillRatio: Float?
)

// A filled region covering more of its bounds than this is a rectangle (caption box); an ellipse covers π/4.
private const val RECT_FILL_RATIO = 0.90f
// The fallback ellipse sits inside the balloon box's outline stroke.
private const val BALLOON_OUTLINE_INSET = 0.06f
// Balloon boxes hug the outline's outer edge; the window adds a few pixels so the outline is inside it.
private const val WINDOW_PAD = 4

fun renderPlans(
    bitmap: Bitmap,
    textBoxes: Map<Int, FloatArray>,
    balloonBoxes: Map<Int, FloatArray?>
): Map<Int, RenderPlan> {
    val todayRaw = textBoxes.mapValues { (_, box) -> todayRenderBox(bitmap, box) }
    return textBoxes.mapValues { (id, glyph) ->
        val (grown, found) = todayRaw.getValue(id)
        val reaches = textBoxes.any { (other, box) -> other != id && intersects(grown, box) }
        val today = when {
            !found -> TodayOutcome.NONE
            reaches -> TodayOutcome.REACH
            grown.contentEquals(glyph) -> TodayOutcome.UNCHANGED
            else -> TodayOutcome.GROWN
        }
        val todayBox = if (reaches) glyph else grown
        val balloon = balloonBoxes[id]
            ?: return@mapValues RenderPlan(today, todayBox, null, "no-host", null)
        val (shape, ratio) = balloonShape(bitmap, glyph, balloon)
        val meetsOther = textBoxes.any { (other, box) -> other != id && shapeMeets(shape, box) }
        if (meetsOther) RenderPlan(today, todayBox, null, "reach", ratio)
        else RenderPlan(today, todayBox, shape, null, ratio)
    }
}

private fun todayRenderBox(bitmap: Bitmap, textBox: FloatArray): Pair<FloatArray, Boolean> {
    val margin = min(max(textBox[2] - textBox[0], textBox[3] - textBox[1]), bitmap.width * 0.25f).toInt()
    val window = window(bitmap, textBox[0].toInt() - margin, textBox[1].toInt() - margin, textBox[2].toInt() + margin, textBox[3].toInt() + margin)
    val inWindow = floatArrayOf(textBox[0] - window.left, textBox[1] - window.top, textBox[2] - window.left, textBox[3] - window.top)
    val interior = findBubbleInterior(window.pixels, window.width, window.height, inWindow) ?: return textBox to false
    return floatArrayOf(interior[0] + window.left, interior[1] + window.top, interior[2] + window.left, interior[3] + window.top) to true
}

/** Flood fill bounded by the balloon box: a region touching the window edge leaked. Ellipse inscribed in the balloon box otherwise. */
private fun balloonShape(bitmap: Bitmap, glyph: FloatArray, balloon: FloatArray): Pair<BalloonShape, Float?> {
    val window = window(
        bitmap,
        balloon[0].toInt() - WINDOW_PAD, balloon[1].toInt() - WINDOW_PAD,
        balloon[2].toInt() + WINDOW_PAD, balloon[3].toInt() + WINDOW_PAD
    )
    val inWindow = floatArrayOf(glyph[0] - window.left, glyph[1] - window.top, glyph[2] - window.left, glyph[3] - window.top)
    val region = interiorRegion(window.pixels, window.width, window.height, inWindow)
    if (region == null) {
        val insetX = (balloon[2] - balloon[0]) * BALLOON_OUTLINE_INSET
        val insetY = (balloon[3] - balloon[1]) * BALLOON_OUTLINE_INSET
        val bounds = floatArrayOf(balloon[0] + insetX, balloon[1] + insetY, balloon[2] - insetX, balloon[3] - insetY)
        return BalloonShape(bounds, ellipse = true, source = ShapeSource.BALLOON_ELLIPSE) to null
    }
    val (box, count) = region
    val ratio = count.toFloat() / ((box[2] - box[0]) * (box[3] - box[1]))
    val bounds = floatArrayOf(
        (box[0] + window.left).toFloat(), (box[1] + window.top).toFloat(),
        (box[2] + window.left).toFloat(), (box[3] + window.top).toFloat()
    )
    val rect = ratio > RECT_FILL_RATIO
    return BalloonShape(bounds, ellipse = !rect, source = if (rect) ShapeSource.FILL_RECT else ShapeSource.FILL_ELLIPSE) to ratio
}

private class Window(val pixels: IntArray, val left: Int, val top: Int, val width: Int, val height: Int)

private fun window(bitmap: Bitmap, l: Int, t: Int, r: Int, b: Int): Window {
    val left = l.coerceIn(0, bitmap.width - 1)
    val top = t.coerceIn(0, bitmap.height - 1)
    val right = r.coerceIn(left + 1, bitmap.width)
    val bottom = b.coerceIn(top + 1, bitmap.height)
    val width = right - left
    val height = bottom - top
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, left, top, width, height)
    return Window(pixels, left, top, width, height)
}

/** Whether [box] reaches into [shape]: an ellipse by its nearest point, a rectangle by overlap. */
internal fun shapeMeets(shape: BalloonShape, box: FloatArray): Boolean {
    val b = shape.bounds
    if (!intersectsBox(b, box)) return false
    if (!shape.ellipse) return true
    val cx = (b[0] + b[2]) / 2f
    val cy = (b[1] + b[3]) / 2f
    val a = (b[2] - b[0]) / 2f
    val bb = (b[3] - b[1]) / 2f
    val nx = cx.coerceIn(box[0], box[2])
    val ny = cy.coerceIn(box[1], box[3])
    val dx = (nx - cx) / a
    val dy = (ny - cy) / bb
    return dx * dx + dy * dy < 1f
}

private fun intersectsBox(a: FloatArray, b: FloatArray): Boolean =
    a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]

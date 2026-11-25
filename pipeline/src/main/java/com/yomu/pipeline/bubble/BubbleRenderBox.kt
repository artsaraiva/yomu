package com.yomu.pipeline.bubble

import android.graphics.Bitmap

// The detector boxes the Japanese glyphs, not the bubble around them (ADR-0007), so a short
// vertical line like はい gives a tall sliver English cannot be typeset into. Flood-filling the
// bubble's white interior around the glyphs recovers the space the letterer actually had.

// Bubble interiors are paper white; light screentone backgrounds (~220) must not pass, or text
// lettered straight onto a grey panel fills out across the artwork.
private const val WHITE_LUMINANCE = 235
// Thickens dark strokes so a hairline gap in a bubble outline does not let the fill escape.
private const val OUTLINE_CLOSE_RADIUS = 2
// Detector boxes sit inset inside the glyphs; seeding just outside the box still reaches the
// interior when dense glyphs leave no free pixel inside it.
private const val SEED_PAD = 3
// The rectangle inscribed in an ellipse is 1/sqrt(2) of its bounding box on each side.
private const val INSCRIBED_FRACTION = 0.707f
// Fixed search cap, a fraction of page width; keeps the per-bubble pixel copy small.
// A bubble reaching further than this from its glyphs falls back to the detector box.
private const val MAX_SEARCH_MARGIN_OF_PAGE_WIDTH = 0.25f
// An ellipse covers pi/4 (0.785) of its bounds; a caption box covering more than this is a rectangle.
private const val RECT_FILL_RATIO = 0.90f
// Balloon boxes hug the outline's outer edge; the window adds a few pixels so the outline is inside it.
private const val BALLOON_WINDOW_PAD = 4
// The fallback ellipse sits inside the balloon box's outline stroke (#471).
private const val BALLOON_OUTLINE_INSET = 0.06f

/** The balloon interior a translation's lines can follow: its bounds, {left, top, right, bottom}, as an [ellipse] or a rectangle. */
data class BalloonShape(val bounds: FloatArray, val ellipse: Boolean)

/**
 * Where one bubble's translation goes: [box] is the rectangle today's typeset fills, [shape] the
 * balloon interior around it (null when there is none to follow), [glyphBox] the detector box.
 */
data class RenderArea(val box: FloatArray, val shape: BalloonShape?, val glyphBox: FloatArray)

/** What [findBubbleInterior] found, in window coordinates. */
internal data class BubbleInterior(val renderBox: FloatArray, val shape: BalloonShape)

/**
 * The area each bubble's translation is typeset into, keyed like [textBoxes] (detector boxes,
 * {left, top, right, bottom} in page pixels). Its box is the rectangle inside the white bubble
 * interior around the glyphs, never smaller than the detector box, and its shape is that interior.
 *
 * A text box with a detector balloon in [balloonBoxes] is filled inside that balloon box only, and
 * gets the ellipse inscribed in the balloon box when no interior is found there (an open balloon).
 * One without a balloon searches around its glyphs and keeps its detector box with no shape when
 * no enclosed interior is found (text on art, screentone bubbles, open bubbles). Either way it
 * keeps its detector box when its box or shape would reach another bubble's glyphs, which would
 * paint two translations over each other (#88).
 */
fun bubbleRenderAreas(
    bitmap: Bitmap,
    textBoxes: Map<Int, FloatArray>,
    balloonBoxes: Map<Int, FloatArray> = emptyMap()
): Map<Int, RenderArea> = textBoxes.mapValues { (id, glyphs) ->
    val balloon = balloonBoxes[id]
    val interior = if (balloon != null) balloonInterior(bitmap, glyphs, balloon) else bubbleInterior(bitmap, glyphs)
    val reachesOther = interior != null && textBoxes.any { (otherId, other) ->
        otherId != id && (intersects(interior.renderBox, other) || shapeMeets(interior.shape, other))
    }
    if (interior == null || reachesOther) RenderArea(glyphs, null, glyphs)
    else RenderArea(interior.renderBox, interior.shape, glyphs)
}

/** [findBubbleInterior] around [textBox] on the page, in page coordinates. */
private fun bubbleInterior(bitmap: Bitmap, textBox: FloatArray): BubbleInterior? {
    val margin = minOf(
        maxOf(textBox[2] - textBox[0], textBox[3] - textBox[1]),
        bitmap.width * MAX_SEARCH_MARGIN_OF_PAGE_WIDTH
    ).toInt()
    val window = Window(
        bitmap,
        textBox[0].toInt() - margin, textBox[1].toInt() - margin,
        textBox[2].toInt() + margin, textBox[3].toInt() + margin
    )
    return findBubbleInterior(window.pixels, window.width, window.height, window.local(textBox))?.let(window::onPage)
}

/**
 * [findBubbleInterior] inside [balloon], where a region touching the balloon box has leaked out of
 * an open balloon. A region that misses the glyph box's centre or is smaller than the glyph box is
 * a glyph counter (口, あ), not the balloon. Without an interior, the ellipse inscribed in [balloon].
 */
private fun balloonInterior(bitmap: Bitmap, textBox: FloatArray, balloon: FloatArray): BubbleInterior {
    val window = Window(
        bitmap,
        balloon[0].toInt() - BALLOON_WINDOW_PAD, balloon[1].toInt() - BALLOON_WINDOW_PAD,
        balloon[2].toInt() + BALLOON_WINDOW_PAD, balloon[3].toInt() + BALLOON_WINDOW_PAD
    )
    val glyphs = window.local(textBox)
    val interior = findBubbleInterior(window.pixels, window.width, window.height, glyphs)
        ?.takeIf { holdsGlyphBox(it.shape.bounds, glyphs) }
    if (interior != null) return window.onPage(interior)

    val insetX = (balloon[2] - balloon[0]) * BALLOON_OUTLINE_INSET
    val insetY = (balloon[3] - balloon[1]) * BALLOON_OUTLINE_INSET
    val bounds = floatArrayOf(balloon[0] + insetX, balloon[1] + insetY, balloon[2] - insetX, balloon[3] - insetY)
    return BubbleInterior(renderBoxIn(bounds, textBox), BalloonShape(bounds, ellipse = true))
}

private fun holdsGlyphBox(region: FloatArray, glyphs: FloatArray): Boolean {
    val cx = (glyphs[0] + glyphs[2]) / 2f
    val cy = (glyphs[1] + glyphs[3]) / 2f
    return cx > region[0] && cx < region[2] && cy > region[1] && cy < region[3] &&
        (region[2] - region[0]) * (region[3] - region[1]) >= (glyphs[2] - glyphs[0]) * (glyphs[3] - glyphs[1])
}

/** A page region copied out for a fill, clamped to the page. */
private class Window(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
    val left = left.coerceIn(0, bitmap.width - 1)
    val top = top.coerceIn(0, bitmap.height - 1)
    val width = right.coerceIn(this.left + 1, bitmap.width) - this.left
    val height = bottom.coerceIn(this.top + 1, bitmap.height) - this.top
    val pixels = IntArray(width * height).also { bitmap.getPixels(it, 0, width, this.left, this.top, width, height) }

    fun local(box: FloatArray) = floatArrayOf(box[0] - left, box[1] - top, box[2] - left, box[3] - top)
    private fun page(box: FloatArray) = floatArrayOf(box[0] + left, box[1] + top, box[2] + left, box[3] + top)
    fun onPage(interior: BubbleInterior) =
        BubbleInterior(page(interior.renderBox), interior.shape.copy(bounds = page(interior.shape.bounds)))
}

/**
 * Pure core of [bubbleRenderAreas] over an ARGB search window; boxes are in window coordinates.
 * Null means no enclosed interior was found.
 */
internal fun findBubbleInterior(pixels: IntArray, width: Int, height: Int, textBox: FloatArray): BubbleInterior? {
    val fillable = fillablePixels(pixels, width, height)
    val (interior, spanArea) = interiorAround(fillable, width, height, textBox) ?: return null

    val bounds = FloatArray(4) { interior[it].toFloat() }
    val fillRatio = spanArea.toFloat() / ((interior[2] - interior[0]) * (interior[3] - interior[1]))
    return BubbleInterior(renderBoxIn(bounds, textBox), BalloonShape(bounds, ellipse = fillRatio <= RECT_FILL_RATIO))
}

/** The rectangle inscribed in the ellipse of [bounds], grown to hold [textBox]. */
private fun renderBoxIn(bounds: FloatArray, textBox: FloatArray): FloatArray {
    val insetX = (bounds[2] - bounds[0]) * (1 - INSCRIBED_FRACTION) / 2f
    val insetY = (bounds[3] - bounds[1]) * (1 - INSCRIBED_FRACTION) / 2f
    return floatArrayOf(
        minOf(bounds[0] + insetX, textBox[0]),
        minOf(bounds[1] + insetY, textBox[1]),
        maxOf(bounds[2] - insetX, textBox[2]),
        maxOf(bounds[3] - insetY, textBox[3])
    )
}

/** White pixels, minus a [OUTLINE_CLOSE_RADIUS] halo around every dark one. */
private fun fillablePixels(pixels: IntArray, width: Int, height: Int): BooleanArray {
    val fillable = BooleanArray(pixels.size) { true }
    for (y in 0 until height) for (x in 0 until width) {
        if (luminance(pixels[y * width + x]) > WHITE_LUMINANCE) continue
        for (ny in maxOf(0, y - OUTLINE_CLOSE_RADIUS)..minOf(height - 1, y + OUTLINE_CLOSE_RADIUS)) {
            for (nx in maxOf(0, x - OUTLINE_CLOSE_RADIUS)..minOf(width - 1, x + OUTLINE_CLOSE_RADIUS)) {
                fillable[ny * width + nx] = false
            }
        }
    }
    return fillable
}

/**
 * Bounds of the enclosed region holding the most seed pixels around [textBox], with its row-span
 * area: each row counted from its leftmost to its rightmost pixel, so glyph holes inside the
 * interior don't count against its shape. Seeds also land in glyph counters (口, あ) and, where
 * the box pokes past an outline, outside the bubble; the interior is the region most of the box
 * sits in. A region reaching the window edge has leaked into the page and is never chosen.
 */
private fun interiorAround(fillable: BooleanArray, width: Int, height: Int, textBox: FloatArray): Pair<IntArray, Int>? {
    val seedLeft = (textBox[0].toInt() - SEED_PAD).coerceIn(0, width - 1)
    val seedTop = (textBox[1].toInt() - SEED_PAD).coerceIn(0, height - 1)
    val seedRight = (textBox[2].toInt() + SEED_PAD).coerceIn(seedLeft + 1, width)
    val seedBottom = (textBox[3].toInt() + SEED_PAD).coerceIn(seedTop + 1, height)

    val region = IntArray(fillable.size) // 0 = unvisited, otherwise region index + 1
    val queue = IntArray(fillable.size)
    val bounds = mutableListOf<IntArray>()
    val leaks = mutableListOf<Boolean>()
    val seedCounts = mutableListOf<Int>()

    for (sy in seedTop until seedBottom) for (sx in seedLeft until seedRight) {
        val seed = sy * width + sx
        if (!fillable[seed]) continue
        if (region[seed] == 0) {
            val label = bounds.size + 1
            val box = intArrayOf(sx, sy, sx + 1, sy + 1)
            var head = 0
            var tail = 0
            queue[tail++] = seed
            region[seed] = label
            while (head < tail) {
                val i = queue[head++]
                val x = i % width
                val y = i / width
                box[0] = minOf(box[0], x)
                box[1] = minOf(box[1], y)
                box[2] = maxOf(box[2], x + 1)
                box[3] = maxOf(box[3], y + 1)
                if (x > 0 && fillable[i - 1] && region[i - 1] == 0) { region[i - 1] = label; queue[tail++] = i - 1 }
                if (x < width - 1 && fillable[i + 1] && region[i + 1] == 0) { region[i + 1] = label; queue[tail++] = i + 1 }
                if (y > 0 && fillable[i - width] && region[i - width] == 0) { region[i - width] = label; queue[tail++] = i - width }
                if (y < height - 1 && fillable[i + width] && region[i + width] == 0) { region[i + width] = label; queue[tail++] = i + width }
            }
            bounds.add(box)
            // A bubble clipped by the screen edge counts as leaked and falls back;
            // handle it if captures often cut bubbles.
            leaks.add(box[0] == 0 || box[1] == 0 || box[2] == width || box[3] == height)
            seedCounts.add(0)
        }
        seedCounts[region[seed] - 1]++
    }

    val best = seedCounts.indices.filter { !leaks[it] }.maxByOrNull { seedCounts[it] } ?: return null
    val box = bounds[best]
    var spanArea = 0
    for (y in box[1] until box[3]) {
        var first = -1
        var last = -1
        for (x in box[0] until box[2]) if (region[y * width + x] == best + 1) { if (first < 0) first = x; last = x }
        if (first >= 0) spanArea += last - first + 1
    }
    return box to spanArea
}

private fun intersects(a: FloatArray, b: FloatArray): Boolean =
    a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]

/** Whether [box] reaches into [shape]: an ellipse by the box's point nearest its centre, a rectangle by overlap. */
private fun shapeMeets(shape: BalloonShape, box: FloatArray): Boolean {
    val b = shape.bounds
    if (!intersects(b, box)) return false
    if (!shape.ellipse) return true
    val cx = (b[0] + b[2]) / 2f
    val cy = (b[1] + b[3]) / 2f
    val dx = (cx.coerceIn(box[0], box[2]) - cx) / ((b[2] - b[0]) / 2f)
    val dy = (cy.coerceIn(box[1], box[3]) - cy) / ((b[3] - b[1]) / 2f)
    return dx * dx + dy * dy < 1f
}

internal fun luminance(argb: Int): Int =
    ((argb shr 16 and 0xFF) * 299 + (argb shr 8 and 0xFF) * 587 + (argb and 0xFF) * 114) / 1000

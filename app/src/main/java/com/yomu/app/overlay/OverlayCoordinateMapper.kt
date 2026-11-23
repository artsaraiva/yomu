package com.yomu.app.overlay

data class OverlayBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

object OverlayCoordinateMapper {
    data class MapParams(
        val offsetX: Float,
        val offsetY: Float,
        val scaleX: Float,
        val scaleY: Float
    )

    // #475: params() KDoc goes here.
    fun params(
        captureWidth: Int,
        captureHeight: Int,
        displayWidth: Int,
        displayHeight: Int,
        overlayScreenX: Int,
        overlayScreenY: Int
    ): MapParams {
        val scaleX = if (captureWidth > 0) displayWidth.toFloat() / captureWidth else 1f
        val scaleY = if (captureHeight > 0) displayHeight.toFloat() / captureHeight else 1f
        return MapParams(
            offsetX = overlayScreenX.toFloat(),
            offsetY = overlayScreenY.toFloat(),
            scaleX = scaleX,
            scaleY = scaleY
        )
    }

    // A bubble grown to hold its text can reach past the canvas; slide it back so no line is
    // drawn off-screen. A box larger than the canvas is pinned to the top-left.
    fun clampToCanvas(bounds: OverlayBounds, canvasWidth: Float, canvasHeight: Float): OverlayBounds {
        val shiftX = slideInside(bounds.left, bounds.right, canvasWidth)
        val shiftY = slideInside(bounds.top, bounds.bottom, canvasHeight)
        return OverlayBounds(
            left = bounds.left + shiftX,
            top = bounds.top + shiftY,
            right = bounds.right + shiftX,
            bottom = bounds.bottom + shiftY
        )
    }

    private fun slideInside(start: Float, end: Float, size: Float): Float = when {
        start < 0f -> -start
        end > size -> size - end
        else -> 0f
    }

    /** Moves [bounds] from capture pixels to the display's, then into the overlay window that starts at the offset. */
    fun map(bounds: FloatArray, params: MapParams): OverlayBounds {
        return OverlayBounds(
            left = bounds[0] * params.scaleX - params.offsetX,
            top = bounds[1] * params.scaleY - params.offsetY,
            right = bounds[2] * params.scaleX - params.offsetX,
            bottom = bounds[3] * params.scaleY - params.offsetY
        )
    }
}

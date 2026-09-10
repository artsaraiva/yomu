package com.yomu.app.overlay

data class OverlayBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
}

object OverlayCoordinateMapper {
    data class MapParams(
        val offsetX: Float,
        val offsetY: Float,
        val scaleX: Float,
        val scaleY: Float
    )

    fun params(
        captureWidth: Int,
        captureHeight: Int,
        canvasWidth: Int,
        canvasHeight: Int,
        overlayScreenX: Int,
        overlayScreenY: Int
    ): MapParams {
        val scaleX = if (captureWidth > 0) canvasWidth.toFloat() / captureWidth else 1f
        val scaleY = if (captureHeight > 0 && canvasHeight > captureHeight) {
            canvasHeight.toFloat() / captureHeight
        } else {
            1f
        }
        return MapParams(
            offsetX = overlayScreenX.toFloat(),
            offsetY = overlayScreenY.toFloat(),
            scaleX = scaleX,
            scaleY = scaleY
        )
    }

    // A bubble grown to hold its text can reach past the canvas; slide it back so no line is
    // drawn off-screen. A box taller than the canvas is pinned to the top.
    fun clampToCanvas(bounds: OverlayBounds, canvasHeight: Float): OverlayBounds {
        val shift = when {
            bounds.top < 0f -> -bounds.top
            bounds.bottom > canvasHeight -> canvasHeight - bounds.bottom
            else -> return bounds
        }
        return bounds.copy(top = bounds.top + shift, bottom = bounds.bottom + shift)
    }

    fun map(bounds: FloatArray, params: MapParams): OverlayBounds {
        return OverlayBounds(
            left = (bounds[0] - params.offsetX) * params.scaleX,
            top = (bounds[1] - params.offsetY) * params.scaleY,
            right = (bounds[2] - params.offsetX) * params.scaleX,
            bottom = (bounds[3] - params.offsetY) * params.scaleY
        )
    }
}

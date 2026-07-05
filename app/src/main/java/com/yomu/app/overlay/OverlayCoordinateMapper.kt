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

    fun map(bounds: FloatArray, params: MapParams): OverlayBounds {
        return OverlayBounds(
            left = (bounds[0] - params.offsetX) * params.scaleX,
            top = (bounds[1] - params.offsetY) * params.scaleY,
            right = (bounds[2] - params.offsetX) * params.scaleX,
            bottom = (bounds[3] - params.offsetY) * params.scaleY
        )
    }
}

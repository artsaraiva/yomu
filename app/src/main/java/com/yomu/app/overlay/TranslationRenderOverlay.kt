package com.yomu.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import com.yomu.pipeline.typesetting.TypesetBubble

class TranslationRenderOverlay(
    private val context: Context,
    private val windowManager: WindowManager
) {
    companion object {
        private const val TAG = "TranslationRender"
    }

    private var overlayView: FrameLayout? = null

    fun show(
        bubbles: List<TypesetBubble>,
        pageWidth: Int,
        pageHeight: Int
    ) {
        remove()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        )

        overlayView = object : FrameLayout(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val screenLocation = IntArray(2)

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                getLocationOnScreen(screenLocation)
                val mapParams = OverlayCoordinateMapper.params(
                    captureWidth = pageWidth,
                    captureHeight = pageHeight,
                    canvasWidth = canvas.width,
                    canvasHeight = canvas.height,
                    overlayScreenX = screenLocation[0],
                    overlayScreenY = screenLocation[1]
                )
                Log.d(
                    TAG,
                    "render canvasW=${canvas.width} canvasH=${canvas.height} pageW=$pageWidth pageH=$pageHeight overlayX=${screenLocation[0]} overlayY=${screenLocation[1]} scaleX=${mapParams.scaleX} scaleY=${mapParams.scaleY}"
                )
                for (bubble in bubbles) {
                    drawBubble(canvas, bubble, paint, mapParams)
                }
            }
        }.apply {
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { remove() }
        }

        windowManager.addView(overlayView, params)
    }

    fun remove() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }

    private fun drawBubble(
        canvas: Canvas,
        bubble: TypesetBubble,
        paint: Paint,
        params: OverlayCoordinateMapper.MapParams
    ) {
        val mappedBounds = OverlayCoordinateMapper.map(bubble.boundingBox, params)
        val bx = mappedBounds.left
        val by = mappedBounds.top
        val bw = mappedBounds.width()
        val bh = mappedBounds.height()
        val radius = minOf(bw, bh) * 0.12f

        paint.isAntiAlias = true
        paint.color = bubble.backgroundColor
        canvas.drawRoundRect(bx, by, bx + bw, by + bh, radius, radius, paint)

        val saveCount = canvas.save()
        canvas.clipRect(bx, by, bx + bw, by + bh)

        paint.color = bubble.textColor
        paint.textSize = bubble.fontSize
        paint.typeface = Typeface.DEFAULT

        val lineHeight = paint.fontSpacing * 1.3f
        val totalTextHeight = lineHeight * bubble.textLines.size
        val blockTop = by + (bh - totalTextHeight) / 2f
        var textY = blockTop - paint.fontMetrics.ascent

        for (line in bubble.textLines) {
            val lineWidth = paint.measureText(line)
            val textX = bx + (bw - lineWidth) / 2f
            canvas.drawText(line, textX, textY, paint)
            textY += lineHeight
        }

        canvas.restoreToCount(saveCount)
    }
}

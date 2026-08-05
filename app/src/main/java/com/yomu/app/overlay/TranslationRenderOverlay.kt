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
        private const val OCR_BACKGROUND_ALPHA = 0xAA
        private const val OCR_TEXT_SIZE_DP = 14f
        private const val OCR_LINE_SPACING = 1.2f
        private const val TRANSLATED_TEXT_SIZE_DP = 16f
        private const val TRANSLATED_LINE_SPACING = 1.3f
    }

    private var overlayView: FrameLayout? = null
    private var pageWidth: Int = 0
    private var pageHeight: Int = 0
    private val bubbleStates = mutableListOf<OverlayBubbleState>()

    fun show(
        bubbles: List<TypesetBubble>,
        pageWidth: Int,
        pageHeight: Int
    ) {
        remove()

        val params = createLayoutParams()
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
                for (bubble in bubbles) {
                    drawTypesetBubble(canvas, bubble, paint, mapParams)
                }
            }
        }.apply {
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { remove() }
        }

        windowManager.addView(overlayView, params)
    }

    fun showOcrBubbles(
        states: List<OverlayBubbleState>,
        pageWidth: Int,
        pageHeight: Int
    ) {
        this.pageWidth = pageWidth
        this.pageHeight = pageHeight
        bubbleStates.clear()
        bubbleStates.addAll(states)

        val existingView = overlayView
        if (existingView != null) {
            existingView.invalidate()
            return
        }

        remove()
        val params = createLayoutParams()
        overlayView = object : FrameLayout(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val screenLocation = IntArray(2)

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                getLocationOnScreen(screenLocation)
                val mapParams = OverlayCoordinateMapper.params(
                    captureWidth = this@TranslationRenderOverlay.pageWidth,
                    captureHeight = this@TranslationRenderOverlay.pageHeight,
                    canvasWidth = canvas.width,
                    canvasHeight = canvas.height,
                    overlayScreenX = screenLocation[0],
                    overlayScreenY = screenLocation[1]
                )
                Log.d(
                    TAG,
                    "ocr render canvasW=${canvas.width} canvasH=${canvas.height} pageW=$pageWidth pageH=$pageHeight overlayX=${screenLocation[0]} overlayY=${screenLocation[1]} scaleX=${mapParams.scaleX} scaleY=${mapParams.scaleY}"
                )
                for (state in bubbleStates) {
                    drawOverlayBubbleState(canvas, state, paint, mapParams)
                }
            }
        }.apply {
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { remove() }
        }

        windowManager.addView(overlayView, params)
    }

    fun updateTranslations(states: List<OverlayBubbleState>) {
        bubbleStates.clear()
        bubbleStates.addAll(states)
        overlayView?.invalidate()
    }

    fun remove() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        bubbleStates.clear()
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
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
    }

    private fun drawTypesetBubble(
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

        val lineHeight = paint.fontSpacing * TRANSLATED_LINE_SPACING
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

    private fun drawOverlayBubbleState(
        canvas: Canvas,
        state: OverlayBubbleState,
        paint: Paint,
        params: OverlayCoordinateMapper.MapParams
    ) {
        val bounds = state.bounds
        val sourceBounds = floatArrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val mappedBounds = OverlayCoordinateMapper.map(sourceBounds, params)
        val bx = mappedBounds.left
        val by = mappedBounds.top
        val bw = mappedBounds.width()
        val bh = mappedBounds.height()
        val radius = minOf(bw, bh) * 0.12f

        paint.isAntiAlias = true
        paint.color = if (state.isTranslated) {
            Color.WHITE
        } else {
            Color.argb(OCR_BACKGROUND_ALPHA, 0, 0, 0)
        }
        canvas.drawRoundRect(bx, by, bx + bw, by + bh, radius, radius, paint)

        val saveCount = canvas.save()
        canvas.clipRect(bx, by, bx + bw, by + bh)

        val text = if (state.isTranslated) {
            state.translatedText.orEmpty()
        } else {
            state.ocrText
        }
        val textSize = if (state.isTranslated) {
            dpToPx(TRANSLATED_TEXT_SIZE_DP)
        } else {
            dpToPx(OCR_TEXT_SIZE_DP)
        }
        paint.color = if (state.isTranslated) Color.BLACK else Color.WHITE
        paint.textSize = textSize
        paint.typeface = if (state.isTranslated) Typeface.DEFAULT else Typeface.DEFAULT_BOLD

        val lines = wrapText(text, bw * 0.9f, paint)
        val lineHeight = paint.fontSpacing * if (state.isTranslated) TRANSLATED_LINE_SPACING else OCR_LINE_SPACING
        val totalTextHeight = lineHeight * lines.size
        val blockTop = by + (bh - totalTextHeight) / 2f
        var textY = blockTop - paint.fontMetrics.ascent

        for (line in lines) {
            val lineWidth = paint.measureText(line)
            val textX = bx + (bw - lineWidth) / 2f
            canvas.drawText(line, textX, textY, paint)
            textY += lineHeight
        }

        canvas.restoreToCount(saveCount)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }
}

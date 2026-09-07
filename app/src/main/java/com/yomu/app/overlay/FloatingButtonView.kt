package com.yomu.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.yomu.app.ui.theme.paperColors

class FloatingButtonView(context: Context) : View(context) {
    enum class State { IDLE, TRANSLATING }

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 24f * density
    }
    private val bounds = RectF()
    var currentState: State = State.IDLE
        private set

    init { updateAppearance() }

    fun setState(state: State) {
        currentState = state
        updateAppearance()
    }

    fun updateAppearance() {
        contentDescription = if (currentState == State.IDLE) "Translate screen. Hold for quick settings." else "Translating screen"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = context.paperColors()
        val inset = 2f * density
        bounds.set(inset, inset, width - inset, height - inset)
        fill.color = colors.paperRaised
        canvas.drawRoundRect(bounds, 18f * density, 18f * density, fill)
        edge.color = colors.inkMuted
        canvas.drawRoundRect(bounds, 18f * density, 18f * density, edge)
        bounds.inset(4f * density, 4f * density)
        fill.color = if (currentState == State.IDLE) colors.accent else colors.paperRaised
        canvas.drawRoundRect(bounds, 14f * density, 14f * density, fill)
        textPaint.color = if (currentState == State.IDLE) colors.onAccent else colors.ink
        val textYOffset = -(textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(if (currentState == State.IDLE) "読" else "…", width / 2f, height / 2f + textYOffset, textPaint)
        if (currentState == State.TRANSLATING) {
            edge.color = colors.accent
            canvas.drawArc(bounds, -90f, 100f, false, edge)
        }
    }
}

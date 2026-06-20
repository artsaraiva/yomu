package com.yomu.app.overlay

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator

class FloatingButtonView(context: Context) : View(context) {

    enum class State {
        IDLE,
        TRANSLATING
    }

    private val density = resources.displayMetrics.density

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 24f * density
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }

    private var currentColor = IDLE_COLOR
    private var currentTextAlpha = 255
    private var rotationAngle = 0f

    private val ringRect = RectF()
    private val sweepAngle = 100f
    private val ringInset = 2f * density

    private var colorAnimator: ValueAnimator? = null
    private var alphaAnimator: ValueAnimator? = null
    private var rotationAnimator: ValueAnimator? = null

    var currentState: State = State.IDLE
        private set

    fun setState(state: State) {
        if (currentState == state) return
        currentState = state

        when (state) {
            State.IDLE -> transitionToIdle()
            State.TRANSLATING -> transitionToTranslating()
        }
    }

    private fun transitionToIdle() {
        stopRotationAnimator()
        animateColorTo(IDLE_COLOR)
        animateTextAlphaTo(255)
    }

    private fun transitionToTranslating() {
        startRotationAnimator()
        animateColorTo(TRANSLATING_COLOR)
        animateTextAlphaTo(128)
    }

    private fun animateColorTo(targetColor: Int) {
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor).apply {
            duration = COLOR_ANIMATION_DURATION
            addUpdateListener {
                currentColor = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun animateTextAlphaTo(targetAlpha: Int) {
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofInt(currentTextAlpha, targetAlpha).apply {
            duration = ALPHA_ANIMATION_DURATION
            addUpdateListener {
                currentTextAlpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun startRotationAnimator() {
        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = ROTATION_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopRotationAnimator() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        rotationAngle = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) - ringInset

        backgroundPaint.color = currentColor
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        textPaint.alpha = currentTextAlpha
        val textYOffset = (textPaint.descent() - textPaint.ascent()) / 2f - textPaint.descent()
        canvas.drawText("読", cx, cy + textYOffset, textPaint)

        if (currentState == State.TRANSLATING) {
            ringPaint.color = 0xFFFFFFFF.toInt()
            val ringRadius = radius - ringPaint.strokeWidth / 2f
            ringRect.set(
                cx - ringRadius,
                cy - ringRadius,
                cx + ringRadius,
                cy + ringRadius
            )
            canvas.drawArc(ringRect, rotationAngle - sweepAngle / 2f, sweepAngle, false, ringPaint)
        }
    }

    override fun onDetachedFromWindow() {
        colorAnimator?.cancel()
        alphaAnimator?.cancel()
        stopRotationAnimator()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val IDLE_COLOR = 0xFFFF5722.toInt()
        private const val TRANSLATING_COLOR = 0xFF4CAF50.toInt()
        private const val COLOR_ANIMATION_DURATION = 300L
        private const val ALPHA_ANIMATION_DURATION = 150L
        private const val ROTATION_DURATION = 1000L
    }
}

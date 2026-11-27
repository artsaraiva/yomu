package com.yomu.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.yomu.app.ui.theme.paperColors
import kotlin.math.ceil

/**
 * The capsule that grows out of the floating button while a page translates: the stage message over three step
 * marks and "Step n of 3". The button sits on one of its ends, so the content keeps clear of that end.
 */
class ProgressCapsuleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(14f)
    }
    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(12f) }
    private val pill = RectF()
    private val mark = RectF()
    private var message = ""
    private var step = 0
    private var grown = 1f
    private var animator: ValueAnimator? = null

    /** True while the button is at the capsule's right end, so the capsule grows and reads to its left. */
    var buttonAtRight = false
        set(value) {
            field = value
            invalidate()
        }

    /** True while the capsule is still growing out of the button. */
    val growing: Boolean get() = animator != null

    init {
        visibility = GONE
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }

    /** Shows [message] as step [step] of [PROGRESS_STEPS]; a hidden capsule grows out of the button, unless animations are off. */
    fun show(message: String, step: Int) {
        this.message = message
        this.step = step
        contentDescription = "$message. ${caption()}"
        if (visibility != VISIBLE) {
            visibility = VISIBLE
            grow()
        }
        invalidate()
    }

    fun hide() {
        stopGrowing()
        visibility = GONE
    }

    /** The size the capsule takes for its current message when it may be at most [maxWidth] wide. */
    fun sizeWithin(maxWidth: Int): Point {
        val content = maxOf(titlePaint.measureText(message), marksWidth() + MARK_TO_CAPTION_DP * density + captionPaint.measureText(caption()))
        val width = ceil(content + (OUTER_PADDING_DP + BUTTON_PADDING_DP) * density).toInt()
        val height = ceil(lineHeight(titlePaint) + lineHeight(captionPaint) + (LINE_GAP_DP + 2 * VERTICAL_PADDING_DP) * density).toInt()
        return Point(minOf(width, maxWidth), height)
    }

    override fun onDetachedFromWindow() {
        stopGrowing()
        super.onDetachedFromWindow()
    }

    private fun grow() {
        stopGrowing()
        if (!ValueAnimator.areAnimatorsEnabled()) return
        grown = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GROW_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                grown = it.animatedValue as Float
                if (it.animatedFraction >= 1f) animator = null
                invalidate()
            }
            start()
        }
    }

    private fun stopGrowing() {
        animator?.cancel()
        animator = null
        grown = 1f
    }

    private fun caption() = "Step $step of $PROGRESS_STEPS"

    private fun marksWidth() = (PROGRESS_STEPS * MARK_WIDTH_DP + (PROGRESS_STEPS - 1) * MARK_GAP_DP) * density

    private fun lineHeight(paint: Paint) = paint.descent() - paint.ascent()

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = context.paperColors()
        val inset = edge.strokeWidth / 2f
        val radius = minOf(RADIUS_DP * density, height / 2f)
        // The pill starts as the button's own circle and stretches away from it.
        val length = 2 * radius + (width - 2 * radius) * grown
        if (buttonAtRight) pill.set(width - length + inset, inset, width - inset, height - inset)
        else pill.set(inset, inset, length - inset, height - inset)
        fill.color = colors.paperRaised
        canvas.drawRoundRect(pill, radius, radius, fill)
        edge.color = colors.inkMuted
        canvas.drawRoundRect(pill, radius, radius, edge)

        val alpha = (grown * 255).toInt()
        val left = (if (buttonAtRight) OUTER_PADDING_DP else BUTTON_PADDING_DP) * density
        val room = width - (OUTER_PADDING_DP + BUTTON_PADDING_DP) * density
        val top = (height - lineHeight(titlePaint) - LINE_GAP_DP * density - lineHeight(captionPaint)) / 2f
        titlePaint.color = colors.ink
        titlePaint.alpha = alpha
        val title = TextUtils.ellipsize(message, titlePaint, room, TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, left, top - titlePaint.ascent(), titlePaint)

        val captionTop = top + lineHeight(titlePaint) + LINE_GAP_DP * density
        val markTop = captionTop + (lineHeight(captionPaint) - MARK_HEIGHT_DP * density) / 2f
        for (index in 0 until PROGRESS_STEPS) {
            val markLeft = left + index * (MARK_WIDTH_DP + MARK_GAP_DP) * density
            mark.set(markLeft, markTop, markLeft + MARK_WIDTH_DP * density, markTop + MARK_HEIGHT_DP * density)
            fill.color = when {
                index + 1 < step -> colors.ink
                index + 1 == step -> colors.accent
                else -> colors.edge
            }
            fill.alpha = alpha
            canvas.drawRoundRect(mark, mark.height() / 2f, mark.height() / 2f, fill)
        }
        fill.alpha = 255
        captionPaint.color = colors.inkMuted
        captionPaint.alpha = alpha
        canvas.drawText(caption(), left + marksWidth() + MARK_TO_CAPTION_DP * density, captionTop - captionPaint.ascent(), captionPaint)
    }

    private companion object {
        const val GROW_MS = 200L
        const val RADIUS_DP = 28f
        const val OUTER_PADDING_DP = 20f
        // The button's 56 dp plus a 12 dp gap before the text.
        const val BUTTON_PADDING_DP = 68f
        const val VERTICAL_PADDING_DP = 8f
        const val LINE_GAP_DP = 2f
        const val MARK_WIDTH_DP = 14f
        const val MARK_HEIGHT_DP = 6f
        const val MARK_GAP_DP = 4f
        const val MARK_TO_CAPTION_DP = 8f
    }
}

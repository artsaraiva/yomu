package com.yomu.app.overlay

import kotlin.math.roundToInt

/** The reading panel's text size stepper (#551): ten percent a step over the Typesetting slider's 50–200% range. */
internal object FontSizeSteps {
    private const val STEP = 10
    private const val MIN = 50
    private const val MAX = 200

    /** The next ten above [scale]; a value between steps lands on the nearest ten above it. */
    fun larger(scale: Float): Float = ((percent(scale) / STEP + 1) * STEP).coerceAtMost(MAX) / 100f

    /** The next ten below [scale]; a value between steps lands on the nearest ten below it. */
    fun smaller(scale: Float): Float = (((percent(scale) - 1) / STEP) * STEP).coerceAtLeast(MIN) / 100f

    fun label(scale: Float): String = "${percent(scale)}%"

    private fun percent(scale: Float): Int = (scale * 100).roundToInt().coerceIn(MIN, MAX)
}

package com.yomu.app.detection

import android.content.SharedPreferences
import com.yomu.pipeline.bubble.BubbleDetector
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The reader's bubble confidence threshold (#227). Save refuses anything off the 0.10–0.60 range or
 * its 0.05 steps, mirroring `GenerationProfileStore`; load falls back to the default for a stored
 * value that is out of range or wrongly typed.
 */
class DetectionThresholdStore(private val prefs: SharedPreferences) {

    fun load(): Float = runCatching { prefs.getFloat(KEY, DEFAULT) }.getOrNull()?.takeIf(::accepts) ?: DEFAULT

    fun save(value: Float): Boolean {
        if (!accepts(value)) return false
        prefs.edit().putFloat(KEY, value).apply()
        return true
    }

    fun reset() = prefs.edit().remove(KEY).apply()

    companion object {
        const val KEY = "detection_confidence_threshold"
        const val MIN = 0.10f
        const val MAX = 0.60f
        const val STEP = 0.05f
        const val DEFAULT = BubbleDetector.DEFAULT_CONFIDENCE_THRESHOLD

        fun accepts(value: Float): Boolean {
            if (!value.isFinite() || value !in MIN..MAX) return false
            val steps = (value - MIN) / STEP
            return abs(steps - steps.roundToInt()) < 1e-3f
        }

        /** Slider positions between [MIN] and [MAX]: position 0 is [MIN], position [STEPS] is [MAX]. */
        val STEPS = ((MAX - MIN) / STEP).roundToInt()

        /** Nearest step, exact for every stored value: n / 20f is the float closest to n × 0.05. */
        fun snap(value: Float): Float = ((value / STEP).roundToInt() / 20f).coerceIn(MIN, MAX)

        fun atStep(step: Int): Float = snap(MIN + step.coerceIn(0, STEPS) * STEP)

        fun stepOf(value: Float): Int = ((value - MIN) / STEP).roundToInt().coerceIn(0, STEPS)
    }
}

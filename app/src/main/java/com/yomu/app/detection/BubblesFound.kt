package com.yomu.app.detection

import kotlin.math.abs

/**
 * The reading panel's three named detection thresholds (#551). A lower threshold keeps fainter boxes,
 * so [MORE] sits below the default and [FEWER] above it.
 */
enum class BubblesFound(val label: String, val threshold: Float) {
    FEWER("Fewer", 0.40f),
    DEFAULT("Default", DetectionThresholdStore.DEFAULT),
    MORE("More", 0.15f);

    companion object {
        /** The choice stored as [threshold], or null for a value from the Pipeline slider that matches none. */
        fun of(threshold: Float): BubblesFound? = entries.firstOrNull { abs(it.threshold - threshold) < 1e-3f }
    }
}

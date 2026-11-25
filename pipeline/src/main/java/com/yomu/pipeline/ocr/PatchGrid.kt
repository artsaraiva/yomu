package com.yomu.pipeline.ocr

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The grid of 16-pixel patches Hayai reads a crop as: the crop is resized to [width] × [height], keeping its shape,
 * so a wide line or a tall column stays legible instead of being squeezed square.
 */
internal data class PatchGrid(val rows: Int, val columns: Int) {
    val height: Int get() = rows * PATCH
    val width: Int get() = columns * PATCH
    val patches: Int get() = rows * columns

    companion object {
        const val PATCH = 16
        // Hayai's own tier choice goes to 384 patches for big crops. Capped here, the wide spreads stay under twice the
        // old reader's time on the emulator and JMangaBench_Read scores 98.82 against 98.78 uncapped (#510).
        private const val MAX_TIER = 192
        private const val MIN_PATCHES = 64
        private const val MAX_SCALE = 4.0

        /** The grid for a crop of [height] × [width] pixels. */
        fun of(height: Int, width: Int): PatchGrid = fit(height, width, min(autoTier(height, width), MAX_TIER))

        /**
         * The largest shape-keeping grid of at most [maxPatches], scaled up no more than [MAX_SCALE] times; a crop
         * that leaves fewer than [MIN_PATCHES] is scaled up further to reach them. Ported from Hayai's NaFlex resize.
         */
        private fun fit(height: Int, width: Int, maxPatches: Int): PatchGrid {
            fun grid(scale: Double) = PatchGrid(
                rows = max(1, ceil(height * scale / PATCH).toInt()),
                columns = max(1, ceil(width * scale / PATCH).toInt())
            )
            fun largestScale(low: Double, high: Double, limit: Int): Double {
                var lo = low
                var hi = high
                while (hi - lo >= SEARCH_STEP) {
                    val mid = (lo + hi) / 2
                    if (grid(mid).patches <= limit) lo = mid else hi = mid
                }
                return lo
            }
            val scale = largestScale(SEARCH_STEP, MAX_SCALE, maxPatches)
            if (grid(scale).patches >= MIN_PATCHES) return grid(scale)
            return grid(largestScale(scale, UPSCALE_CEILING, min(MIN_PATCHES, maxPatches)))
        }

        /** Hayai's published 'auto' choice among the 128, 192, 256 and 384 patch tiers. */
        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        private fun autoTier(height: Int, width: Int): Int {
            val tiers = listOf(128, 192, 256, 384).associateWith { fit(height, width, it) }
            fun count(tier: Int) = tiers.getValue(tier).patches
            fun scale(tier: Int) = tiers.getValue(tier).let { min(it.height / max(1, height).toDouble(), it.width / max(1, width).toDouble()) }
            if (count(128) == count(384)) return 128
            val h = max(1, height).toDouble()
            val w = max(1, width).toDouble()
            val aspect = max(h, w) / min(h, w)
            val square = w / h in 0.77..1.30
            val horizontal = w / h > 1.30
            if (!square) {
                if (!horizontal && scale(128) >= 1.65 && aspect < 3.8) return 128
                if (horizontal && scale(128) >= 2.35 && aspect < 3.2) return 128
            }
            val s256 = scale(256)
            if (count(384) > count(256) &&
                (s256 < 1.28 || (square && s256 < 2.20) || (horizontal && s256 < 1.45) ||
                    (aspect >= 3.8 && s256 < 1.75) || (aspect >= 5.0 && s256 < 2.20))
            ) return 384
            if (count(192) == count(128)) return 128
            if (count(256) == count(192)) return 192
            if (!square) {
                if (!horizontal && scale(192) >= 1.55 && aspect < 3.6) return 192
                if (horizontal && scale(192) >= 1.85 && aspect < 3.0) return 192
            }
            return 256
        }

        private const val SEARCH_STEP = 1e-5
        private const val UPSCALE_CEILING = 100.0
    }
}

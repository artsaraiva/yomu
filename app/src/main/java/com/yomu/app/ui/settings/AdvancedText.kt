package com.yomu.app.ui.settings

import com.yomu.app.translation.ResourceLimit
import com.yomu.core.RuntimeLimits

/** What a slider row says beside its label: the value now, and the default only while the value differs from it. */
internal data class SliderText(val value: String, val default: String?)

private fun <T> sliderText(value: T, default: T, format: (T) -> String): SliderText =
    SliderText(format(value), format(default).takeIf { value != default })

internal fun limitText(limit: ResourceLimit, value: Int, cores: Int = RuntimeLimits.MAX_THREADS): SliderText =
    sliderText(value, limit.default) {
        when (limit) {
            ResourceLimit.THREADS -> "$it of $cores cores"
            ResourceLimit.CONTEXT_TOKENS -> "$it tokens"
            ResourceLimit.FIT_BUDGET_PERCENT -> "$it% of this phone"
        }
    }

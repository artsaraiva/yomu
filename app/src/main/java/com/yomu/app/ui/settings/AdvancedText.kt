package com.yomu.app.ui.settings

import com.yomu.app.db.entities.ModelType
import com.yomu.app.detection.DetectionThresholdStore
import com.yomu.app.translation.ResourceLimit
import com.yomu.core.GenerationBound
import com.yomu.core.RuntimeLimits

/** The Advanced page's sections, in the order it lists them (#549). */
internal enum class AdvancedSection(val title: String, val appliedNote: String? = null) {
    BubbleFinding("Bubble finding", "Applied on the next capture"),
    TextReading("Text reading"),
    Generation("Generation", "Applied on the next capture"),
    Performance("Performance", "Applied on the next page"),
    LiveUsage("Live usage")
}

/** The one line a closed section shows about its current state. */
internal fun SettingsUiState.summary(section: AdvancedSection): String = when (section) {
    AdvancedSection.BubbleFinding -> {
        val threshold = thresholdText(detectionThreshold)
        "${selectedName(ModelType.DETECTION)} · threshold ${if (threshold.default == null) "at default" else threshold.value}"
    }
    AdvancedSection.TextReading -> selectedName(ModelType.OCR)
    AdvancedSection.Generation -> "Sampler · ${if (generationOverridden) "changed from this model's defaults" else "this model's defaults"}"
    AdvancedSection.Performance -> {
        val threads = resourceLimits.getValue(ResourceLimit.THREADS)
        "$threads ${if (threads == 1) "thread" else "threads"} · ${resourceLimits.getValue(ResourceLimit.CONTEXT_TOKENS)} tokens · " +
            "${resourceLimits.getValue(ResourceLimit.FIT_BUDGET_PERCENT)}% memory"
    }
    AdvancedSection.LiveUsage -> "Memory, CPU and the last page's time"
}

private fun SettingsUiState.selectedName(type: ModelType): String =
    deliverables[type].orEmpty().firstOrNull { it.id == selectedIds[type] }?.name ?: "No model selected"

/** Whether [section] holds a value off its default, which is when its Reset does anything. */
internal fun SettingsUiState.canReset(section: AdvancedSection): Boolean = when (section) {
    AdvancedSection.BubbleFinding -> detectionThreshold != DetectionThresholdStore.DEFAULT
    AdvancedSection.Generation -> generationOverridden
    AdvancedSection.Performance -> ResourceLimit.entries.any { resourceLimits[it] != it.default }
    AdvancedSection.TextReading, AdvancedSection.LiveUsage -> false
}

/** One message naming every stored value in [labels] that was recovered to its default, or null for none. */
internal fun recoveryWarning(labels: List<String>): String? =
    labels.takeIf { it.isNotEmpty() }?.let { "Some saved settings were invalid and are using their defaults: ${it.joinToString()}." }

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

internal fun thresholdText(value: Float): SliderText = sliderText(value, DetectionThresholdStore.DEFAULT) { "%.2f".format(it) }

/** [default] is the selected model's own sampling, not the bound's, since each model's maker recommends its own. */
internal fun generationText(bound: GenerationBound, value: Float, default: Float): SliderText {
    val decimals = bound.step.toString().substringAfter('.', "").trimEnd('0').length
    return sliderText(value, default) { "%.${decimals}f".format(it) }
}

/** "TOP_K" → "Top-k". Rendered from the bound so a new entry needs no label literal (#196). */
internal val GenerationBound.label: String
    get() = name.lowercase().replace('_', '-').replaceFirstChar { it.uppercase() }

internal val GenerationBound.consequence: String
    get() = when (this) {
        GenerationBound.TEMPERATURE -> "Lower keeps to the likeliest wording, higher varies it."
        GenerationBound.TOP_K -> "How many candidate words each step picks from. On the phone only."
        GenerationBound.TOP_P -> "Lower drops more of the unlikely words."
    }

internal val ResourceLimit.label: String
    get() = when (this) {
        ResourceLimit.THREADS -> "Threads"
        ResourceLimit.CONTEXT_TOKENS -> "Context size"
        ResourceLimit.FIT_BUDGET_PERCENT -> "Memory budget"
    }

internal val ResourceLimit.consequence: String
    get() = when (this) {
        ResourceLimit.THREADS -> "Fewer keeps the phone responsive but translates slower."
        ResourceLimit.CONTEXT_TOKENS -> "Smaller uses less memory; a long page may go line by line."
        ResourceLimit.FIT_BUDGET_PERCENT -> "Models that need more are not offered."
    }

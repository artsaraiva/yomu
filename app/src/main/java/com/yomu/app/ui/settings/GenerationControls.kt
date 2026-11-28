package com.yomu.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams
import kotlin.math.roundToInt

/** Slider positions between a bound's ends: position 0 is [GenerationBound.min]. */
private val GenerationBound.positions: Int get() = ((max - min) / step).roundToInt()

/** The value at [position], kept inside the range float error may leave. */
private fun GenerationBound.at(position: Int): Float = (min + position * step).coerceIn(min, max)

@Composable
internal fun RecoveryWarning(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The sampler sliders of Advanced's Generation section (#192, #549); [defaults] are the selected model's own. */
@Composable
internal fun GenerationControls(
    generation: GenerationParams,
    defaults: GenerationParams,
    warning: String?,
    onChange: (GenerationBound, Float) -> Unit
) {
    warning?.let { RecoveryWarning(it) }
    Text(
        "For the translation model, on this phone or on your server. A value you change applies to every model; " +
            "the rest follow what each model's maker recommends.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    GenerationBound.entries.forEach { bound -> GenerationSlider(bound, bound.read(generation), bound.read(defaults), onChange) }
}

@Composable
private fun GenerationSlider(bound: GenerationBound, stored: Float, default: Float, onChange: (GenerationBound, Float) -> Unit) {
    // Local while dragging; saved once on release so a drag does not queue a profile swap per frame.
    var value by remember(stored) { mutableFloatStateOf(stored) }
    SliderRow(bound.label, generationText(bound, value, default), bound.consequence) {
        SteppedSlider(
            position = ((value - bound.min) / bound.step).roundToInt(),
            positions = bound.positions,
            onMove = { value = bound.at(it) },
            onRelease = { onChange(bound, value) }
        )
    }
}

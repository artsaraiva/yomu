package com.yomu.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomu.app.ui.theme.PaperButton
import com.yomu.app.ui.theme.PaperSurface
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams
import kotlin.math.roundToInt

/** "TOP_K" → "Top-k". Rendered from the bound so a new entry needs no UI literal (#196). */
internal val GenerationBound.label: String
    get() = name.lowercase().replace('_', '-').replaceFirstChar { it.uppercase() }

private fun GenerationBound.format(value: Float): String {
    val decimals = step.toString().substringAfter('.', "").trimEnd('0').length
    return "%.${decimals}f".format(value)
}

/** Snap a slider position to the declared step and keep it inside the range float error may leave. */
private fun GenerationBound.snap(value: Float): Float =
    (min + ((value - min) / step).roundToInt() * step).coerceIn(min, max)

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

@Composable
internal fun AdvancedGenerationPanel(
    generation: GenerationParams,
    overridden: Boolean,
    onChange: (GenerationBound, Float) -> Unit,
    onReset: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    PaperSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Advanced generation", fontWeight = FontWeight.Medium)
                    Text(
                        text = if (overridden) "Changed from the shipped defaults" else "Shipped defaults",
                        fontSize = 12.sp,
                        color = if (overridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            if (expanded) {
                Text(
                    text = "Sampler settings for the on-device LLM, shared by every model. Applied on the next capture.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GenerationBound.entries.forEach { bound ->
                    Spacer(Modifier.height(12.dp))
                    GenerationSlider(bound, bound.read(generation), onChange)
                }
                Spacer(Modifier.height(12.dp))
                PaperButton("Reset to defaults", onReset, enabled = overridden)
            }
        }
    }
}

@Composable
private fun GenerationSlider(bound: GenerationBound, stored: Float, onChange: (GenerationBound, Float) -> Unit) {
    // Local while dragging; saved once on release so a drag does not queue a profile swap per frame.
    var value by remember(stored) { mutableFloatStateOf(stored) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(bound.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("${bound.format(value)} (default ${bound.format(bound.default)})", fontSize = 14.sp)
    }
    Slider(
        value = value,
        onValueChange = { value = bound.snap(it) },
        onValueChangeFinished = { onChange(bound, value) },
        valueRange = bound.min..bound.max,
        steps = ((bound.max - bound.min) / bound.step).roundToInt() - 1,
        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    )
}

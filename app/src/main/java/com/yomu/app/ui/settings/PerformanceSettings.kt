package com.yomu.app.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yomu.app.translation.ResourceLimit
import com.yomu.app.ui.theme.PaperButton
import com.yomu.app.ui.theme.PaperSurface
import com.yomu.core.RuntimeLimits
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val REFRESH_MS = 1_000L

@Composable
fun PerformanceSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    // Sampled only while this screen is showing.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshResources()
            delay(REFRESH_MS)
        }
    }
    SettingsPage(SettingsSection.Performance.title, SettingsSection.Performance.description, onBack) {
        ResourceReadoutPanel(state.resources)
        ResourceLimitsPanel(state.resourceLimits, viewModel::setResourceLimit, viewModel::resetResourceLimits)
    }
}

@Composable
private fun ResourceReadoutPanel(readout: ResourceReadout?) {
    val context = LocalContext.current
    fun bytes(value: Long) = Formatter.formatShortFileSize(context, value)
    PaperSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Right now", style = MaterialTheme.typography.titleSmall)
            if (readout == null) {
                Text("Measuring…", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            ReadoutRow("App memory", "${bytes(readout.appPssBytes)}, including the loaded model")
            ReadoutRow("Native heap", bytes(readout.nativeHeapBytes))
            ReadoutRow("Phone memory free", "${bytes(readout.deviceAvailableBytes)} of ${bytes(readout.deviceTotalBytes)}")
            ReadoutRow("CPU", readout.cpuPercent?.let { "$it% of all cores" } ?: "Measuring…")
            ReadoutRow("Last page", readout.lastPageMs?.let { "Translated in $it ms" } ?: "No page translated yet")
        }
    }
}

@Composable
private fun ReadoutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResourceLimitsPanel(
    stored: Map<ResourceLimit, Int>,
    onChange: (ResourceLimit, Int) -> Unit,
    onReset: () -> Unit
) {
    PaperSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LimitSlider(ResourceLimit.THREADS, stored, "Threads", onChange) {
                "$it of ${RuntimeLimits.MAX_THREADS} cores (default ${ResourceLimit.THREADS.default}). " +
                    "Fewer keeps the phone responsive but translates slower. Applied on the next page."
            }
            LimitSlider(ResourceLimit.CONTEXT_TOKENS, stored, "Context size", onChange) {
                "$it tokens (default ${ResourceLimit.CONTEXT_TOKENS.default}). Smaller uses less memory, " +
                    "but a long page may be translated line by line. Applied on the next page."
            }
            LimitSlider(ResourceLimit.RAM_PERCENT, stored, "Memory ceiling", onChange) {
                "$it% of this phone's memory (default ${ResourceLimit.RAM_PERCENT.default}%). " +
                    "Models that need more are not offered."
            }
            PaperButton("Reset to defaults", onReset, enabled = ResourceLimit.entries.any { stored[it] != it.default })
        }
    }
}

@Composable
private fun LimitSlider(
    limit: ResourceLimit,
    stored: Map<ResourceLimit, Int>,
    label: String,
    onChange: (ResourceLimit, Int) -> Unit,
    describe: (Int) -> String
) {
    val options = limit.options
    val storedIndex = options.indexOf(stored.getValue(limit)).coerceAtLeast(0)
    // Local while dragging; saved once on release, like the other settings sliders.
    var index by remember(storedIndex) { mutableIntStateOf(storedIndex) }
    SettingRow(label, describe(options[index])) {
        if (options.size > 1) {
            Slider(
                value = index.toFloat(),
                onValueChange = { index = it.roundToInt() },
                onValueChangeFinished = { onChange(limit, options[index]) },
                valueRange = 0f..(options.size - 1).toFloat(),
                steps = options.size - 2,
                colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelType
import com.yomu.app.detection.DetectionThresholdStore
import com.yomu.app.translation.ResourceLimit
import com.yomu.app.ui.theme.PaperSurface

/**
 * Everything most readers never touch, on one page (#549): the reading models and the detection threshold, the
 * sampler, the model's resource limits and the live readout. Sections open in place and any number may be open.
 */
@Composable
fun AdvancedSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    // A recovered sampler value is news the reader has to see, so its section starts open.
    var open by rememberSaveable {
        mutableStateOf(setOfNotNull(AdvancedSection.Generation.takeIf { state.generationRecoveryWarning != null }))
    }
    val section: @Composable (AdvancedSection, (() -> Unit)?, @Composable ColumnScope.() -> Unit) -> Unit = { which, onReset, content ->
        Section(which, state, isOpen = which in open, onToggle = { open = if (which in open) open - which else open + which }, onReset, content)
    }
    SettingsPage(SettingsSection.Advanced.title, "The defaults suit most pages. Change these only when something looks off.", onBack) {
        PaperSurface(Modifier.fillMaxWidth()) {
            Column {
                section(AdvancedSection.BubbleFinding, viewModel::resetDetectionThreshold) {
                    ModelSlot(state, viewModel, ModelType.DETECTION, "Detector model", "Finds the speech bubbles on the page.")
                    ThresholdSlider(state.detectionThreshold, viewModel::setDetectionThreshold)
                }
                Divider(Modifier.padding(horizontal = 16.dp))
                section(AdvancedSection.TextReading, null) {
                    ModelSlot(state, viewModel, ModelType.OCR, "Reading model", "Reads the Japanese inside each bubble.")
                }
                Divider(Modifier.padding(horizontal = 16.dp))
                section(AdvancedSection.Generation, viewModel::resetGeneration) {
                    GenerationControls(state.generation, state.generationDefaults, state.generationRecoveryWarning, viewModel::setGeneration)
                }
            }
        }
        PaperSurface(Modifier.fillMaxWidth()) {
            section(AdvancedSection.Performance, viewModel::resetResourceLimits) {
                state.limitRefusal?.let { Text(it.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                ResourceLimit.entries.forEach { LimitSlider(it, state.resourceLimits, state.limitRefusal, viewModel::setResourceLimit) }
            }
        }
        PaperSurface(Modifier.fillMaxWidth()) {
            section(AdvancedSection.LiveUsage, null) { LiveUsage(state.resources, viewModel::refreshResources) }
        }
    }
}

@Composable
private fun Section(
    section: AdvancedSection,
    state: SettingsUiState,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onReset: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    CollapsibleSection(
        title = section.title,
        // Open, the controls show the state themselves, so the line says when a change takes hold instead.
        subtitle = section.appliedNote.takeIf { isOpen } ?: state.summary(section),
        open = isOpen,
        onToggle = onToggle,
        onReset = onReset,
        resetEnabled = state.canReset(section),
        content = content
    )
}

@Composable
private fun ThresholdSlider(stored: Float, onChange: (Float) -> Unit) {
    // Local while dragging; saved once on release, like the generation sliders.
    var value by remember(stored) { mutableFloatStateOf(stored) }
    SliderRow("Detection threshold", thresholdText(value), "Lower finds fainter bubbles, higher skips doubtful ones.") {
        SteppedSlider(
            position = DetectionThresholdStore.stepOf(value),
            positions = DetectionThresholdStore.STEPS,
            onMove = { value = DetectionThresholdStore.atStep(it) },
            onRelease = { onChange(value) }
        )
    }
}

@Composable
private fun LimitSlider(limit: ResourceLimit, stored: Map<ResourceLimit, Int>, refusal: LimitRefusal?, onChange: (ResourceLimit, Int) -> Unit) {
    val options = limit.options
    val storedIndex = options.indexOf(stored.getValue(limit)).coerceAtLeast(0)
    // Local while dragging; saved once on release. Keyed on the refusal so a refused change snaps back.
    var index by remember(storedIndex, refusal) { mutableIntStateOf(storedIndex) }
    SliderRow(limit.label, limitText(limit, options[index]), limit.consequence) {
        // A one-core phone has a single thread count, and a slider needs two ends.
        if (options.size > 1) {
            SteppedSlider(index, options.size - 1, onMove = { index = it }, onRelease = { onChange(limit, options[index]) })
        }
    }
}

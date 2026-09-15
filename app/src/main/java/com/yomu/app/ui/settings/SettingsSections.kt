package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelType
import com.yomu.app.ui.theme.*

@Composable
private fun SectionPage(section: SettingsSection, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    SettingsPage(section.title, section.description, onBack, content)

@Composable
fun TranslationSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    SectionPage(SettingsSection.Translation, onBack) {
        TranslationModelCard(
            state = state,
            onDownload = viewModel::downloadModel,
            onDelete = viewModel::deleteModel,
            onCancel = viewModel::cancelDownload,
            onSelectLlm = viewModel::setLlmModel
        )
        state.recoveryWarning?.let { RecoveryWarning(it) }
        AdvancedGenerationPanel(
            generation = state.generation,
            overridden = state.generationOverridden,
            onChange = viewModel::setGeneration,
            onReset = viewModel::resetGeneration
        )
    }
}

@Composable
fun PipelineSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    SectionPage(SettingsSection.Pipeline, onBack) {
        listOf(
            Triple(ModelType.DETECTION, "Detection", "Finds the speech bubbles on the page."),
            Triple(ModelType.OCR, "OCR", "Reads the Japanese inside each bubble.")
        ).forEach { (type, title, description) ->
            PaperSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.models.filter { it.type == type }.forEach { model ->
                        Column {
                            ModelStatusRow(
                                model = model,
                                isDownloading = model.id in state.downloads,
                                progress = state.downloads[model.id] ?: 0,
                                onDownload = { viewModel.downloadModel(model.id) },
                                onDelete = { viewModel.deleteModel(model.id) },
                                onCancel = { viewModel.cancelDownload(model.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypesettingSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    SectionPage(SettingsSection.Typesetting, onBack) {
        PaperSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SettingRow("Font size", "${(state.fontSizeScale * 100).toInt()}% of the fitted size") {
                    Slider(
                        value = state.fontSizeScale,
                        onValueChange = viewModel::setFontSizeScale,
                        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun AppearanceSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var themeConfirmation by remember { mutableStateOf<String?>(null) }
    SectionPage(SettingsSection.Appearance, onBack) {
        PaperSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SettingRow("Theme", "Paper colours for the app and the overlay.") {
                    Column {
                        listOf("system" to "Follow system", "day" to "Day paper", "night" to "Night paper").forEach { (mode, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.theme == mode, onClick = {
                                    viewModel.setTheme(mode)
                                    themeConfirmation = "$label selected"
                                })
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        themeConfirmation?.let { PaperSuccess(it) { themeConfirmation = null } }
    }
}

package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yomu.app.db.entities.ModelType
import com.yomu.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var themeConfirmation by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text("Translation", style = MaterialTheme.typography.titleLarge)
        Text("How Yomu turns Japanese into English, on your device.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        TranslationModelCard(
            state = state,
            onDownload = { viewModel.downloadModel(it) },
            onDelete = { viewModel.deleteModel(it) },
            onCancel = { viewModel.cancelDownload(it) },
            onSelectLlm = { viewModel.setLlmModel(it) }
        )
        Spacer(modifier = Modifier.height(8.dp))

        state.recoveryWarning?.let {
            RecoveryWarning(it)
            Spacer(modifier = Modifier.height(8.dp))
        }
        AdvancedGenerationPanel(
            generation = state.generation,
            overridden = state.generationOverridden,
            onChange = viewModel::setGeneration,
            onReset = viewModel::resetGeneration
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Reading", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        val visionModels = state.models.filter { it.type == ModelType.VISION }
        if (visionModels.isNotEmpty()) {
            PaperSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Reading models", fontWeight = FontWeight.Medium)
                    Text(
                        text = "The parts that find the speech bubbles and read the Japanese. Downloaded once.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    visionModels.forEach { model ->
                        ModelStatusRow(
                            model = model,
                            isDownloading = model.id in state.downloads,
                            progress = state.downloads[model.id] ?: 0,
                            onDownload = { viewModel.downloadModel(model.id) },
                            onDelete = { viewModel.deleteModel(model.id) },
                            onCancel = { viewModel.cancelDownload(model.id) }
                        )
                        if (model != visionModels.last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        PaperSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleLarge)
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
        themeConfirmation?.let { PaperSuccess(it) { themeConfirmation = null } }
        Spacer(Modifier.height(20.dp))

        Text("Font Size", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(state.fontSizeScale * 100).toInt()}%",
            fontSize = 14.sp
        )
        Slider(
            value = state.fontSizeScale,
            onValueChange = { viewModel.setFontSizeScale(it) },
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant),
            valueRange = 0.5f..2.0f,
            steps = 14,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Divider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Yomu v1.0",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.LlmModelOption
import com.yomu.app.translation.LlmModelTier
import com.yomu.app.translation.TranslationEngineType
import com.yomu.core.Constants
import com.yomu.core.toFileSizeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text("Translation Mode", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("local", "hybrid", "cloud").forEach { mode ->
                FilterChip(
                    selected = state.translationMode == mode,
                    onClick = { viewModel.setTranslationMode(mode) },
                    label = { Text(mode.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Translation Engine", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TranslationEngineType.entries.forEach { engine ->
                FilterChip(
                    selected = state.selectedEngine == engine,
                    onClick = { viewModel.setTranslationEngine(engine) },
                    label = { Text(engine.label, fontSize = 12.sp) }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.selectedEngine.description,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Models", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        val visionModels = state.models.filter { it.type == ModelType.VISION }
        if (visionModels.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Required Models", fontWeight = FontWeight.Medium)
                    Text(
                        text = "Needed for bubble detection and OCR",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    visionModels.forEach { model ->
                        ModelStatusRow(
                            model = model,
                            isDownloading = state.downloadingId == model.id,
                            progress = state.downloadProgress,
                            onDownload = { viewModel.downloadModel(model.id) },
                            onDelete = { viewModel.deleteModel(model.id) }
                        )
                        if (model != visionModels.last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        TranslationEngineType.entries.forEach { engine ->
            EngineModelCard(
                engine = engine,
                state = state,
                onDownload = { viewModel.downloadModel(it) },
                onDelete = { viewModel.deleteModel(it) },
                onSelectLlm = { viewModel.setLlmModel(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Font Size", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(state.fontSizeScale * 100).toInt()}%",
            fontSize = 14.sp
        )
        Slider(
            value = state.fontSizeScale,
            onValueChange = { viewModel.setFontSizeScale(it) },
            valueRange = 0.5f..2.0f,
            steps = 14,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Language", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${state.sourceLanguage.uppercase()} → ${state.targetLanguage.uppercase()}",
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = "Japanese → English (Phase 1)",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto-detect manga pages", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Switch(
                checked = state.autoDetect,
                onCheckedChange = { viewModel.setAutoDetect(it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "When enabled, the floating button will pulse when manga is detected on screen.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun EngineModelCard(
    engine: TranslationEngineType,
    state: SettingsUiState,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSelectLlm: (LlmModelOption) -> Unit
) {
    val models = state.models
    val downloadingId = state.downloadingId
    val downloadProgress = state.downloadProgress
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(engine.label, fontWeight = FontWeight.Medium)
            Text(
                text = engine.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (engine) {
                TranslationEngineType.ML_KIT -> {
                    val model = models.find { it.id == Constants.ML_KIT_JA_EN_MODEL_ID }
                    if (model != null) {
                        ModelStatusRow(
                            model = model,
                            isDownloading = downloadingId == model.id,
                            progress = downloadProgress,
                            onDownload = { onDownload(model.id) },
                            onDelete = { onDelete(model.id) }
                        )
                    } else {
                        Text(
                            text = "Checking status…",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TranslationEngineType.OPUS_MT -> {
                    val model = models.find { it.id == Constants.OPUS_MT_MODEL_ID }
                    if (model != null) {
                        ModelStatusRow(
                            model = model,
                            isDownloading = downloadingId == model.id,
                            progress = downloadProgress,
                            onDownload = { onDownload(model.id) },
                            onDelete = { onDelete(model.id) }
                        )
                    } else {
                        Text(
                            text = "~111MB OPUS-MT JA→EN, INT8 quantized",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TranslationEngineType.LLM -> {
                    Text(
                        text = "Pick which model fills the translation slot. The default is safe on every device.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    state.llmModels.forEach { option ->
                        LlmModelRow(
                            option = option,
                            selected = option.id == state.selectedLlmModelId,
                            canRun = state.canRun(option),
                            model = models.find { it.id == option.id },
                            isDownloading = downloadingId == option.id,
                            progress = downloadProgress,
                            onSelect = { onSelectLlm(option) },
                            onDownload = { onDownload(option.id) },
                            onDelete = { onDelete(option.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LlmModelRow(
    option: LlmModelOption,
    selected: Boolean,
    canRun: Boolean,
    model: ModelEntity?,
    isDownloading: Boolean,
    progress: Int,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val hosted = option.tier == LlmModelTier.HOSTED
    val selectable = hosted && canRun
    val subtitle = buildString {
        append(option.sizeBytes.toFileSizeString())
        append(if (hosted) " · Hosted" else " · HuggingFace sign-in required (coming soon)")
        if (hosted && !canRun) append(" · Won't fit this device")
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = selectable
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.displayName,
                    fontSize = 13.sp,
                    color = if (selectable) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = if (hosted && !canRun) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hosted && model != null) {
                when {
                    isDownloading -> Text("$progress%", fontSize = 11.sp)
                    model.status == ModelStatus.READY -> OutlinedButton(onClick = onDelete) {
                        Text("Delete", fontSize = 12.sp)
                    }
                    else -> Button(onClick = onDownload) {
                        Text(
                            if (model.status == ModelStatus.ERROR) "Retry" else "Download",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStatusRow(
    model: ModelEntity,
    isDownloading: Boolean,
    progress: Int,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            when (model.status) {
                ModelStatus.READY -> Text(
                    text = if (model.id == Constants.ML_KIT_JA_EN_MODEL_ID) "Downloaded (baseline)" else "Ready",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ModelStatus.DOWNLOADING -> Text(
                    text = "Downloading $progress%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ModelStatus.AVAILABLE -> Text(
                    text = "${model.fileSize.toFileSizeString()} — Not downloaded",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ModelStatus.ERROR -> Text(
                    text = "Error — tap to retry",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
                else -> Text(
                    text = model.status.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            isDownloading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Text("$progress%", fontSize = 11.sp)
                }
            }
            model.status == ModelStatus.READY -> {
                OutlinedButton(onClick = onDelete) {
                    Text("Delete", fontSize = 12.sp)
                }
            }
            else -> {
                Button(onClick = onDownload) {
                    Text(
                        if (model.status == ModelStatus.ERROR) "Retry" else "Download",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    if (isDownloading) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

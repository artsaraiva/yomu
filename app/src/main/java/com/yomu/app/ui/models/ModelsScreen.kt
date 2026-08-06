package com.yomu.app.ui.models

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
import com.yomu.core.Constants
import com.yomu.core.toFileSizeString

@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AI Models",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Download models to translate manga on your device.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (state.models.isEmpty() && state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.sections.forEach { section ->
                    if (section.models.isNotEmpty()) {
                        Text(
                            text = section.capability.label(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        section.models.forEach { model ->
                            ModelCard(
                                model = model,
                                isDownloading = state.downloadingId == model.id,
                                progress = state.downloadProgress,
                                onDownload = { viewModel.downloadModel(model.id) },
                                onDelete = { viewModel.deleteModel(model.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelEntity,
    isDownloading: Boolean,
    progress: Int,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, fontWeight = FontWeight.Medium)
                    if (model.id == Constants.ML_KIT_JA_EN_MODEL_ID) {
                        Text(
                            text = "Temporary Phase 0 baseline — local LLM translation not yet functional.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${model.fileSize.toFileSizeString()} — ${model.status.name}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (model.status) {
                    ModelStatus.AVAILABLE -> Button(onClick = onDownload) {
                        Text("Download", fontSize = 12.sp)
                    }
                    ModelStatus.DOWNLOADING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        val progressText = if (model.type == ModelType.TRANSLATION) {
                            "Downloading…"
                        } else {
                            "$progress%"
                        }
                        Text(progressText, fontSize = 11.sp)
                    }
                    ModelStatus.READY -> OutlinedButton(onClick = onDelete) {
                        Text("Delete", fontSize = 12.sp)
                    }
                    ModelStatus.ERROR -> Button(onClick = onDownload) {
                        Text("Retry", fontSize = 12.sp)
                    }
                    else -> {
                        Text(model.status.name, fontSize = 12.sp)
                    }
                }
            }

            if (isDownloading && model.type != ModelType.TRANSLATION) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

private fun ModelCapability.label(): String {
    return when (this) {
        ModelCapability.BUBBLE_DETECTION -> "Bubble Detection"
        ModelCapability.OCR -> "OCR Encoder + Decoder"
        ModelCapability.TRANSLATION_ENGINE -> "Translation Engine"
    }
}

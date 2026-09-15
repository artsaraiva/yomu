package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomu.app.R
import com.yomu.app.translation.LlmModelOption
import com.yomu.app.ui.theme.*

@Composable
internal fun TranslationModelCard(
    state: SettingsUiState,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: (String) -> Unit,
    onSelectLlm: (LlmModelOption) -> Unit
) {
    val models = state.models
    val downloads = state.downloads
    PaperSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.translation_model_label), fontWeight = FontWeight.Medium)
            Text(
                text = stringResource(R.string.translation_model_description),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    isDownloading = option.id in downloads,
                    progress = downloads[option.id] ?: 0,
                    onSelect = { onSelectLlm(option) },
                    onDownload = { onDownload(option.id) },
                    onDelete = { onDelete(option.id) },
                    onCancel = { onCancel(option.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

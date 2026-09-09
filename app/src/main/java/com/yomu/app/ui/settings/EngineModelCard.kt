package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomu.app.translation.LlmModelOption
import com.yomu.app.translation.LlmModelTier
import com.yomu.app.translation.TranslationEngineType
import com.yomu.app.ui.theme.*
import com.yomu.core.Constants

@Composable
internal fun EngineModelCard(
    engine: TranslationEngineType,
    state: SettingsUiState,
    onDownload: (String) -> Unit,
    onDownloadHf: (LlmModelOption) -> Unit,
    onDelete: (String) -> Unit,
    onSelectLlm: (LlmModelOption) -> Unit,
    onHfSignIn: () -> Unit,
    onHfSignOut: () -> Unit
) {
    val models = state.models
    val downloadingId = state.downloadingId
    val downloadProgress = state.downloadProgress
    PaperSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(engine.labelRes), fontWeight = FontWeight.Medium)
            Text(
                text = stringResource(engine.descriptionRes),
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
                        PaperLoading("Checking model status…")
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
                            hfSignedIn = state.hfSignedIn,
                            model = models.find { it.id == option.id },
                            isDownloading = downloadingId == option.id,
                            progress = downloadProgress,
                            onSelect = { onSelectLlm(option) },
                            onDownload = {
                                if (option.tier == LlmModelTier.HOSTED) onDownload(option.id)
                                else onDownloadHf(option)
                            },
                            onDelete = { onDelete(option.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // Only worth showing when a gated model actually needs it — none in the catalog
                    // today, so this stays hidden until an HF_AUTH entry is added.
                    if (state.llmModels.any { it.tier == LlmModelTier.HF_AUTH }) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HfSignInRow(signedIn = state.hfSignedIn, onSignIn = onHfSignIn, onSignOut = onHfSignOut)
                    }
                }
            }
        }
    }
}

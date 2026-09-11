package com.yomu.app.ui.settings

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.TranslationEngineType
import com.yomu.app.ui.theme.*
import com.yomu.core.TranslationPromptMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var themeConfirmation by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val hfSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.completeHfSignIn(result.data) }

    state.gatedTermsUrl?.let { termsUrl ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissGatedPrompt() },
            title = { Text("Accept the model's terms") },
            text = {
                Text(
                    "This model is gated. Open its HuggingFace page, accept the licence terms with " +
                        "your account, then download again."
                )
            },
            confirmButton = {
                PaperAction(onClick = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(termsUrl)))
                    viewModel.dismissGatedPrompt()
                }) { Text("Open page") }
            },
            dismissButton = {
                PaperAction(onClick = { viewModel.dismissGatedPrompt() }) { Text("Dismiss") }
            }
        )
    }

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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TranslationEngineType.entries.forEach { engine ->
                FilterChip(
                    selected = state.selectedEngine == engine,
                    onClick = { viewModel.setTranslationEngine(engine) },
                    border = FilterChipDefaults.filterChipBorder(borderColor = MaterialTheme.colorScheme.onSurfaceVariant, selectedBorderColor = MaterialTheme.colorScheme.primary),
                    leadingIcon = if (state.selectedEngine == engine) { { Icon(Icons.Default.Check, contentDescription = "Selected") } } else null,
                    label = { Text(stringResource(engine.labelRes), fontSize = 12.sp) }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(state.selectedEngine.descriptionRes),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        val selectedLlm = state.llmModels.firstOrNull { it.id == state.selectedLlmModelId }
        // promptMode is read only on the per-line path, so a batch model would render a switch that
        // does nothing (ADR-0013). The batch call subsumes it anyway: it carries the whole page.
        if (state.selectedEngine == TranslationEngineType.LLM &&
            selectedLlm?.promptMode == TranslationPromptMode.TRANSLATION_ONLY &&
            !selectedLlm.idKeyedBatch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use surrounding dialogue (experimental)", modifier = Modifier.weight(1f))
                PaperSwitch(checked = state.captureContext, onCheckedChange = viewModel::setCaptureContext)
            }
            Text(
                "Uses nearby text in the current capture. Does not remember previous pages. " +
                    "May take longer; experimental processing could cause the app to close unexpectedly.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(20.dp))
        }

        TranslationEngineType.entries.forEach { engine ->
            EngineModelCard(
                engine = engine,
                state = state,
                onDownload = { viewModel.downloadModel(it) },
                onDownloadHf = { viewModel.downloadHfModel(it) },
                onDelete = { viewModel.deleteModel(it) },
                onSelectLlm = { viewModel.setLlmModel(it) },
                onHfSignIn = { hfSignInLauncher.launch(viewModel.hfAuthorizeIntent()) },
                onHfSignOut = { viewModel.signOutHf() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Reading", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        val visionModels = state.models.filter { it.type == ModelType.VISION }
        if (visionModels.isNotEmpty()) {
            PaperSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Reading models", fontWeight = FontWeight.Medium)
                    Text(
                        text = "The parts that find the speech bubbles and read the Japanese. Downloaded once, used with every engine.",
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto-detect manga pages", fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PaperSwitch(
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

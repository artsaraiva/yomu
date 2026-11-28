package com.yomu.app.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.AppDestination
import com.yomu.app.ui.settings.SlotPickerSheet
import com.yomu.app.ui.theme.*
import com.yomu.core.TargetLanguage

private const val PERMISSION_TITLE = "Let Yomu draw over your manga app"

/**
 * First-run setup as one stepper (#547): the language, the reading kit, then permission to draw over other apps. The
 * step whose turn it is opens in place; once all three are done the screen's one action starts reading.
 */
@Composable
fun SetupScreen(state: HomeUiState, viewModel: HomeViewModel, onStartReading: () -> Unit, onOpenDestination: (AppDestination) -> Unit) {
    val context = LocalContext.current
    val phases = state.setupPhases()
    val allDone = phases.values.all { it == SetupPhase.Done }
    val textColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    var changingTranslator by rememberSaveable { mutableStateOf(false) }
    // The kit's own live region goes when its step collapses, so the end of the download is announced from here.
    val view = LocalView.current
    val kitDone = phases.getValue(SetupStepId.Kit) == SetupPhase.Done
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(state.setupRunning, kitDone) {
        if (wasRunning && kitDone) view.announceForAccessibility("Reading kit downloaded")
        wasRunning = state.setupRunning
    }
    // Providers and Settings change which translator takes the pages, and with it whether the phone model is needed.
    LaunchedEffect(Unit) { viewModel.refreshSlots() }
    state.translatorConfirm?.let {
        TranslatorConfirmDialog(it.confirm, { viewModel.acceptTranslatorConfirm()?.let(onOpenDestination) }, viewModel::declineTranslatorConfirm)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            YomuMark(48.dp)
            Text("Let's get you reading", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(if (state.setupSkipsPhoneModel) "Three steps." else "Three steps. After that, translation runs on this phone.")
        }
        when (phases.getValue(SetupStepId.Language)) {
            SetupPhase.Done -> DoneStep("Translate into", state.setupLanguage.promptName) {
                // Not mid-download: another language can swap the translator being fetched.
                if (!state.setupRunning) {
                    TextButton(colors = textColors, onClick = viewModel::reopenSetupLanguage, modifier = Modifier.semantics { contentDescription = "Change language" }) { Text("Change") }
                }
            }
            else -> LanguageStep(state, viewModel::chooseSetupLanguage, viewModel::confirmSetupLanguage)
        }
        when (phases.getValue(SetupStepId.Kit)) {
            SetupPhase.Done -> DoneStep("Reading kit", state.setupKit().progressText())
            SetupPhase.Active -> KitStep(
                state, viewModel::prepareSetup, { changingTranslator = true }, viewModel::setupWith,
                { group, entry -> viewModel.pickTranslatorEntry(group, entry)?.let(onOpenDestination) }
            )
            SetupPhase.Later -> LaterStep(2, KIT_TITLE, KIT_CAPTION)
        }
        when (phases.getValue(SetupStepId.Permission)) {
            SetupPhase.Done -> DoneStep("Draw over your manga app", "Allowed")
            SetupPhase.Active -> ActiveStep(3, PERMISSION_TITLE, "Yomu draws its button and translations on top of the page you're reading.") {
                PaperButton("Open Android settings", {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }, Modifier.fillMaxWidth())
            }
            SetupPhase.Later -> LaterStep(3, PERMISSION_TITLE, "Opens Android settings. Then you can start reading.")
        }
        if (allDone) {
            PaperAction(onClick = { if (viewModel.completeSetup()) onStartReading() }, accented = true, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Start reading", style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(
                "Android asks to share your screen each time you turn reading on.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // With every step done there is nothing to finish later, so the quiet way out completes setup too.
            if (allDone) TextButton(colors = textColors, onClick = { viewModel.completeSetup() }) { Text("Go to Home") }
            else TextButton(colors = textColors, onClick = viewModel::dismissSetup) { Text("Finish later") }
        }
    }
    if (changingTranslator) {
        SlotPickerSheet(
            title = "Translator",
            deliverables = state.deliverables[ModelType.LLM].orEmpty(),
            selectedId = state.chosenIds[ModelType.LLM],
            pendingId = null,
            statuses = state.models.associate { it.id to it.status },
            downloads = emptyMap(),
            fits = state::fits,
            needsSlowWarning = state::needsSlowWarning,
            languageBlock = state::languageBlock,
            phoneModelIdle = !state.phoneTranslates,
            onPick = {
                viewModel.pickSetupModel(ModelType.LLM, it)
                changingTranslator = false
            },
            onCancel = {},
            onDelete = viewModel::deleteModel,
            onDismiss = { changingTranslator = false }
        )
    }
}

/** Setup's open first step: the language comes before the download because it decides which translator the kit carries. */
@Composable
private fun LanguageStep(state: HomeUiState, onChoose: (TargetLanguage) -> Unit, onConfirm: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ActiveStep(1, "Translate into", "Yomu reads Japanese. Pick the language you read in.") {
        Column {
            TargetLanguage.entries.forEach { language ->
                val reason = state.languageUnavailable(language)
                val selected = language == state.setupLanguage
                Row(
                    Modifier.fillMaxWidth().selectable(selected, enabled = reason == null, role = Role.RadioButton) { onChoose(language) }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(selected, onClick = null, enabled = reason == null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onSurface))
                    Column {
                        Text(language.promptName, style = MaterialTheme.typography.titleSmall, color = if (reason == null) LocalContentColor.current else muted)
                        reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                    }
                }
            }
        }
        PaperButton("Continue", onConfirm, Modifier.fillMaxWidth())
    }
}

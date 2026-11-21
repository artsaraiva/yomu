package com.yomu.app.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelType
import com.yomu.app.ui.settings.SlotPickerSheet
import com.yomu.app.ui.theme.*

@Composable
fun SetupScreen(state: HomeUiState, viewModel: HomeViewModel) {
    val context = LocalContext.current
    val rows = state.setupRows()
    val steps = state.setupSteps()
    val downloaded = steps.first().done
    val running = state.setupRunning
    var changing by rememberSaveable { mutableStateOf<ModelType?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Setup", style = MaterialTheme.typography.headlineLarge)
        SetupChecklist(steps)
        if (!state.setupDownloadsReady) {
            PaperSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    rows.forEach { row -> SetupModelRowView(row, running, canChange = !running) { changing = row.type } }
                }
            }
        }
        when {
            state.setupError -> PaperError("Download failed. Check your connection and storage.", viewModel::prepareSetup)
            !state.setupStarted -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.onMeteredNetwork && !downloaded) Text("You're on mobile data.")
                PaperButton(
                    if (downloaded) "Continue" else "Download",
                    viewModel::prepareSetup,
                    Modifier.fillMaxWidth(),
                    enabled = rows.none { it.state == SetupModelState.TooBig }
                )
            }
            running -> Text("Keep Yomu open until this finishes.", style = MaterialTheme.typography.bodySmall)
            state.readiness != Readiness.Ready -> SetupCard("Allow display over other apps", "Yomu draws its button and translations over your manga app.", "Open settings") {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }
            else -> SetupCard("Ready", "Android asks to share your screen each time you turn reading on.", "Go to Home", viewModel::completeSetup)
        }
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = viewModel::dismissSetup) { Text("Back to Home") }
    }
    rows.firstOrNull { it.type == changing }?.let { row ->
        SlotPickerSheet(
            title = row.slot,
            deliverables = state.deliverables[row.type].orEmpty(),
            selectedId = state.chosenIds[row.type],
            pendingId = null,
            statuses = state.models.associate { it.id to it.status },
            downloads = emptyMap(),
            fits = state::fits,
            needsSlowWarning = state::needsSlowWarning,
            languageBlock = state::languageBlock,
            onPick = {
                viewModel.pickSetupModel(row.type, it)
                changing = null
            },
            onCancel = {},
            onDelete = viewModel::deleteModel,
            onDismiss = { changing = null }
        )
    }
}

@Composable
private fun SetupChecklist(steps: List<SetupStep>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Row(
                Modifier.semantics(mergeDescendants = true) { stateDescription = if (step.done) "Done" else "Not done" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (step.done) Icon(Icons.Default.Check, contentDescription = null)
                    else Text("${index + 1}.", style = MaterialTheme.typography.titleMedium)
                }
                Text(step.label, style = MaterialTheme.typography.titleMedium, color = if (step.done) muted else LocalContentColor.current)
            }
        }
    }
}

@Composable
private fun SetupModelRowView(row: SetupModelRow, running: Boolean, canChange: Boolean, onChange: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val alarming = row.state == SetupModelState.Failed || row.state == SetupModelState.TooBig
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = row.announcement()
                    liveRegion = LiveRegionMode.Polite
                }
            ) {
                Text(row.slot, style = MaterialTheme.typography.labelMedium, color = muted)
                Text(row.deliverable.name, style = MaterialTheme.typography.titleSmall)
                Text(row.statusText(running), style = MaterialTheme.typography.bodySmall, color = if (alarming) MaterialTheme.colorScheme.error else muted)
            }
            if (canChange) {
                TextButton(onClick = onChange, modifier = Modifier.semantics { contentDescription = "Change ${row.slot} model" }) { Text("Change") }
            }
        }
        (row.state as? SetupModelState.Downloading)?.let {
            LinearProgressIndicator(
                progress = if (it.totalBytes > 0) it.bytesDownloaded.toFloat() / it.totalBytes else 0f,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                // The default track is the card's own colour, which hides how much is left.
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun SetupCard(title: String, body: String, action: String, onAction: () -> Unit) {
    PaperSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body)
            PaperButton(action, onAction)
        }
    }
}

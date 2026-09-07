package com.yomu.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yomu.app.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onRequestScreenCapture: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Home", style = MaterialTheme.typography.headlineLarge)
        PaperAction(
            onClick = onRequestScreenCapture,
            enabled = !state.isServiceRunning,
            accented = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("あ → A", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (state.isServiceRunning) "Ready to read" else "Start reading",
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            if (state.isServiceRunning) "Tap the floating button to translate your screen."
            else "Translate manga on your screen, in the app you already read with.",
            style = MaterialTheme.typography.bodyMedium
        )
        state.confirmation?.let { PaperSuccess(it, viewModel::dismissConfirmation) }
        if (state.serviceError) {
            PaperError("Couldn't start the reading overlay. Check display-over-apps permission and try again.") {
                viewModel.dismissServiceError()
                onRequestScreenCapture()
            }
        }
        Text("Continue reading", style = MaterialTheme.typography.titleLarge)
        when {
            state.historyLoading -> PaperLoading("Finding your last translation…")
            state.historyError -> PaperError("Couldn't load your last translation.", viewModel::loadRecentHistory)
            state.latestTranslation == null -> PaperEmpty("Your next reading session starts here.", "Start reading", onRequestScreenCapture)
            else -> PaperSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.latestTranslation?.translatedText.orEmpty().take(100), maxLines = 3, style = MaterialTheme.typography.bodyLarge)
                    Text("Last translation · ${state.latestTranslation?.bubbleCount} bubbles", style = MaterialTheme.typography.bodySmall)
                    PaperButton("Continue reading", onRequestScreenCapture, enabled = !state.isServiceRunning)
                }
            }
        }
        PaperSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reading overlay", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    PaperSwitch(checked = state.isServiceRunning, onCheckedChange = { enabled ->
                        if (enabled) onRequestScreenCapture() else viewModel.stopService()
                    })
                }
                Divider()
                Text("Mode: ${state.translationMode.replaceFirstChar { it.uppercase() }}")
                Text("Pages today: ${state.pagesTranslatedToday}")
                Text("Models: ${state.modelStatus}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

package com.yomu.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomu.app.ui.theme.*

/** Every saved page, newest first, and the one place Recent is cleared from. */
@Composable
fun RecentScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val openRecent = rememberOpenRecent()
    var confirmClear by remember { mutableStateOf(false) }
    val quiet = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear recent translations?") },
            text = { Text("This removes your saved translations from this device.") },
            confirmButton = { TextButton(colors = quiet, onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("Clear") } },
            dismissButton = { TextButton(colors = quiet, onClick = { confirmClear = false }) { Text("Keep") } }
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Text("Recent", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                if (state.translations.isNotEmpty()) TextButton(colors = quiet, onClick = { confirmClear = true }) { Text("Clear") }
            }
        }
        state.confirmation?.let { message -> item { PaperSuccess(message, viewModel::dismissConfirmation) } }
        if (state.clearError) item {
            PaperError("Couldn't clear Recent. Your translations are still saved.", viewModel::clearHistory)
        }
        when {
            state.historyLoading -> item { PaperLoading("Loading recent translations…") }
            state.historyError -> item { PaperError("Couldn't load recent translations.", viewModel::loadRecentHistory) }
            state.translations.isEmpty() -> item { Text("No saved translations.", style = MaterialTheme.typography.bodyMedium) }
            else -> items(state.translations, key = { it.id }) { translation ->
                PaperSurface(Modifier.fillMaxWidth()) { RecentRow(translation) { openRecent.open(translation) } }
            }
        }
    }
    openRecent.Sheet(state.translations)
}

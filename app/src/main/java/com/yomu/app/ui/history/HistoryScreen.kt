package com.yomu.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yomu.app.ui.theme.*

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onRequestScreenCapture: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                if (!state.isEmpty) PaperButton("Clear", viewModel::clearHistory)
            }
        }
        if (state.cleared) item { PaperSuccess("History cleared", viewModel::dismissConfirmation) }
        if (state.clearError) item { PaperError("Couldn't clear history. Your translations are still saved.", viewModel::clearHistory) }
        when {
            state.loading -> item { PaperLoading("Loading translations…") }
            state.loadError -> item { PaperError("Couldn't load your translations.", viewModel::loadHistory) }
            state.isEmpty -> item { PaperEmpty("No translations yet. Use the floating button to translate manga on your screen.", "Start reading", onRequestScreenCapture) }
            else -> items(state.translations, key = { it.id }) { translation ->
                PaperSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(translation.translatedText.take(100), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text("${translation.bubbleCount} bubbles · ${translation.translationTimeMs}ms", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

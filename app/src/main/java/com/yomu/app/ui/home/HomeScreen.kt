package com.yomu.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yomu.app.ui.settings.SaveToRecentViewModel
import com.yomu.app.translation.AppDestination
import com.yomu.app.ui.settings.SettingsSection
import com.yomu.app.ui.theme.*
import kotlinx.coroutines.launch

private const val RECENT_ON_HOME = 3

/**
 * Home: Yomu's name over the reading control, the language and translator rows, and the latest saved pages. Settings
 * is the corner button and the whole of Recent is behind See all, so Home needs no navigation bar (#546).
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    saveToRecent: SaveToRecentViewModel,
    onRequestScreenCapture: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenRecent: () -> Unit,
    onOpenDestination: (AppDestination) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val saving by saveToRecent.enabled.collectAsState()
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val openRecent = rememberOpenRecent()
    // Home leaves composition while Settings is open, and returning there doesn't resume the activity.
    LaunchedEffect(Unit) { viewModel.refreshSlots() }
    state.translatorConfirm?.let {
        TranslatorConfirmDialog(it.confirm, { viewModel.acceptTranslatorConfirm()?.let(onOpenDestination) }, viewModel::declineTranslatorConfirm)
    }
    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                YomuMark(36.dp)
                Text("Yomu", style = MaterialTheme.typography.headlineMedium, maxLines = 1, modifier = Modifier.weight(1f).semantics { heading() })
                state.homeAction?.let { HomeAction(it.label) { onOpenDestination(it.destination) } }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        }
        item { ReadingHero(state, onRequestScreenCapture, viewModel::stopService, viewModel::openSetup) }
        state.confirmation?.let { message -> item { PaperSuccess(message, viewModel::dismissConfirmation) } }
        if (state.serviceError) item {
            PaperError("Couldn't start reading. Try the reading control again.", viewModel::dismissServiceError)
        }
        if (state.readingStatus != ReadingStatus.NotReady) item {
            ReadingChoices(
                state,
                viewModel::pickTranslationModel,
                { group, entry -> viewModel.pickTranslatorEntry(group, entry)?.let(onOpenDestination) },
                onOpenSection,
                viewModel::chooseLanguage
            )
        }
        // Shown while saving is off even before any page was saved, so Home says why none is kept.
        if (state.historyLoading || state.historyError || state.hasRead || !saving) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        // It counts saved pages, so while saving is off it would undercount what was translated today (#454).
                        if (saving) Text(
                            pagesTodayLabel(state.pagesTranslatedToday),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.translations.isNotEmpty()) {
                        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = onOpenRecent) { Text("See all") }
                    }
                }
            }
            if (!saving) item {
                Text("Saving is off, so new pages aren't added. You can switch it back on under Settings > Translation.", style = MaterialTheme.typography.bodySmall)
            }
            when {
                state.historyLoading -> item { PaperLoading("Loading recent translations…") }
                state.historyError -> item { PaperError("Couldn't load recent translations.", viewModel::loadRecentHistory) }
                // Not while saving is off: the next translation won't appear, and the note above says why.
                state.translations.isEmpty() -> if (saving) item {
                    PaperEmpty("Your next translation will appear here.", "Back to reading") {
                        scope.launch { scroll.animateScrollToItem(0) }
                    }
                }
                else -> item { RecentCard(state.translations.take(RECENT_ON_HOME), openRecent::open) }
            }
        }
    }
    openRecent.Sheet(state.translations)
}

/** A translator's Home action: quiet accent text in the top row, so the reading control stays the strong one (#543). */
@Composable
private fun HomeAction(label: String, onClick: () -> Unit) {
    val accent = Color(LocalYomuColors.current.accent)
    TextButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = accent)
    ) {
        Icon(Sparkle, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

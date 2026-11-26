package com.yomu.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yomu.app.ui.settings.SaveToRecentViewModel
import com.yomu.app.translation.AppDestination
import com.yomu.app.ui.settings.SettingsSection
import com.yomu.app.ui.theme.*
import com.yomu.core.TargetLanguage
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    saveToRecent: SaveToRecentViewModel,
    onRequestScreenCapture: () -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onOpenDestination: (AppDestination) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val saving by saveToRecent.enabled.collectAsState()
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val readingStatus = state.readingStatus
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear recent translations?") },
            text = { Text("This removes your saved translations from this device.") },
            confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("Clear") } },
            dismissButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { confirmClear = false }) { Text("Keep") } }
        )
    }
    // Home leaves composition while Settings is open, and returning there doesn't resume the activity.
    LaunchedEffect(Unit) { viewModel.refreshSlots() }
    state.translatorConfirm?.let {
        TranslatorConfirmDialog(it.confirm, { viewModel.acceptTranslatorConfirm()?.let(onOpenDestination) }, viewModel::declineTranslatorConfirm)
    }
    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Home", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                state.homeAction?.let { HomeAction(it.label) { onOpenDestination(it.destination) } }
            }
            Spacer(Modifier.height(16.dp))
            val label = when (readingStatus) {
                ReadingStatus.On -> "Reading is ON"
                ReadingStatus.Off -> "Reading is OFF"
                ReadingStatus.NotReady -> "Finish setup to read"
            }
            val icon = when (readingStatus) {
                ReadingStatus.On -> Icons.Default.Check
                ReadingStatus.Off -> Icons.Default.PlayArrow
                ReadingStatus.NotReady -> Icons.Default.Warning
            }
            PaperAction(
                onClick = {
                    if (readingStatus == ReadingStatus.On) viewModel.stopService() else onRequestScreenCapture()
                },
                enabled = readingStatus != ReadingStatus.NotReady,
                accented = readingStatus == ReadingStatus.On,
                modifier = Modifier.fillMaxWidth().semantics { role = Role.Switch; stateDescription = label }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(icon, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.headlineSmall)
                        Text(when (readingStatus) {
                            ReadingStatus.On -> "Tap to turn off"
                            ReadingStatus.Off -> "Tap to turn on"
                            ReadingStatus.NotReady -> "Yomu needs a few things first"
                        }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (readingStatus == ReadingStatus.On) "Keep reading in your manga app. Tap the floating button to translate a page."
                else "Turn reading on, then browse manga in the app you already use.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (readingStatus != ReadingStatus.NotReady) {
                Spacer(Modifier.height(12.dp))
                QuickSettingsStrip(
                    state,
                    viewModel::pickTranslationModel,
                    { group, entry -> viewModel.pickTranslatorEntry(group, entry)?.let(onOpenDestination) },
                    onOpenSettings,
                    viewModel::chooseLanguage
                )
            }
        }
        state.confirmation?.let { message -> item { PaperSuccess(message, viewModel::dismissConfirmation) } }
        if (state.serviceError) item {
            PaperError("Couldn't start reading. Try the reading control again.", viewModel::dismissServiceError)
        }
        if (readingStatus == ReadingStatus.NotReady) item {
            PaperSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Get ready to read", style = MaterialTheme.typography.titleLarge)
                    Text(when (state.readiness) {
                        Readiness.NeedsModels -> "Download the parts that find the speech bubbles, read the Japanese and translate it."
                        Readiness.NeedsPermission -> "Allow Yomu to display translations over your manga app."
                        Readiness.NeedsBoth -> "Download the parts that find the speech bubbles, read the Japanese and translate it, then allow Yomu to display over other apps."
                        Readiness.Ready -> "Finish the short setup, then turn reading on."
                    })
                    PaperButton("Finish setup", viewModel::openSetup)
                }
            }
        }
        // It counts saved pages, so while saving is off it would undercount what was translated today (#454).
        if (saving) item {
            Text("${state.pagesTranslatedToday} ${if (state.pagesTranslatedToday == 1) "page" else "pages"} translated today", style = MaterialTheme.typography.bodySmall)
        }
        // Shown while saving is off even before any page was saved, so Home says why none is kept.
        if (state.historyLoading || state.historyError || state.hasRead || !saving) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (state.translations.isNotEmpty()) TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { confirmClear = true }) { Text("Clear") }
                }
            }
            if (!saving) item {
                Text("Saving is off, so new pages aren't added. You can switch it back on under Settings > Translation.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.clearError) item {
                PaperError("Couldn't clear Recent. Your translations are still saved.", viewModel::clearHistory)
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
                else -> {
                    items(if (showAll) state.translations else state.translations.take(3), key = { it.id }) { translation ->
                        var expanded by rememberSaveable(translation.id) { mutableStateOf(false) }
                        PaperAction(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(translation.translatedText, maxLines = if (expanded) Int.MAX_VALUE else 3, style = MaterialTheme.typography.bodyMedium)
                                val translatedAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(translation.createdAt))
                                val language = TargetLanguage.fromCode(translation.targetLanguage)?.promptName
                                Text(listOfNotNull(translatedAt, language).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                Text(if (expanded) "Show less" else "Read translation", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (state.translations.size > 3) item {
                        PaperButton(if (showAll) "Show recent only" else "Show all translations", { showAll = !showAll })
                    }
                }
            }
        }
    }
}

/** A translator's Home action: quiet accent text beside the title, so the Reading switch stays the strong control (#543). */
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

/** A four-point sparkle with concave sides; Material's core icons have none. */
private val Sparkle: ImageVector = ImageVector.Builder("Sparkle", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        quadTo(12.6f, 11.4f, 22f, 12f)
        quadTo(12.6f, 12.6f, 12f, 22f)
        quadTo(11.4f, 12.6f, 2f, 12f)
        quadTo(11.4f, 11.4f, 12f, 2f)
        close()
    }
}.build()

package com.yomu.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.PickerGroup
import com.yomu.app.translation.TranslatorPickerEntry
import com.yomu.app.ui.theme.LocalYomuColors
import com.yomu.app.ui.theme.PaperButton
import com.yomu.app.ui.theme.PaperError

internal const val KIT_TITLE = "Download the reading kit"
internal const val KIT_CAPTION = "Finds the speech bubbles, reads the Japanese and translates it."

/**
 * Setup's open second step (#547): the three models as one download, with what is inside behind a disclosure. Only the
 * translator can be changed here, since it is the one a reader has a reason to choose; the other two change in Settings.
 */
@Composable
internal fun KitStep(
    state: HomeUiState,
    onDownload: () -> Unit,
    onChangeTranslator: () -> Unit,
    onChooseTranslator: (String?) -> Unit,
    onPickEntry: (PickerGroup, TranslatorPickerEntry) -> Unit
) {
    val kit = state.setupKit()
    // The translator setup hands the pages to, until it takes them (#536).
    val waitingOn = state.setupTranslatorGroup()?.takeIf { state.phoneTranslates && kit.downloaded }
    var inside by rememberSaveable { mutableStateOf(false) }
    ActiveStep(2, KIT_TITLE, KIT_CAPTION) {
        KitProgress(kit)
        when {
            state.setupError -> PaperError("Download failed. Check your connection and storage.", onDownload)
            kit.running -> Unit
            waitingOn != null -> TranslatorEntries(waitingOn, onPickEntry)
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                kit.tooBig?.let {
                    Text(
                        "${it.deliverable.name} is too big for this phone. Change the translator under What's inside.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.onMeteredNetwork && !kit.downloaded) Text("You're on mobile data.")
                PaperButton(if (kit.downloaded) "Continue" else "Download", onDownload, Modifier.fillMaxWidth(), enabled = kit.tooBig == null)
            }
        }
        Divider()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InsideHeader(inside) { inside = !inside }
            if (inside) {
                kit.rows.forEach { row -> KitRow(row, canChange = row.type == ModelType.LLM && !kit.running, onChangeTranslator) }
                if (!kit.running) TranslatorChoice(state, onChooseTranslator)
            }
        }
    }
}

/** One bar and one count for the whole kit. TalkBack hears the download start and end, not each update. */
@Composable
private fun KitProgress(kit: SetupKit) {
    Column(
        Modifier.clearAndSetSemantics {
            contentDescription = kit.announcement()
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = kit.fraction,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = Color(LocalYomuColors.current.accent),
            // The default track is the card's own colour, which hides how much is left.
            trackColor = MaterialTheme.colorScheme.outlineVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kit.progressText(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (kit.running) Text("Keep Yomu open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsideHeader(open: Boolean, onToggle: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).heightIn(min = 48.dp)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "WHAT'S INSIDE", color = muted, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.07.em)
        )
        Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = muted)
    }
}

@Composable
private fun KitRow(row: SetupModelRow, canChange: Boolean, onChange: () -> Unit) {
    val colors = LocalYomuColors.current
    val alarming = row.state == SetupModelState.Failed || row.state == SetupModelState.TooBig
    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) { stateDescription = row.spokenState() },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                row.state == SetupModelState.Done -> Icon(Icons.Default.Check, null, Modifier.size(20.dp), Color(colors.success))
                alarming -> Icon(Icons.Default.Warning, null, Modifier.size(20.dp), MaterialTheme.colorScheme.error)
                else -> Icon(DownloadArrow, null, Modifier.size(20.dp))
            }
            Column {
                Text(row.slot)
                Text(
                    row.detail(), style = MaterialTheme.typography.bodySmall,
                    color = if (alarming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (canChange) {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                onClick = onChange, modifier = Modifier.semantics { contentDescription = "Change translator" }
            ) { Text("Change") }
        }
    }
}

/** Offers each other translator in place of the phone model, or the phone model back once one is chosen (#536). */
@Composable
private fun TranslatorChoice(state: HomeUiState, onChoose: (String?) -> Unit) {
    val textColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    Column {
        state.setupTranslatorOffers().forEach { group ->
            TextButton(colors = textColors, onClick = { onChoose(group.translatorId) }) {
                Text("Translate on ${group.name.replaceFirstChar(Char::lowercase)} instead")
            }
        }
        if (state.setupTranslatorId != null) {
            Text("No translation model is downloaded to this phone.", style = MaterialTheme.typography.bodySmall)
            TextButton(colors = textColors, onClick = { onChoose(null) }) { Text("Translate on this phone instead") }
        }
    }
}

/** The chosen translator's picker entries: one that isn't set up opens its screen, and picking a model finishes this step. */
@Composable
private fun TranslatorEntries(group: PickerGroup, onPick: (PickerGroup, TranslatorPickerEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(group.name, style = MaterialTheme.typography.titleMedium)
        group.entries.forEach { entry ->
            PaperButton(entry.subtitle?.let { "${entry.name} · $it" } ?: entry.name, { onPick(group, entry) }, Modifier.fillMaxWidth())
        }
    }
}

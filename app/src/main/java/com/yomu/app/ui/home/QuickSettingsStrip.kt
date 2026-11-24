package com.yomu.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomu.app.ui.settings.SettingsSection
import com.yomu.app.ui.settings.SlowDeliverableDialog
import com.yomu.app.ui.settings.deliverableStatus
import com.yomu.core.TargetLanguage
import com.yomu.core.toFileSizeString

/**
 * The model chip and the language chip. The model dropdown lists the phone's models and the server's in
 * two groups, and the checked row is the model that translates the next page (ADR-0023). The phone's group
 * holds only the models on the phone; the rest of the catalog is in Settings › Translation.
 */
@Composable
internal fun QuickSettingsStrip(
    state: HomeUiState,
    onPickTranslationModel: (String) -> Unit,
    onPickServerModel: (String) -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onChooseLanguage: (TargetLanguage) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var languagesOpen by rememberSaveable { mutableStateOf(false) }
    var confirmSlow by rememberSaveable { mutableStateOf<String?>(null) }
    val statuses = state.models.associate { it.id to it.status }
    // One row always: the model chip shrinks and ellipsizes so the language chip never wraps (#273).
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f, fill = false)) {
            AssistChip(
                onClick = { open = true },
                label = { Text(state.translationChipLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                GroupHeader("On this phone")
                state.phoneRows().forEach { deliverable ->
                    val selected = state.checked(deliverable)
                    val fits = state.fits(deliverable)
                    val languageBlock = state.languageBlock(deliverable)
                    val status = deliverableStatus(statuses[deliverable.id], null, selected, deliverable.id == state.translationPendingId)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(deliverable.name, style = MaterialTheme.typography.titleSmall)
                                val detail = listOfNotNull(
                                    status,
                                    deliverable.sizeBytes.toFileSizeString(),
                                    deliverable.speed.label,
                                    deliverable.languageLabel,
                                    "Needs more memory".takeUnless { fits },
                                    languageBlock
                                ).joinToString(" · ")
                                Text(detail, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            open = false
                            if (state.needsSlowWarning(deliverable)) confirmSlow = deliverable.id
                            else onPickTranslationModel(deliverable.id)
                        },
                        enabled = fits && languageBlock == null,
                        leadingIcon = { CheckMark(selected) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("More models in Settings", style = MaterialTheme.typography.titleSmall) },
                    onClick = { open = false; onOpenSettings(SettingsSection.Translation) },
                    leadingIcon = { CheckMark(false) }
                )
                Divider()
                GroupHeader("Your server")
                ServerRows(
                    state,
                    onPick = { name -> open = false; onPickServerModel(name) },
                    onSetUp = { open = false; onOpenSettings(SettingsSection.Providers) }
                )
            }
        }
        Box {
            AssistChip(
                onClick = { languagesOpen = true },
                label = { Text(state.languageChipLabel(), maxLines = 1) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = languagesOpen, onDismissRequest = { languagesOpen = false }) {
                TargetLanguage.entries.forEach { language ->
                    val reason = state.languageUnavailable(language)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(language.promptName, style = MaterialTheme.typography.titleSmall)
                                reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        },
                        onClick = {
                            languagesOpen = false
                            onChooseLanguage(language)
                        },
                        enabled = reason == null,
                        leadingIcon = { CheckMark(language == state.targetLanguage) }
                    )
                }
            }
        }
    }
    confirmSlow?.let { id ->
        SlowDeliverableDialog(
            onConfirm = {
                confirmSlow = null
                onPickTranslationModel(id)
            },
            onDismiss = { confirmSlow = null }
        )
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() }
    )
}

@Composable
private fun CheckMark(checked: Boolean) {
    if (checked) Icon(Icons.Default.Check, contentDescription = "Selected") else Spacer(Modifier.size(24.dp))
}

/**
 * The server's group: the models from the last Connect, or the typed name when the server keeps no list.
 * Until the server is set up, its one row opens Providers, and no server model can be picked.
 */
@Composable
private fun ServerRows(state: HomeUiState, onPick: (String) -> Unit, onSetUp: () -> Unit) {
    val models = state.serverModels
    if (models == null) {
        DropdownMenuItem(
            text = {
                Column {
                    Text("Set up your server", style = MaterialTheme.typography.titleSmall)
                    Text("A server you run, or a hosted API you have a key for", style = MaterialTheme.typography.bodySmall)
                }
            },
            onClick = onSetUp,
            leadingIcon = { CheckMark(false) }
        )
        return
    }
    models.forEach { name ->
        DropdownMenuItem(
            text = { Text(name, style = MaterialTheme.typography.titleSmall) },
            onClick = { onPick(name) },
            leadingIcon = { CheckMark(state.serverModelChecked(name)) }
        )
    }
}

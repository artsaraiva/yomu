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
import com.yomu.app.service.PickerGroup
import com.yomu.app.translation.TranslatorPickerEntry
import com.yomu.app.ui.settings.SettingsSection
import com.yomu.app.ui.settings.SlowDeliverableDialog
import com.yomu.app.ui.settings.deliverableStatus
import com.yomu.core.TargetLanguage
import com.yomu.core.toFileSizeString

/**
 * The model chip and the language chip. The model dropdown lists the phone's models, then a group for each
 * registered translator that offers entries (#534), and the checked row is the one that translates the next page
 * (ADR-0023). The phone's group holds only the models on the phone; the rest of the catalog is in Settings › Translation.
 */
@Composable
internal fun QuickSettingsStrip(
    state: HomeUiState,
    onPickTranslationModel: (String) -> Unit,
    onPickTranslatorEntry: (PickerGroup, TranslatorPickerEntry) -> Unit,
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
                label = { Text(state.translatorRowLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                state.translatorGroups.forEach { group ->
                    Divider()
                    GroupHeader(group.name)
                    group.entries.forEach { entry ->
                        TranslatorRow(entry) { open = false; onPickTranslatorEntry(group, entry) }
                    }
                }
            }
        }
        Box {
            AssistChip(
                onClick = { languagesOpen = true },
                label = { Text(state.languageRowLabel(), maxLines = 1) },
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

@Composable
private fun TranslatorRow(entry: TranslatorPickerEntry, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                entry.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        onClick = onClick,
        leadingIcon = { CheckMark(entry.checked) }
    )
}

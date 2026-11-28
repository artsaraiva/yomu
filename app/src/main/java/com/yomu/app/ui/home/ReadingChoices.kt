package com.yomu.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yomu.app.service.PickerGroup
import com.yomu.app.translation.TranslatorPickerEntry
import com.yomu.app.ui.settings.SettingsSection
import com.yomu.app.ui.settings.SlowDeliverableDialog
import com.yomu.app.ui.settings.deliverableStatus
import com.yomu.app.ui.theme.PaperSheet
import com.yomu.app.ui.theme.PaperSurface
import com.yomu.core.TargetLanguage
import com.yomu.core.toFileSizeString

private enum class Choice { Language, Translator }

/**
 * The Language row and the Translator row, each opening a sheet. The translator sheet lists the phone's models, then
 * a group for each registered translator that offers entries (#534), and the checked row is the one that translates
 * the next page (ADR-0023). The phone's group holds only the models on the phone; the rest of the catalog is in
 * Settings › Translator.
 */
@Composable
internal fun ReadingChoices(
    state: HomeUiState,
    onPickTranslationModel: (String) -> Unit,
    onPickTranslatorEntry: (PickerGroup, TranslatorPickerEntry) -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onChooseLanguage: (TargetLanguage) -> Unit
) {
    var open by rememberSaveable { mutableStateOf<Choice?>(null) }
    var confirmSlow by rememberSaveable { mutableStateOf<String?>(null) }
    val close = { open = null }
    PaperSurface(Modifier.fillMaxWidth()) {
        Column {
            ChoiceRow(Globe, "Language", state.languageRowLabel()) { open = Choice.Language }
            Divider(Modifier.padding(horizontal = 16.dp))
            ChoiceRow(PhoneOutline, "Translator", state.translatorRowLabel()) { open = Choice.Translator }
        }
    }
    when (open) {
        Choice.Language -> PaperSheet("Language", close) {
            TargetLanguage.entries.forEach { language ->
                val reason = state.languageUnavailable(language)
                SheetRow(language.promptName, reason, language == state.targetLanguage, enabled = reason == null) {
                    close()
                    onChooseLanguage(language)
                }
            }
        }
        Choice.Translator -> PaperSheet("Translator", close) {
            TranslatorRows(
                state,
                onPickModel = { id, slow ->
                    close()
                    if (slow) confirmSlow = id else onPickTranslationModel(id)
                },
                onPickEntry = { group, entry -> close(); onPickTranslatorEntry(group, entry) },
                onMoreModels = { close(); onOpenSettings(SettingsSection.Translator) }
            )
        }
        null -> Unit
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
private fun TranslatorRows(
    state: HomeUiState,
    onPickModel: (id: String, slow: Boolean) -> Unit,
    onPickEntry: (PickerGroup, TranslatorPickerEntry) -> Unit,
    onMoreModels: () -> Unit
) {
    val statuses = state.models.associate { it.id to it.status }
    GroupHeader("On this phone")
    state.phoneRows().forEach { deliverable ->
        val selected = state.checked(deliverable)
        val fits = state.fits(deliverable)
        val languageBlock = state.languageBlock(deliverable)
        val detail = listOfNotNull(
            deliverableStatus(statuses[deliverable.id], null, selected, deliverable.id == state.translationPendingId),
            deliverable.sizeBytes.toFileSizeString(),
            deliverable.speed.label,
            deliverable.languageLabel,
            "Needs more memory".takeUnless { fits },
            languageBlock
        ).joinToString(" · ")
        SheetRow(deliverable.name, detail, selected, enabled = fits && languageBlock == null) {
            onPickModel(deliverable.id, state.needsSlowWarning(deliverable))
        }
    }
    SheetRow("More models in Settings", null, checked = false, onClick = onMoreModels)
    state.translatorGroups.forEach { group ->
        Divider(Modifier.padding(horizontal = 16.dp))
        GroupHeader(group.name)
        group.entries.forEach { entry -> SheetRow(entry.name, entry.subtitle, entry.checked) { onPickEntry(group, entry) } }
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = muted)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = muted)
        }
    }
}

@Composable
private fun SheetRow(title: String, detail: String?, checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = "Selected") else Spacer(Modifier.size(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) LocalContentColor.current else muted)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
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

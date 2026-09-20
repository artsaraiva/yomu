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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelType
import com.yomu.app.ui.settings.SlowDeliverableDialog
import com.yomu.app.ui.settings.deliverableStatus
import com.yomu.core.toFileSizeString

@Composable
internal fun QuickSettingsStrip(state: HomeUiState, onPickTranslationModel: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
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
                state.deliverables[ModelType.LLM].orEmpty().forEach { deliverable ->
                    val selected = deliverable.id == state.translationSelectedId
                    val fits = state.fits(deliverable)
                    val status = deliverableStatus(statuses[deliverable.id], null, selected, deliverable.id == state.translationPendingId)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(deliverable.name, style = MaterialTheme.typography.titleSmall)
                                val detail = "$status · ${deliverable.sizeBytes.toFileSizeString()} · ${deliverable.speed}"
                                Text(if (fits) detail else "$detail · Needs more memory", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            open = false
                            if (state.needsSlowWarning(deliverable)) confirmSlow = deliverable.id
                            else onPickTranslationModel(deliverable.id)
                        },
                        enabled = fits,
                        leadingIcon = {
                            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected") else Spacer(Modifier.size(24.dp))
                        }
                    )
                }
            }
        }
        AssistChip(onClick = {}, label = { Text("Japanese → English") }, enabled = false)
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

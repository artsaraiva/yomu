package com.yomu.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelType
import com.yomu.app.ui.settings.deliverableStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickSettingsStrip(state: HomeUiState, onPickTranslationModel: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val statuses = state.models.associate { it.id to it.status }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            AssistChip(
                onClick = { open = true },
                label = { Text(state.translationChipLabel()) },
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
                                Text(if (fits) status else "$status · Needs more memory", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            open = false
                            onPickTranslationModel(deliverable.id)
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
}

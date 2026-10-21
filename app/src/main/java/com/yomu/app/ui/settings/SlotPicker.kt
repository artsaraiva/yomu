package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.service.LanguageSwitch
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.ui.theme.*
import com.yomu.core.TargetLanguage
import com.yomu.core.toFileSizeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlotPicker(
    title: String,
    description: String,
    deliverables: List<SlotDeliverable>,
    selectedId: String?,
    pendingId: String?,
    statuses: Map<String, ModelStatus>,
    downloads: Map<String, Int>,
    fits: (SlotDeliverable) -> Boolean,
    needsSlowWarning: (SlotDeliverable) -> Boolean,
    languageBlock: (SlotDeliverable) -> String?,
    onPick: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmSlow by rememberSaveable { mutableStateOf<String?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    PaperSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SettingRow(title, description) {
                Surface(onClick = { open = true }, color = Color.Transparent, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(deliverables.firstOrNull { it.id == selectedId }?.name ?: "No model selected", style = MaterialTheme.typography.titleSmall)
                            deliverables.firstOrNull { it.id == pendingId }?.let {
                                Text("Switches to ${it.name} when its download finishes", style = MaterialTheme.typography.bodySmall, color = muted)
                            }
                        }
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            deliverables.forEach { deliverable ->
                DeliverableRow(
                    deliverable = deliverable,
                    selected = deliverable.id == selectedId,
                    pending = deliverable.id == pendingId,
                    status = statuses[deliverable.id],
                    progress = downloads[deliverable.id],
                    fits = fits(deliverable),
                    languageBlock = languageBlock(deliverable),
                    onPick = { if (needsSlowWarning(deliverable)) confirmSlow = deliverable.id else onPick(deliverable.id) },
                    onCancel = { onCancel(deliverable.id) },
                    onDelete = { if (deliverable.id == selectedId) confirmDelete = deliverable.id else onDelete(deliverable.id) }
                )
            }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete the model in use?") },
            text = { Text("Yomu can't translate or read until you pick and download a model.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDelete(id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } }
        )
    }
    confirmSlow?.let { id ->
        SlowDeliverableDialog(
            onConfirm = {
                confirmSlow = null
                onPick(id)
            },
            onDismiss = { confirmSlow = null }
        )
    }
}

/** Every surface that picks a model asks the same thing, so a Slow pick reads the same wherever it starts. */
@Composable
internal fun SlowDeliverableDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This model is slow") },
        text = { Text("It is a large model and takes longer to translate each page. Pick it anyway?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Pick it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Every surface that chooses a language asks the same thing when the model in use writes English only (#338). */
@Composable
internal fun LanguageOfferDialog(offer: LanguageSwitch.Offer, keep: TargetLanguage, onAccept: () -> Unit, onDecline: () -> Unit) {
    val download = if (offer.downloadBytes > 0) " It downloads ${offer.downloadBytes.toFileSizeString()} first." else ""
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Switch to ${offer.model.name}?") },
        text = { Text("${offer.current.name} writes English only. ${offer.model.name} can translate into ${offer.language.promptName}.$download") },
        confirmButton = { TextButton(onClick = onAccept) { Text("Switch") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Keep ${keep.promptName}") } }
    )
}

internal fun deliverableStatus(status: ModelStatus?, progress: Int?, selected: Boolean, pending: Boolean): String = when {
    progress != null -> "Downloading $progress%"
    status == ModelStatus.READY -> if (selected) "In use" else "Downloaded"
    status == ModelStatus.ERROR -> "Couldn't download"
    else -> "Not downloaded"
} + if (pending) " · Takes over when downloaded" else ""

internal fun deliverableDetail(deliverable: SlotDeliverable, fits: Boolean, languageBlock: String? = null): String = listOfNotNull(
    deliverable.sizeBytes.toFileSizeString(),
    deliverable.speed.label,
    deliverable.languageLabel,
    deliverable.licence,
    "Experimental".takeIf { deliverable.experimental },
    if (fits) "Fits this device" else "Needs more memory",
    languageBlock
).joinToString(" · ")

@Composable
private fun DeliverableRow(
    deliverable: SlotDeliverable,
    selected: Boolean,
    pending: Boolean,
    status: ModelStatus?,
    progress: Int?,
    fits: Boolean,
    languageBlock: String?,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val pickable = fits && languageBlock == null
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val statusLabel = deliverableStatus(status, progress, selected, pending)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onPick, enabled = pickable)
            Column(Modifier.weight(1f)) {
                Text(deliverable.name, style = MaterialTheme.typography.titleSmall, color = if (pickable) LocalContentColor.current else muted)
                Text(deliverableDetail(deliverable, fits, languageBlock), style = MaterialTheme.typography.bodySmall, color = muted)
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == ModelStatus.ERROR && progress == null) MaterialTheme.colorScheme.error else muted
                )
            }
            when {
                progress != null -> PaperButton("Cancel", onCancel)
                status == ModelStatus.READY -> PaperButton("Delete", onDelete)
                else -> PaperButton(if (status == ModelStatus.ERROR) "Retry" else "Download", onPick, enabled = pickable)
            }
        }
        progress?.let {
            LinearProgressIndicator(progress = it / 100f, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

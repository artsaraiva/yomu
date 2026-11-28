package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.ui.theme.PaperButton
import com.yomu.app.ui.theme.PaperSheet
import com.yomu.app.ui.theme.PaperSurface

/**
 * The phone's side of the Translator page (#548): the downloaded models as cards, the rest to download, and the ones
 * this phone can't hold folded into one row. A model's licence, experimental status and Delete are in its detail.
 */
@Composable
internal fun PhoneModelList(state: SettingsUiState, onPick: (String) -> Unit, onCancel: (String) -> Unit, onDelete: (String) -> Unit) {
    val deliverables = state.deliverables[ModelType.LLM].orEmpty()
    val statuses = state.models.associate { it.id to it.status }
    val selectedId = state.selectedIds[ModelType.LLM]
    val pendingId = state.pendingIds[ModelType.LLM]
    val groups = phoneModels(deliverables, statuses, state.downloads.keys + setOfNotNull(pendingId), state::fits)
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showTooBig by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmSlow by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val pick: (SlotDeliverable) -> Unit = { if (state.needsSlowWarning(it)) confirmSlow = it.id else onPick(it.id) }
    val pickable: (SlotDeliverable) -> Boolean = { state.fits(it) && state.languageBlock(it) == null }

    if (groups.downloaded.isNotEmpty()) {
        GroupLabel("Downloaded")
        groups.downloaded.forEach { model ->
            val inUse = state.phoneTranslates && model.id == selectedId
            DownloadedCard(model, inUse, pickable(model), downloadedLine(model, inUse, state.languageBlock(model)), { pick(model) }) { detailId = model.id }
        }
    }
    val listed = groups.toDownload + if (showMore) groups.more else emptyList()
    if (listed.isNotEmpty() || groups.more.isNotEmpty()) {
        GroupLabel(if (groups.downloaded.isEmpty()) "To download" else "More to download")
        PaperSurface(Modifier.fillMaxWidth()) {
            Column {
                listed.forEachIndexed { index, model ->
                    if (index > 0) Divider(Modifier.padding(horizontal = 16.dp))
                    val status = statuses[model.id]
                    val progress = state.downloads[model.id]
                    DownloadRow(
                        model, status, progress, pickable(model),
                        downloadNote(status, progress, model.id == pendingId, state.languageBlock(model)),
                        onPick = { pick(model) }, onCancel = { onCancel(model.id) }, onMore = { detailId = model.id }
                    )
                }
                if (groups.more.isNotEmpty()) {
                    if (listed.isNotEmpty()) Divider(Modifier.padding(horizontal = 16.dp))
                    FoldRow(if (showMore) "Show fewer" else moreLabel(groups.more.size), showMore) { showMore = !showMore }
                }
            }
        }
    }
    if (groups.tooBig.isNotEmpty()) {
        TooBigCard(groups.tooBig, showTooBig, onToggle = { showTooBig = !showTooBig }) { detailId = it.id }
    }

    deliverables.firstOrNull { it.id == detailId }?.let { model ->
        ModelDetailSheet(
            model, state.fits(model), downloaded = statuses[model.id] == ModelStatus.READY,
            onDelete = {
                detailId = null
                if (model.id == selectedId) confirmDelete = model.id else onDelete(model.id)
            },
            onDismiss = { detailId = null }
        )
    }
    confirmDelete?.let { id ->
        DeleteSelectedDialog(
            phoneModelIdle = !state.phoneTranslates,
            onDelete = {
                confirmDelete = null
                onDelete(id)
            },
            onKeep = { confirmDelete = null }
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

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() }
    )
}

/** What left the model's card: its size, licence, experimental status and fit, and Delete once it is downloaded. */
@Composable
private fun ModelDetailSheet(model: SlotDeliverable, fits: Boolean, downloaded: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    PaperSheet(model.name, onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModelTags(model)
            modelFacts(model, fits).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (downloaded) PaperButton("Delete", onDelete)
        }
    }
}

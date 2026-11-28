package com.yomu.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.ui.home.DownloadArrow
import com.yomu.app.ui.theme.LocalYomuColors
import com.yomu.app.ui.theme.PaperAction
import com.yomu.app.ui.theme.PaperButton
import com.yomu.app.ui.theme.PaperSurface
import com.yomu.core.toFileSizeString

/** A downloaded model: tapping the card picks it, and the picked one is outlined. Its detail opens from the dots. */
@Composable
internal fun DownloadedCard(model: SlotDeliverable, inUse: Boolean, pickable: Boolean, line: String, onPick: () -> Unit, onMore: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    PaperSurface(Modifier.fillMaxWidth(), selected = inUse) {
        Row(
            Modifier.selectable(selected = inUse, enabled = pickable, role = Role.RadioButton, onClick = onPick)
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(selected = inUse, onClick = null, enabled = pickable)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(
                        model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (pickable) LocalContentColor.current else muted
                    )
                    Text(line, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                ModelTags(model)
            }
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, contentDescription = "More about ${model.name}") }
        }
    }
}

/** A model that isn't on the phone: its button downloads it, and while it downloads the row shows how far and Cancel. */
@Composable
internal fun DownloadRow(
    model: SlotDeliverable,
    status: ModelStatus?,
    progress: Int?,
    pickable: Boolean,
    note: String?,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onMore: () -> Unit
) {
    val failed = status == ModelStatus.ERROR && progress == null
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModelSummary(model, pickable, note, failed, Modifier.weight(1f), onMore)
            when {
                progress != null -> PaperButton("Cancel", onCancel)
                failed -> PaperButton("Retry", onPick, enabled = pickable)
                else -> DownloadButton(model, onPick, pickable)
            }
        }
        progress?.let {
            LinearProgressIndicator(progress = it / 100f, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        }
    }
}

/** A model's name, tags and note; tapping them opens its detail. */
@Composable
private fun ModelSummary(model: SlotDeliverable, pickable: Boolean, note: String?, failed: Boolean, modifier: Modifier, onMore: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClickLabel = "More about ${model.name}", onClick = onMore),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(model.name, style = MaterialTheme.typography.titleMedium, color = if (pickable) LocalContentColor.current else muted)
        ModelTags(model)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else muted) }
    }
}

@Composable
private fun DownloadButton(model: SlotDeliverable, onClick: () -> Unit, enabled: Boolean) {
    val size = model.sizeBytes.toFileSizeString()
    PaperAction(onClick, Modifier.semantics { contentDescription = "Download ${model.name}, $size" }, enabled) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(DownloadArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(size, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The models this phone can't hold, behind one dashed row so they stay in reach without crowding the list. */
@Composable
internal fun TooBigCard(models: List<SlotDeliverable>, open: Boolean, onToggle: () -> Unit, onMore: (SlotDeliverable) -> Unit) {
    val edge = Color(LocalYomuColors.current.inkMuted)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.fillMaxWidth().clip(shape).drawBehind {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            drawOutline(shape.createOutline(size, layoutDirection, this), edge, style = Stroke(1.dp.toPx(), pathEffect = dash))
        }
    ) {
        FoldRow(tooBigLabel(models.size), open, onToggle)
        if (open) models.forEach { model ->
            Divider(Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModelSummary(model, pickable = false, note = null, failed = false, Modifier.weight(1f)) { onMore(model) }
                Text(model.sizeBytes.toFileSizeString(), style = MaterialTheme.typography.labelLarge, color = muted)
            }
        }
    }
}

/** A row that folds the rows after it away; [label] says what is behind it. */
@Composable
internal fun FoldRow(label: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Open", onClick = onToggle)
            .semantics { stateDescription = if (open) "Open" else "Closed" }
            .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(
            if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ModelTags(model: SlotDeliverable) {
    val colors = LocalYomuColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modelTags(model).forEach { tag ->
            Surface(shape = MaterialTheme.shapes.small, color = Color(colors.paper), contentColor = Color(colors.ink), border = BorderStroke(1.dp, Color(colors.edge))) {
                Text(tag, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

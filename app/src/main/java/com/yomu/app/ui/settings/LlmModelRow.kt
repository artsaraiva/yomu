package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.translation.LlmModelOption
import com.yomu.app.ui.theme.*
import com.yomu.core.toFileSizeString

@Composable
internal fun LlmModelRow(
    option: LlmModelOption,
    selected: Boolean,
    canRun: Boolean,
    model: ModelEntity?,
    isDownloading: Boolean,
    progress: Int,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val selectable = canRun && model?.status == ModelStatus.READY
    val subtitle = buildString {
        append(option.sizeBytes.toFileSizeString())
        append(" · ")
        append(option.licence)
        append(" · Hosted")
        if (!canRun) append(" · Won't fit this device")
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = selectable
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.displayName,
                    fontSize = 13.sp,
                    color = if (selectable) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canRun && model != null) {
                when {
                    isDownloading -> Text("$progress%", fontSize = 11.sp)
                    model.status == ModelStatus.READY -> PaperAction(onClick = onDelete) {
                        Text("Delete", fontSize = 12.sp)
                    }
                    else -> PaperAction(onClick = onDownload) {
                        Text(
                            if (model.status == ModelStatus.ERROR) "Retry" else "Download",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

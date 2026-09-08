package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.ui.theme.*
import androidx.compose.material.icons.filled.DateRange
import com.yomu.core.Constants
import com.yomu.core.toFileSizeString

@Composable
internal fun ModelStatusRow(
    model: ModelEntity,
    isDownloading: Boolean,
    progress: Int,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Text(model.name, style = MaterialTheme.typography.titleSmall)
    Text(model.fileSize.toFileSizeString(), style = MaterialTheme.typography.bodySmall)
    if (model.status == ModelStatus.ERROR && !isDownloading) {
        PaperError("Couldn't download this model.", onDownload)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            when (model.status) {
                ModelStatus.READY -> Text(
                    text = if (model.id == Constants.ML_KIT_JA_EN_MODEL_ID) "Downloaded (baseline)" else "Ready",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ModelStatus.DOWNLOADING -> Text(
                    text = "Downloading $progress%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ModelStatus.AVAILABLE -> Text(
                    text = "Not downloaded",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ModelStatus.ERROR -> Text(
                    text = "Error — tap to retry",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
                else -> Text(
                    text = model.status.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            isDownloading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(androidx.compose.material.icons.Icons.Default.DateRange, contentDescription = "Downloading")
                    Text("$progress%", fontSize = 11.sp)
                }
            }
            model.status == ModelStatus.READY -> {
                PaperAction(onClick = onDelete) {
                    Text("Delete", fontSize = 12.sp)
                }
            }
            else -> {
                PaperAction(onClick = onDownload) {
                    Text(
                        if (model.status == ModelStatus.ERROR) "Retry" else "Download",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    if (isDownloading) {
        LinearProgressIndicator(
            progress = progress.coerceIn(0, 100) / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

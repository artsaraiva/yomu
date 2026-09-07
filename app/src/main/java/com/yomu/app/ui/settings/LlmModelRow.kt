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
import com.yomu.app.translation.LlmModelTier
import com.yomu.app.ui.theme.*
import com.yomu.core.toFileSizeString

@Composable
internal fun LlmModelRow(
    option: LlmModelOption,
    selected: Boolean,
    canRun: Boolean,
    hfSignedIn: Boolean,
    model: ModelEntity?,
    isDownloading: Boolean,
    progress: Int,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val hosted = option.tier == LlmModelTier.HOSTED
    val downloaded = model?.status == ModelStatus.READY
    // HF-auth entries need the user signed in before they can be fetched or run; hosted entries are
    // always actionable. Either way the model must fit the device and (to select) be downloaded.
    val actionable = hosted || hfSignedIn
    val selectable = actionable && canRun && downloaded
    val subtitle = buildString {
        append(option.sizeBytes.toFileSizeString())
        append(" · ")
        append(option.licence)
        append(if (hosted) " · Hosted" else if (hfSignedIn) " · HuggingFace" else " · HuggingFace sign-in required")
        if (actionable && !canRun) append(" · Won't fit this device")
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
            if (actionable && canRun && model != null) {
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

@Composable
internal fun HfSignInRow(signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (signedIn) "Signed in to HuggingFace" else "Sign in to HuggingFace for gated models",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (signedIn) {
            PaperAction(onClick = onSignOut) { Text("Sign out", fontSize = 12.sp) }
        } else {
            PaperAction(onClick = onSignIn) { Text("Sign in", fontSize = 12.sp) }
        }
    }
}

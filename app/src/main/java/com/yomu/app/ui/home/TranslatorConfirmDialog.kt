package com.yomu.app.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.yomu.app.translation.TranslatorPick

/**
 * Asked when a translator's pick needs the reader to agree first, such as the first server pick before any page's
 * text leaves the phone (ADR-0023). Declining changes nothing.
 */
@Composable
internal fun TranslatorConfirmDialog(confirm: TranslatorPick.Confirm, onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(confirm.title) },
        text = { Text(confirm.message) },
        confirmButton = { TextButton(onClick = onAccept) { Text(confirm.confirmLabel) } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Cancel") } }
    )
}

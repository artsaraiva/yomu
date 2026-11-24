package com.yomu.app.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Asked on the first server pick, before any page's text leaves the phone (ADR-0023). Declining keeps
 * the phone model in use.
 */
@Composable
internal fun ServerNoticeDialog(address: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Translate on your server?") },
        text = {
            Text(
                "The text of each page you translate will be sent to $address. The page itself stays on your phone, " +
                    "and you can pick a phone model here again at any time."
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Send to my server") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Cancel") } }
    )
}

package com.yomu.app.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val REFRESH_MS = 1_000L

/** What the app is using right now (#79). It lives inside Advanced's Live usage section, so it samples only while that is open. */
@Composable
internal fun LiveUsage(readout: ResourceReadout?, onRefresh: () -> Unit) {
    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            delay(REFRESH_MS)
        }
    }
    val context = LocalContext.current
    fun bytes(value: Long) = Formatter.formatShortFileSize(context, value)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // No early return here: leaving a composable lambda mid-group unbalances the Compose group stack on recompose.
        if (readout == null) {
            Text("Measuring…", style = MaterialTheme.typography.bodySmall)
        } else {
            ReadoutRow("App memory", "${bytes(readout.appPssBytes)} (PSS)")
            ReadoutRow("Native heap", bytes(readout.nativeHeapBytes))
            ReadoutRow("Phone memory free", "${bytes(readout.deviceAvailableBytes)} of ${bytes(readout.deviceTotalBytes)}")
            ReadoutRow("CPU", readout.cpuPercent?.let { "$it% of all cores" } ?: "Measuring…")
            ReadoutRow("Page time, last page", readout.lastPageTotalMs?.let { "$it ms" } ?: "No page translated yet")
            ReadoutRow("Model time, last page", readout.lastPageMs?.let { "$it ms" } ?: "No page translated yet")
        }
    }
}

@Composable
private fun ReadoutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
    }
}

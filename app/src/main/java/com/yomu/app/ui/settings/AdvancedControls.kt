package com.yomu.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A section that opens in place (#549). The header toggles it; Reset shows only while open, so a closed list reads as
 * titles and summaries alone. A null [onReset] is a section with nothing to reset.
 */
@Composable
internal fun CollapsibleSection(
    title: String,
    subtitle: String,
    open: Boolean,
    onToggle: () -> Unit,
    onReset: (() -> Unit)? = null,
    resetEnabled: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Open", onClick = onToggle)
                .semantics { stateDescription = if (open) "Open" else "Closed" }
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = if (open) FontWeight.Bold else null)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            if (open && onReset != null) {
                TextButton(
                    onClick = onReset,
                    enabled = resetEnabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                    modifier = Modifier.semantics { contentDescription = "Reset $title" }
                ) { Text("Reset") }
            }
            Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = muted)
        }
        if (open) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

/** A slider with its label and value on one line above it and what moving it costs below. */
@Composable
internal fun SliderRow(label: String, text: SliderText, consequence: String, slider: @Composable () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(text.value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
        }
        slider()
        Text(consequence, style = MaterialTheme.typography.bodySmall, color = muted)
        text.default?.let { Text("Default $it", style = MaterialTheme.typography.bodySmall, color = muted) }
    }
}

/** A stepped slider over [positions] + 1 places from 0; callers keep the position local and save in [onRelease]. */
@Composable
internal fun SteppedSlider(position: Int, positions: Int, onMove: (Int) -> Unit, onRelease: () -> Unit) {
    Slider(
        value = position.toFloat(),
        onValueChange = { onMove(it.roundToInt()) },
        onValueChangeFinished = onRelease,
        valueRange = 0f..positions.toFloat(),
        steps = positions - 1,
        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    )
}

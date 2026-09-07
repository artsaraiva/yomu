package com.yomu.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PaperAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accented: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalYomuColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = MaterialTheme.shapes.medium,
        color = Color(if (accented && enabled) colors.accent else colors.paperRaised),
        contentColor = Color(if (!enabled) colors.inkMuted else if (accented) colors.onAccent else colors.ink),
        border = BorderStroke(1.dp, Color(if (enabled) colors.inkMuted else colors.edge)),
        shadowElevation = if (!enabled || pressed) 0.dp else 3.dp
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
fun PaperButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PaperAction(onClick, modifier, enabled) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Switch(
        checked = checked, onCheckedChange = onCheckedChange,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            uncheckedBorderColor = scheme.onSurfaceVariant,
            uncheckedThumbColor = scheme.onSurfaceVariant,
            uncheckedTrackColor = scheme.surfaceVariant,
            checkedTrackColor = scheme.primary,
            checkedThumbColor = scheme.onPrimary,
            checkedBorderColor = scheme.onSurfaceVariant
        )
    )
}

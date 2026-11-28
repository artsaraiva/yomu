package com.yomu.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yomu.app.ui.theme.LocalYomuColors
import com.yomu.app.ui.theme.PaperSurface

// The three ways the setup stepper draws a step (#547): open in place, collapsed to one line once done, flat until its turn.

/** The open step: its number, what it asks for, and its controls in [content]. */
@Composable
internal fun ActiveStep(number: Int, title: String, caption: String, content: @Composable ColumnScope.() -> Unit) {
    PaperSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepBadge(SetupPhase.Active, number)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** A finished step on one line: what it settled and, in [action], a way to change it. */
@Composable
internal fun DoneStep(label: String, value: String, action: @Composable () -> Unit = {}) {
    PaperSurface(Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 60.dp).padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.weight(1f).semantics(mergeDescendants = true) { stateDescription = "Done" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StepBadge(SetupPhase.Done)
                Column {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleMedium)
                }
            }
            action()
        }
    }
}

/** A step whose turn hasn't come: no card behind it, so the open step is the only raised one. */
@Composable
internal fun LaterStep(number: Int, title: String, caption: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = Color.Transparent,
        border = BorderStroke(1.dp, Color(LocalYomuColors.current.edge))
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) { stateDescription = "Not started" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StepBadge(SetupPhase.Later, number)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = muted)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

/** The step's [number] in a circle: filled with ink while open, outlined while waiting. A done step shows a tick on green instead. */
@Composable
private fun StepBadge(phase: SetupPhase, number: Int? = null) {
    val colors = LocalYomuColors.current
    val size = 28.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val shape = when (phase) {
        SetupPhase.Done -> Modifier.background(Color(colors.success), CircleShape)
        SetupPhase.Active -> Modifier.background(Color(colors.ink), CircleShape)
        SetupPhase.Later -> Modifier.border(2.dp, Color(colors.inkMuted), CircleShape)
    }
    // The step's own state description says where it stands; the number adds nothing spoken.
    Box(Modifier.size(size).then(shape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (phase == SetupPhase.Done) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color(colors.paperRaised), modifier = Modifier.size(18.dp))
        } else {
            Text(
                "${number ?: ""}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color(if (phase == SetupPhase.Active) colors.paperRaised else colors.inkMuted)
            )
        }
    }
}

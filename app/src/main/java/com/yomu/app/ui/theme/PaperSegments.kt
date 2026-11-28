package com.yomu.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A row of [options] of which exactly one, [selected], is on; the on segment is inked in. */
@Composable
fun <T> PaperSegments(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val colors = LocalYomuColors.current
    Surface(
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        color = Color(colors.paperRaised), border = BorderStroke(1.dp, Color(colors.inkMuted))
    ) {
        Row(Modifier.selectableGroup().height(IntrinsicSize.Min)) {
            options.forEachIndexed { index, option ->
                val on = option == selected
                if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Color(colors.edge)))
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).fillMaxHeight()
                        .background(if (on) Color(colors.ink) else Color.Transparent)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(option) })
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label(option), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                        color = Color(if (on) colors.paperRaised else colors.ink)
                    )
                }
            }
        }
    }
}

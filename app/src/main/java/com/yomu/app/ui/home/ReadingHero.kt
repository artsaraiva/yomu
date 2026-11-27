package com.yomu.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.yomu.app.ui.theme.LocalYomuColors
import com.yomu.app.ui.theme.PaperAction
import com.yomu.app.ui.theme.PaperButton
import com.yomu.app.ui.theme.PaperSurface

private val STEPS = listOf("Start, and share your screen", "Open your manga app", "Tap 読 on a page")

/**
 * Home's reading control. While reading is off it is the one accent action on Home, over the three steps to a first
 * page; while on it teaches the overlay's gestures, which the overlay itself can't explain, and stopping is a quiet
 * button; before setup is done it leads to setup. Start and stop are one switch to TalkBack.
 */
@Composable
internal fun ReadingHero(state: HomeUiState, onStart: () -> Unit, onStop: () -> Unit, onFinishSetup: () -> Unit) {
    PaperSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state.readingStatus) {
                ReadingStatus.Off -> ReadingOff(state.targetLanguage.promptName, onStart)
                ReadingStatus.On -> ReadingOn(onStop)
                ReadingStatus.NotReady -> NotReady(state.readiness, onFinishSetup)
            }
        }
    }
}

@Composable
private fun ReadingOff(language: String, onStart: () -> Unit) {
    val state = "Reading is off"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Overline(state)
        Text("Read any manga app in $language", style = MaterialTheme.typography.headlineMedium)
    }
    PaperAction(
        onClick = onStart, accented = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { role = Role.Switch; stateDescription = state }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Start reading", style = MaterialTheme.typography.titleLarge)
        }
    }
    // Three columns of caption text don't fit once the reader's font is large, so the steps stack.
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            STEPS.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepNumber(index + 1)
                    Caption(step)
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STEPS.forEachIndexed { index, step ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StepNumber(index + 1)
                    Caption(step, TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ReadingOn(onStop: () -> Unit) {
    val colors = LocalYomuColors.current
    val state = "Reading is on"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color(colors.success), modifier = Modifier.size(20.dp))
            Overline(state, Color(colors.success))
        }
        Text("Open your manga app", style = MaterialTheme.typography.headlineMedium)
        Text("The 読 button is waiting on top of it.", style = MaterialTheme.typography.bodyMedium)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Gesture("Tap", " to translate the page") { YomuMark(32.dp) }
        Gesture("Hold", " for language, text size and more") {
            Box(Modifier.border(2.dp, Color(colors.inkMuted), CircleShape).padding(4.dp)) { YomuMark(32.dp) }
        }
        Gesture("Tap a bubble", " to see the Japanese") {
            val bubble = RoundedCornerShape(50)
            Box(Modifier.size(32.dp, 26.dp).background(Color.White, bubble).border(2.dp, Color(colors.ink), bubble))
        }
    }
    PaperAction(onClick = onStop, modifier = Modifier.fillMaxWidth().semantics { role = Role.Switch; stateDescription = state }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(StopSquare, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Stop reading", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NotReady(readiness: Readiness, onFinishSetup: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Overline("Yomu needs a few things first")
        Text("Finish setup to read", style = MaterialTheme.typography.headlineMedium)
    }
    Text(when (readiness) {
        Readiness.NeedsModels -> "Download the parts that find the speech bubbles, read the Japanese and translate it."
        Readiness.NeedsPermission -> "Allow Yomu to display translations over your manga app."
        Readiness.NeedsBoth -> "Download the parts that find the speech bubbles, read the Japanese and translate it, then allow Yomu to display over other apps."
        Readiness.Ready -> "Finish the short setup, then turn reading on."
    })
    PaperButton("Finish setup", onFinishSetup, Modifier.fillMaxWidth())
}

/** One overlay gesture: its picture in a column as wide as the widest one, then what it does. */
@Composable
private fun Gesture(action: String, effect: String, picture: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(44.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) { picture() }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(action) }
                append(effect)
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun Overline(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(), color = color,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.07.em)
    )
}

@Composable
private fun Caption(text: String, align: TextAlign = TextAlign.Start) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
}

@Composable
private fun StepNumber(number: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val size = 28.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(Modifier.size(size).border(2.dp, muted, CircleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Text("$number", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = muted)
    }
}

/** Yomu's mark, the 読 of the floating button; it keeps its size whatever the reader's font scale. */
@Composable
internal fun YomuMark(size: Dp) {
    val colors = LocalYomuColors.current
    Box(Modifier.size(size).background(Color(colors.accent), CircleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) { (size * 0.55f).toSp() }
        Text("読", color = Color(colors.onAccent), fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

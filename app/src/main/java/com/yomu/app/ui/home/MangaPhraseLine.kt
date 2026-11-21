package com.yomu.app.ui.home

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private const val PHRASE_MILLIS = 2_500L

/** Shown one at a time while setup downloads (#464). Decoration only: the model rows carry the real progress. */
val MANGA_PHRASES = listOf(
    "Inking…",
    "Toning…",
    "Lettering…",
    "Cutting screentone…",
    "Sharpening G-pens…",
    "Ruling panel borders…",
    "Drawing speed lines…",
    "Erasing pencil lines…",
    "Filling the bubbles…",
    "Hiding from the editor…",
    "Flipping pages…",
    "Reading right to left…",
    "Checking the furigana…",
    "Rereading the last chapter…",
    "Bookmarking…",
    "Doki doki…",
    "Gogogo…",
    "Zawa zawa…",
    "Pera pera…",
    "Shiiin…"
)

/** Deals phrases in shuffled rounds: each shows once per round, and a round never opens with the phrase just shown. */
class PhraseDeck(private val phrases: List<String>, private val random: Random = Random.Default) {
    private val round = ArrayDeque<String>()
    private var last: String? = null

    fun next(): String {
        if (round.isEmpty()) {
            val shuffled = phrases.shuffled(random)
            round.addAll(if (shuffled.size > 1 && shuffled.first() == last) shuffled.drop(1) + shuffled.first() else shuffled)
        }
        return round.removeFirst().also { last = it }
    }
}

/** A changing manga phrase with a pulsing screentone dot. Hidden from TalkBack, and still when the system removes animations. */
@Composable
internal fun MangaPhraseLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val deck = remember { PhraseDeck(MANGA_PHRASES) }
    var phrase by remember { mutableStateOf(deck.next()) }
    LaunchedEffect(deck) {
        while (true) {
            delay(PHRASE_MILLIS)
            phrase = deck.next()
        }
    }
    Row(modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreentoneDot(animate)
        val style = MaterialTheme.typography.titleMedium
        if (animate) Crossfade(phrase, label = "manga phrase") { Text(it, style = style) } else Text(phrase, style = style)
    }
}

/** A 3×3 halftone cluster, largest at the centre, whose dots swell and shrink together while [animate] is on. */
@Composable
private fun ScreentoneDot(animate: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface
    val pulse = if (animate) {
        rememberInfiniteTransition(label = "screentone")
            .animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse").value
    } else 1f
    Canvas(Modifier.size(18.dp)) {
        val cell = size.minDimension / 3
        for (row in 0..2) for (col in 0..2) {
            val fromCentre = abs(row - 1) + abs(col - 1)
            drawCircle(color, cell / 2 * (1f - fromCentre * 0.25f) * pulse, Offset(cell * (col + 0.5f), cell * (row + 0.5f)))
        }
    }
}

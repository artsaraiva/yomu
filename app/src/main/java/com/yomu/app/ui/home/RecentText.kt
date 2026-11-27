package com.yomu.app.ui.home

import com.yomu.core.TargetLanguage
import java.io.File

/** One bubble of a saved page: the Japanese Yomu read and what it became. */
data class RecentBubble(val original: String, val translated: String)

private val BUBBLE_START = Regex("""^\[[^\]]+] """)
private const val ARROW = " → "

/**
 * The bubbles of a saved page, which is stored as one `[id] Japanese → translation` line per bubble. A line that
 * opens no bubble continues the one before it, so a translation with line breaks stays whole.
 */
fun recentBubbles(saved: String): List<RecentBubble> {
    val bubbles = mutableListOf<RecentBubble>()
    saved.lines().forEach { line ->
        val start = BUBBLE_START.find(line)
        val last = bubbles.lastOrNull()
        when {
            start != null && ARROW in line -> {
                val body = line.substring(start.range.last + 1)
                bubbles += RecentBubble(body.substringBefore(ARROW), body.substringAfter(ARROW))
            }
            last != null -> bubbles[bubbles.lastIndex] = last.copy(translated = "${last.translated}\n$line")
            else -> bubbles += RecentBubble("", line)
        }
    }
    return bubbles
}

/** The translations of a saved page as one run of text, for a row that shows its first lines. */
fun recentPreview(saved: String): String = recentBubbles(saved).joinToString(" ") { it.translated.replace('\n', ' ') }

/** A Recent row's small print: [time], the bubble count and the language [languageCode] names, when Yomu knows it. */
fun recentDetail(time: String, bubbleCount: Int, languageCode: String): String = listOfNotNull(
    time,
    "$bubbleCount ${if (bubbleCount == 1) "bubble" else "bubbles"}",
    TargetLanguage.fromCode(languageCode)?.promptName
).joinToString(" · ")

fun pagesTodayLabel(pages: Int): String = "$pages ${if (pages == 1) "page" else "pages"} today"

/** The page image saved at [path], or null when the page has none or its file is gone. */
fun pageImage(path: String): File? = File(path).takeIf { path.isNotBlank() && it.isFile }

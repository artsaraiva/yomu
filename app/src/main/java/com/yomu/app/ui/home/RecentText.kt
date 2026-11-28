package com.yomu.app.ui.home

import com.yomu.core.TargetLanguage
import java.io.File

/** One bubble of a saved page: the Japanese Yomu read and what it became. */
data class RecentBubble(val original: String, val translated: String)

private val BUBBLE_START = Regex("""^\[[^\]]+] """)
private const val ARROW = " → "

/**
 * The bubbles of a saved page, which is stored as one `[id] Japanese → translation` entry per bubble. A line that
 * opens no bubble continues the one before it, so Japanese or a translation with line breaks stays whole.
 */
fun recentBubbles(saved: String): List<RecentBubble> {
    val entries = mutableListOf<String>()
    saved.lines().forEach { line ->
        if (entries.isEmpty() || BUBBLE_START.containsMatchIn(line)) entries += line else entries[entries.lastIndex] += "\n$line"
    }
    return entries.map { entry ->
        val body = entry.replaceFirst(BUBBLE_START, "")
        if (ARROW in body) RecentBubble(body.substringBefore(ARROW), body.substringAfter(ARROW)) else RecentBubble("", entry)
    }
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

package com.yomu.ml

import com.yomu.core.TranslatablePage

/** How long a whole id-keyed page may take, on the phone or on the reader's server (ADR-0023). */
internal const val PAGE_TIMEOUT_MS = 120_000

private val ID_KEYED_LINE = "^\\s*\\[(\\d+)]\\s*(.*)$".toRegex()

/**
 * The id-keyed page prompt: one instruction line, then one `[id] source` line per bubble, with panels
 * separated by `---`. Both translators send it, so the reply is read the same way wherever it was written.
 */
internal fun idKeyedPagePrompt(page: TranslatablePage): String = buildString {
    appendLine(
        "Translate each Japanese line to ${page.targetLanguage.promptName}. Keep the [id] tag before each line. " +
            "Panels are separated by ---."
    )
    page.panels.forEachIndexed { index, panel ->
        if (index > 0) appendLine("---")
        panel.forEach { bubble -> appendLine("[${bubble.bubbleId}] ${bubble.sourceText}") }
    }
}

/**
 * Reads an id-keyed reply as it streams, reporting each finished `[id] text` line to [onBubble].
 *
 * A shown bubble cannot be taken back, so an id repeated later keeps its first line, and an id that is
 * not on the page is ignored. [byId] is exactly what [onBubble] heard.
 */
internal class IdKeyedReplyReader(
    page: TranslatablePage,
    private val onBubble: (bubbleId: Int, text: String) -> Unit
) {
    private val pageIds = page.panels.flatten().mapTo(mutableSetOf()) { it.bubbleId }
    private val lines = linkedMapOf<Int, String>()
    private val splitter = ReplyLineSplitter(::accept)

    val byId: Map<Int, String> get() = lines

    fun feed(bytes: ByteArray) = splitter.feed(bytes)

    /** Reads the unterminated tail as a line. Only for a reply the model ended; see [ReplyLineSplitter.finish]. */
    fun finish() = splitter.finish()

    private fun accept(line: String) {
        val match = ID_KEYED_LINE.matchEntire(line) ?: return
        val id = match.groupValues[1].toIntOrNull() ?: return
        val text = match.groupValues[2].trim()
        if (id in pageIds && lines.putIfAbsent(id, text) == null) onBubble(id, text)
    }
}

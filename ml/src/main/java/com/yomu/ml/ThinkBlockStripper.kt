package com.yomu.ml

/**
 * The most reply text held while it may be thoughts whose block opened in the prompt (#500). It is about
 * the network translator's 4096-token reply cap in English, so thoughts that still leave room for an
 * answer are held whole. Text held past it is shown as if no thoughts were coming.
 */
internal const val REASONING_HOLD_LIMIT = 16_000

/**
 * Removes a reasoning model's thoughts from streamed reply text, so they never reach the line parse
 * (ADR-0023). Thoughts come as `<think>…</think>` blocks or, when the chat template opened the block in
 * the prompt, as everything before a bare `</think>` (#500).
 *
 * A shown bubble can't be taken back, and nothing at the start of a reply shows whether a bare `</think>`
 * is coming. A reply whose first line is an `[id]` line is taken to be the answer and passes as it arrives.
 * Any other reply is held until a bare `</think>` drops everything before it, or until the reply ends or
 * passes [REASONING_HOLD_LIMIT], which shows all of it in order. A tag split across pieces is still found:
 * text that could be the start of a tag is held back until the next piece settles it.
 */
internal class ThinkBlockStripper {
    private val held = StringBuilder()

    /** Until the first line is judged, and then while a reply that opened with prose may still be reasoning. */
    private var holding = true
    private var openedWithProse = false
    private var thinking = false

    /** The tag that would change [thinking]. */
    private val nextTag: String get() = if (thinking) CLOSE else OPEN

    /** The part of [piece], with what was held before it, that is known to lie outside the model's thoughts. */
    fun feed(piece: String): String {
        held.append(piece)
        if (holding) holding = mayBeReasoning()
        return if (holding) "" else stripBlocks()
    }

    /** Whatever was held back at the end of the reply, unless it lies inside a block that never closed. */
    fun finish(): String {
        holding = false
        val visible = stripBlocks()
        val rest = if (thinking) "" else held.toString()
        held.setLength(0)
        return visible + rest
    }

    /**
     * Whether [held] may still be thoughts whose block opened in the prompt. A bare `</think>` settles it,
     * and everything before that tag is dropped.
     */
    private fun mayBeReasoning(): Boolean {
        val withinLimit = held.length <= REASONING_HOLD_LIMIT
        if (!openedWithProse) {
            val line = firstLine() ?: return withinLimit
            if (isIdKeyedLine(line)) return false
            openedWithProse = true
        }
        val close = held.indexOf(CLOSE)
        if (close < 0) return withinLimit
        held.delete(0, close + CLOSE.length)
        return false
    }

    /** The first line of [held] that has text in it, once it is whole. */
    private fun firstLine(): String? {
        val start = held.indexOfFirst { !it.isWhitespace() }
        val end = if (start < 0) -1 else held.indexOf("\n", start)
        return if (end < 0) null else held.substring(start, end)
    }

    /** What [held] has outside `<think>…</think>` blocks, keeping back a tail that could be the start of a tag. */
    private fun stripBlocks(): String {
        val visible = StringBuilder()
        while (true) {
            val tag = nextTag
            val at = held.indexOf(tag)
            if (at < 0) break
            if (!thinking) visible.append(held, 0, at)
            held.delete(0, at + tag.length)
            thinking = !thinking
        }
        val keep = partialTagLength(nextTag)
        if (!thinking) visible.append(held, 0, held.length - keep)
        held.delete(0, held.length - keep)
        return visible.toString()
    }

    /** The length of the longest tail of [held] that is a proper prefix of [tag]. */
    private fun partialTagLength(tag: String): Int =
        (minOf(tag.length - 1, held.length) downTo 1).firstOrNull { held.endsWith(tag.substring(0, it)) } ?: 0

    private companion object {
        const val OPEN = "<think>"
        const val CLOSE = "</think>"
    }
}

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
 * Any other reply is held until its first tag: a `<think>` shows thoughts come in whole blocks, and a bare
 * `</think>` drops everything before it. With no tag, the held text is shown in order once the reply ends
 * or passes [REASONING_HOLD_LIMIT]. A tag split across pieces is still found: text that could be the start
 * of a tag is held back until the next piece settles it.
 */
internal class ThinkBlockStripper {
    private val held = StringBuilder()
    private var phase = Phase.FIRST_LINE
    private var thinking = false

    /** The tag that would change [thinking]. */
    private val nextTag: String get() = if (thinking) CLOSE else OPEN

    /** The part of [piece], with what was held before it, that is known to lie outside the model's thoughts. */
    fun feed(piece: String): String {
        held.append(piece)
        if (phase == Phase.FIRST_LINE) judgeFirstLine()
        if (phase == Phase.HOLDING) endHoldAtFirstTag()
        if (phase != Phase.PASSING && held.length > REASONING_HOLD_LIMIT) phase = Phase.PASSING
        return if (phase == Phase.PASSING) stripBlocks() else ""
    }

    /** Whatever was held back at the end of the reply, unless it lies inside a block that never closed. */
    fun finish(): String {
        val visible = stripBlocks()
        val rest = if (thinking) "" else held.toString()
        held.setLength(0)
        return visible + rest
    }

    /** Passes a reply whose first line with text is an `[id]` line, and holds any other, once that line is whole. */
    private fun judgeFirstLine() {
        val start = held.indexOfFirst { !it.isWhitespace() }
        val end = if (start < 0) -1 else held.indexOf("\n", start)
        if (end < 0) return
        phase = if (isIdKeyedLine(held.substring(start, end))) Phase.PASSING else Phase.HOLDING
    }

    /** Passes [held] from its first tag on, dropping everything before that tag when it is a bare `</think>`. */
    private fun endHoldAtFirstTag() {
        val open = held.indexOf(OPEN)
        val close = held.indexOf(CLOSE)
        if (close >= 0 && (open < 0 || close < open)) held.delete(0, close + CLOSE.length)
        if (open >= 0 || close >= 0) phase = Phase.PASSING
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

    private enum class Phase {
        /** No line with text is whole yet, so nothing shows whether the reply is the answer. */
        FIRST_LINE,

        /** The reply opened with prose, which may be thoughts whose block opened in the prompt. */
        HOLDING,

        /** Any thoughts still to come are marked with whole tags. */
        PASSING
    }

    private companion object {
        const val OPEN = "<think>"
        const val CLOSE = "</think>"
    }
}

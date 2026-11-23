package com.yomu.ml

/**
 * Removes `<think>…</think>` blocks from streamed reply text, so a reasoning model's thoughts never
 * reach the line parse (ADR-0023). A tag split across pieces is still found: text that could be the
 * start of a tag is held back until the next piece settles it.
 */
internal class ThinkBlockStripper {
    private val held = StringBuilder()
    private var thinking = false

    /** The part of [piece], with what was held before it, that is known to lie outside a block. */
    fun feed(piece: String): String {
        held.append(piece)
        val visible = StringBuilder()
        while (true) {
            val tag = if (thinking) CLOSE else OPEN
            val at = held.indexOf(tag)
            if (at < 0) break
            if (!thinking) visible.append(held, 0, at)
            held.delete(0, at + tag.length)
            thinking = !thinking
        }
        val keep = partialTagLength(if (thinking) CLOSE else OPEN)
        if (!thinking) visible.append(held, 0, held.length - keep)
        held.delete(0, held.length - keep)
        return visible.toString()
    }

    /** Whatever was held back at the end of the reply, unless it lies inside a block that never closed. */
    fun finish(): String {
        val rest = if (thinking) "" else held.toString()
        held.setLength(0)
        return rest
    }

    /** The length of the longest tail of [held] that is a proper prefix of [tag]. */
    private fun partialTagLength(tag: String): Int =
        (minOf(tag.length - 1, held.length) downTo 1).firstOrNull { held.endsWith(tag.substring(0, it)) } ?: 0

    private companion object {
        const val OPEN = "<think>"
        const val CLOSE = "</think>"
    }
}

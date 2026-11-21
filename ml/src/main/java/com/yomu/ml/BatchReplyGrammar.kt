package com.yomu.ml

import com.yomu.core.TranslatablePage
import kotlin.math.ceil

/**
 * The shortest limit a reply line gets, a backstop for a line that loops. 160 is ~2x the longest
 * English reference line (75 characters) on the pages ADR-0013 was tuned on, so ordinary lines keep it.
 */
private const val LINE_LIMIT_FLOOR = 160

/**
 * Translation characters to expect per source character, for the target that runs longest: English
 * references ran 2.39x their Japanese source on ADR-0013's pages, and French runs 1.29x English on
 * long strings (ADR-0013, amendment #451).
 */
private const val TRANSLATION_GROWTH = 3.1

/**
 * Mirrors `MAX_REPETITION_THRESHOLD` in llama.cpp's `src/llama-grammar.cpp`, which reads a repetition
 * bound past it as no bound at all. Recheck it when the submodule moves.
 */
private const val LLAMA_MAX_REPETITION = 2000

/**
 * GBNF pinning the reply to exactly one `[id] text` line per bubble, in prompt order, with the
 * ids as literals. Preamble, refusal, a dropped id, a merged pair and a re-ordered reply are all
 * structurally unreachable — which is what the translation slot's reply parser and the post-hoc
 * guards were catching after the fact.
 *
 * Structural only: nothing here forbids Japanese characters or constrains content, so a model
 * that fails to translate still shows it rather than having the grammar hide it. [excludeIdBracket]
 * also keeps `[` out of a line (#214).
 *
 * The last newline is optional: the model ends its reply with end-of-generation, not a newline,
 * and a grammar still waiting for one masks that token, so the model writes on to the line cap (#368).
 *
 * Each line gets its own limit (#451). Lines that keep [LINE_LIMIT_FLOOR] share the `line` rule, so
 * a page with no long line gets just `root` and `line`.
 */
internal fun batchReplyGrammar(page: TranslatablePage, excludeIdBracket: Boolean): String {
    val bubbles = page.panels.flatten()
    val limits = bubbles.map { lineLimit(it.sourceText) }
    val root = bubbles.zip(limits).joinToString(" ") { (bubble, limit) ->
        "\"[${bubble.bubbleId}] \" ${lineRule(limit)} \"\\n\""
    } + "?"
    val excluded = if (excludeIdBracket) "\\r\\n\\[" else "\\r\\n"
    return "root ::= $root\n" + limits.distinct().joinToString("") { "${lineRule(it)} ::= [^$excluded]{1,$it}\n" }
}

/**
 * [LINE_LIMIT_FLOOR], or twice the line's expected translation when that is longer, so a long line
 * isn't cut off mid-sentence. Never past [LLAMA_MAX_REPETITION], so every line keeps a bound.
 */
private fun lineLimit(source: String): Int =
    maxOf(LINE_LIMIT_FLOOR, ceil(2 * TRANSLATION_GROWTH * source.length).toInt()).coerceAtMost(LLAMA_MAX_REPETITION)

private fun lineRule(limit: Int): String = if (limit == LINE_LIMIT_FLOOR) "line" else "line-$limit"

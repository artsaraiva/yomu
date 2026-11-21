package com.yomu.app.ui.home

import kotlin.random.Random

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

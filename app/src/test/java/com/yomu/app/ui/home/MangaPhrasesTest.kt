package com.yomu.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class MangaPhrasesTest {
    @Test
    fun `every phrase shows once before any repeats`() {
        repeat(50) { seed ->
            val deck = PhraseDeck(MANGA_PHRASES, Random(seed))

            assertEquals(MANGA_PHRASES.toSet(), List(MANGA_PHRASES.size) { deck.next() }.toSet())
            assertEquals(MANGA_PHRASES.toSet(), List(MANGA_PHRASES.size) { deck.next() }.toSet())
        }
    }

    @Test
    fun `a new round never opens with the phrase just shown`() {
        repeat(200) { seed ->
            val deck = PhraseDeck(listOf("Inking…", "Toning…", "Lettering…"), Random(seed))
            val shown = List(30) { deck.next() }

            shown.zipWithNext().forEach { (before, after) -> assertNotEquals(before, after) }
        }
    }
}

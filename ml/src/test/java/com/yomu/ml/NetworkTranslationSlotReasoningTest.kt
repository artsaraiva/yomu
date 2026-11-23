package com.yomu.ml

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** A reasoning model's thoughts on the network translator when the prompt opened its `<think>` block (#500, ADR-0023). */
class NetworkTranslationSlotReasoningTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun translatePage_reasoningThatEndsInABareCloseTagNeverReachesATranslation() = runTest {
        server.enqueue(
            chatStream("Okay, bubble 1 is a greeting.\n[1] Hi?\n", "Too casual.\n</th", "ink>\n\n[1] Hello\n[2] Goodbye\n")
        )
        val reported = mutableListOf<Pair<Int, String>>()

        val result = networkSlot(server.base()).translatePage(page(1 to "こんにちは", 2 to "さようなら")) { id, text ->
            reported += id to text
        }

        assertEquals(listOf(1 to "Hello", 2 to "Goodbye"), reported)
        assertEquals("[1] Hello\n[2] Goodbye", result.rawResponse)
    }

    @Test
    fun translatePage_replyThatOpensWithProseKeepsItsLinesAroundAWholeThinkBlock() = runTest {
        server.enqueue(chatStream("Here you go:\n[1] Hello\n<thi", "nk>[2] no</think>\n[2] Goodbye\n"))
        val reported = mutableListOf<Pair<Int, String>>()

        networkSlot(server.base()).translatePage(page(1 to "こんにちは", 2 to "さようなら")) { id, text ->
            reported += id to text
        }

        assertEquals(listOf(1 to "Hello", 2 to "Goodbye"), reported)
    }

    @Test
    fun translatePage_replyThatOpensWithProseAndNeverThinksShowsEveryLineOnce() = runTest {
        server.enqueue(chatStream("Here are the translations:\n\n[1] Hel", "lo\n[2] Good", "bye"))
        val reported = mutableListOf<Pair<Int, String>>()

        val result = networkSlot(server.base()).translatePage(page(1 to "こんにちは", 2 to "さようなら")) { id, text ->
            reported += id to text
        }

        assertEquals(listOf(1 to "Hello", 2 to "Goodbye"), reported)
        assertEquals("Here are the translations:\n\n[1] Hello\n[2] Goodbye", result.rawResponse)
    }

    @Test
    fun translatePage_reasoningUpToTheHoldLimitIsDroppedAndPastItIsShownInOrder() = runTest {
        val drafts = "Okay, two bubbles.\n[1] Hi?\n[2] Bye?\n"
        val expectedByHeldLength = mapOf(
            REASONING_HOLD_LIMIT to listOf(1 to "Hello", 2 to "Goodbye"),
            REASONING_HOLD_LIMIT + 1 to listOf(1 to "Hi?", 2 to "Bye?")
        )
        for ((heldLength, expected) in expectedByHeldLength) {
            server.enqueue(chatStream(drafts.padEnd(heldLength, '.'), "</think>\n[1] Hello\n[2] Goodbye\n"))
            val reported = mutableListOf<Pair<Int, String>>()

            networkSlot(server.base()).translatePage(page(1 to "こんにちは", 2 to "さようなら")) { id, text ->
                reported += id to text
            }

            assertEquals("held $heldLength characters", expected, reported)
        }
    }
}

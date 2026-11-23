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
}

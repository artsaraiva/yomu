package com.yomu.ml

import com.yomu.core.TranslationOutcome
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The network translator's request and streamed reply, against a local server (#486, ADR-0023). */
class NetworkTranslationSlotTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun translatePage_streamedReplyReportsEachLineOnceInOrder() = runTest {
        server.enqueue(chatStream("[1] Hel", "lo\n[2] Good", "bye\n"))
        val reported = mutableListOf<Pair<Int, String>>()

        val result = networkSlot(server.base()).translatePage(page(1 to "こんにちは", 2 to "さようなら")) { id, text ->
            reported += id to text
        }

        assertEquals(listOf(1 to "Hello", 2 to "Goodbye"), reported)
        assertEquals(mapOf(1 to "Hello", 2 to "Goodbye"), result.byId)
        assertEquals(TranslationOutcome.SUCCESS, result.outcome)
        assertEquals("/v1/chat/completions", server.takeRequest().path)
    }
}

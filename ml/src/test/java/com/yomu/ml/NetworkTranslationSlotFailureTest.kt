package com.yomu.ml

import com.yomu.core.ERROR_SERVER_FAILED
import com.yomu.core.ERROR_SERVER_KEY_REJECTED
import com.yomu.core.ERROR_SERVER_MODEL_NOT_FOUND
import com.yomu.core.ERROR_SERVER_TIMEOUT
import com.yomu.core.ERROR_SERVER_UNREACHABLE
import com.yomu.core.TranslationOutcome
import java.net.ServerSocket
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the network translator fails: each cause its own code, the limits, and cancelling (#486, ADR-0023). */
class NetworkTranslationSlotFailureTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun translatePage_eachHttpErrorFailsThePageWithItsOwnCode() = runTest {
        val expected = mapOf(
            401 to ERROR_SERVER_KEY_REJECTED,
            403 to ERROR_SERVER_KEY_REJECTED,
            404 to ERROR_SERVER_MODEL_NOT_FOUND,
            400 to ERROR_SERVER_FAILED,
            500 to ERROR_SERVER_FAILED,
            503 to ERROR_SERVER_FAILED
        )
        for ((status, code) in expected) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("""{"error":{"message":"no"}}"""))

            val result = networkSlot(server.base()).translatePage(page(1 to "こんにちは"))

            assertEquals("HTTP $status", TranslationOutcome.ERROR, result.outcome)
            assertEquals("HTTP $status", code, result.errorCode)
            assertEquals("HTTP $status", emptyMap<Int, String>(), result.byId)
        }
    }

    @Test
    fun translatePage_serverThatCannotBeReachedFailsAsUnreachable() = runTest {
        val closedPort = ServerSocket(0).use { it.localPort }
        for (base in listOf("http://127.0.0.1:$closedPort", "not an address")) {
            val result = networkSlot(base).translatePage(page(1 to "こんにちは"))

            assertEquals(base, TranslationOutcome.ERROR, result.outcome)
            assertEquals(base, ERROR_SERVER_UNREACHABLE, result.errorCode)
        }
    }

    @Test
    fun translatePage_serverThatStallsMidReplyTimesOutAtThePageLimitKeepingLinesAlreadyShown() = runTest {
        val reported = mutableListOf<Pair<Int, String>>()
        val started = System.nanoTime()

        val result = StallingServer(chatEvents("[1] Hello\n", finishReason = null)).use { stalling ->
            networkSlot(stalling.baseUrl, pageTimeoutMs = PAGE_LIMIT_MS)
                .translatePage(page(1 to "こんにちは", 2 to "さようなら")) { id, text -> reported += id to text }
        }

        assertEquals(TranslationOutcome.TIMEOUT, result.outcome)
        assertEquals(ERROR_SERVER_TIMEOUT, result.errorCode)
        assertEquals(listOf(1 to "Hello"), reported)
        assertEquals(mapOf(1 to "Hello"), result.byId)
        assertTrue((System.nanoTime() - started) / 1_000_000 >= PAGE_LIMIT_MS)
    }

    @Test
    fun translatePage_serverThatNeverAnswersTimesOutAtThePageLimit() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val started = System.nanoTime()

        val result = networkSlot(server.base(), pageTimeoutMs = PAGE_LIMIT_MS).translatePage(page(1 to "こんにちは"))

        assertEquals(TranslationOutcome.TIMEOUT, result.outcome)
        assertEquals(ERROR_SERVER_TIMEOUT, result.errorCode)
        assertTrue((System.nanoTime() - started) / 1_000_000 >= PAGE_LIMIT_MS)
    }

    private companion object {
        const val PAGE_LIMIT_MS = 400L
    }
}

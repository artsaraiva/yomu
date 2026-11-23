package com.yomu.ml

import com.yomu.core.ERROR_SERVER_FAILED
import com.yomu.core.ERROR_SERVER_KEY_REJECTED
import com.yomu.core.ERROR_SERVER_MODEL_NOT_FOUND
import com.yomu.core.TranslationOutcome
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
}

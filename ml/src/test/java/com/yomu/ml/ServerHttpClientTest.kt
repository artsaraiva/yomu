package com.yomu.ml

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Plain http only to nearby addresses, checked on what the name resolves to (#488, ADR-0023). */
class ServerHttpClientTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start(LOOPBACK, 0)

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun newCall_httpToANameThatResolvesToAPublicAddressIsRefusedBeforeAnyRequestIsSent() {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val client = ServerHttpClient(stubNetwork("translate.example.com" to listOf("203.0.113.7")).build())

        assertThrows(HttpsRequiredException::class.java) { client.get("http://translate.example.com") }

        assertEquals(0, server.requestCount)
    }

    private fun ServerHttpClient.get(base: String): Int =
        newCall(Request.Builder().url("$base:${server.port}/v1/models").build()).execute().use { it.code }
}

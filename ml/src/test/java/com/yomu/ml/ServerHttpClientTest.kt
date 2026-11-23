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

    @Test
    fun newCall_httpToANameThatResolvesToANearbyAddressIsSent() {
        for (address in listOf("192.168.1.10", "10.0.2.2", "100.64.0.1", "fd00::10", "fe80::1")) {
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            val client = ServerHttpClient(stubNetwork("nas.home.arpa" to listOf(address)).build())

            assertEquals(address, 200, client.get("http://nas.home.arpa"))
        }
        assertEquals(5, server.requestCount)
    }

    @Test
    fun newCall_httpToANameWithOneNearbyAndOnePublicAddressIsRefused() {
        val client = ServerHttpClient(stubNetwork("nas.example.com" to listOf("192.168.1.10", "203.0.113.7")).build())

        assertThrows(HttpsRequiredException::class.java) { client.get("http://nas.example.com") }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun newCall_httpToAnIpAddressIsJudgedByThatAddress() {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val client = ServerHttpClient(stubNetwork().build())

        assertThrows(HttpsRequiredException::class.java) { client.get("http://203.0.113.7") }
        assertEquals(200, client.get("http://192.168.1.10"))

        assertEquals(1, server.requestCount)
    }

    private fun ServerHttpClient.get(base: String): Int =
        newCall(Request.Builder().url("$base:${server.port}/v1/models").build()).execute().use { it.code }
}

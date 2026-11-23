package com.yomu.ml

import java.net.InetSocketAddress
import java.net.Proxy
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

    @Test
    fun newCall_httpRedirectFromANearbyServerToAPublicAddressIsRefused() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://203.0.113.7:${server.port}/v1/models"))
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val client = ServerHttpClient(stubNetwork().build())

        assertThrows(HttpsRequiredException::class.java) { client.get("http://192.168.1.10") }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun newCall_httpSkipsTheClientsProxySoTheServersOwnAddressIsChecked() {
        MockWebServer().use { proxy ->
            proxy.start(LOOPBACK, 0)
            proxy.enqueue(MockResponse().setBody("""{"data":[]}"""))
            val viaProxy = stubNetwork("translate.example.com" to listOf("203.0.113.7"))
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(LOOPBACK, proxy.port)))
            val client = ServerHttpClient(viaProxy.build())

            assertThrows(HttpsRequiredException::class.java) { client.get("http://translate.example.com") }

            assertEquals(0, proxy.requestCount)
        }
    }

    @Test
    fun newCall_httpsToANameThatResolvesToAnyAddressIsSent() {
        val tls = TlsFixture("api.example.com").also { it.serve(server) }
        for (addresses in listOf(listOf("203.0.113.7"), listOf("192.168.1.10", "203.0.113.7"), listOf("2001:db8::7"))) {
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            val client = ServerHttpClient(tls.trust(stubNetwork("api.example.com" to addresses)).build())

            assertEquals(addresses.toString(), 200, client.get("https://api.example.com"))
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun newCall_httpsRedirectToPlainHttpIsNotFollowed() {
        val tls = TlsFixture("api.example.com").also { it.serve(server) }
        MockWebServer().use { plain ->
            plain.start(LOOPBACK, 0)
            plain.enqueue(MockResponse().setBody("""{"data":[]}"""))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://api.example.com:${plain.port}/v1/models"))
            val client = ServerHttpClient(tls.trust(stubNetwork("api.example.com" to listOf("203.0.113.7"))).build())

            assertEquals(302, client.get("https://api.example.com"))

            assertEquals(0, plain.requestCount)
        }
    }

    @Test
    fun newCall_httpRedirectToHttpsIsNotFollowed() {
        val tlsFixture = TlsFixture("api.example.com")
        MockWebServer().use { tls ->
            tlsFixture.serve(tls)
            tls.start(LOOPBACK, 0)
            tls.enqueue(modelsReply())
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://api.example.com:${tls.port}/v1/models"))
            val network = stubNetwork("nas.home.arpa" to listOf("192.168.1.10"), "api.example.com" to listOf("203.0.113.7"))
            val client = ServerHttpClient(tlsFixture.trust(network).build())

            assertEquals(302, client.get("http://nas.home.arpa"))

            assertEquals(0, tls.requestCount)
        }
    }

    private fun modelsReply(): MockResponse = MockResponse().setBody("""{"data":[]}""")

    private fun ServerHttpClient.get(base: String): Int =
        newCall(Request.Builder().url("$base:${server.port}/v1/models").build()).execute().use { it.code }
}

package com.yomu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yomu.app.di.AppModule
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.ml.NetworkTranslationSlot
import com.yomu.ml.NetworkTranslatorConfig
import com.yomu.ml.isNearbyAddress
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Plain http to a nearby server on a device, where Android's cleartext policy applies and the JVM tests
 * can't follow (#488, ADR-0023). Without the manifest's cleartext permission, Android refuses every
 * http address, nearby or not.
 */
@RunWith(AndroidJUnit4::class)
class NearbyCleartextTest {

    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun pageTranslatesOverPlainHttpOnANearbyServer() = runTest {
        val address = checkNotNull(ownNearbyAddress()) { "the device needs a nearby network address" }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(REPLY))
        server.start(address, 0)
        val config = NetworkTranslatorConfig(baseUrl = "http://${address.hostAddress}:${server.port}", apiKey = "", model = "test-model")
        val slot = NetworkTranslationSlot(AppModule.provideOkHttpClient(), { config })

        val result = slot.translatePage(TranslatablePage(listOf(listOf(TranslatableBubble(1, "こんにちは")))))

        assertEquals(TranslationOutcome.SUCCESS, result.outcome)
        assertEquals(mapOf(1 to "Hello"), result.byId)
        assertEquals(1, server.requestCount)
    }

    /**
     * The device's own IPv4 address on its network, such as the emulator's 10.0.2.15. Loopback won't do:
     * Android lets cleartext reach localhost whatever the manifest says.
     */
    private fun ownNearbyAddress(): InetAddress? = NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .firstOrNull { it is Inet4Address && isNearbyAddress(it) }

    private companion object {
        val REPLY = """
            |data: {"choices":[{"index":0,"delta":{"content":"[1] Hello\n"},"finish_reason":null}]}
            |
            |data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}
            |
            |data: [DONE]
            |
            |
        """.trimMargin()
    }
}

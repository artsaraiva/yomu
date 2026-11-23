package com.yomu.ml

import com.yomu.core.GenerationParams
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.core.TranslationStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun translatePage_baseAddressAlreadyEndingInV1IsNotDoubled() = runTest {
        val expected = mapOf(
            "/v1" to "/v1/chat/completions",
            "/v1/" to "/v1/chat/completions",
            "/api/v1" to "/api/v1/chat/completions",
            "/api" to "/api/v1/chat/completions"
        )
        for ((basePath, requestPath) in expected) {
            server.enqueue(chatStream("[1] Hello\n"))

            networkSlot(server.url(basePath).toString()).translatePage(page(1 to "こんにちは"))

            assertEquals(basePath, requestPath, server.takeRequest().path)
        }
    }

    @Test
    fun translatePage_requestSendsThePagePromptModelAndOnlyTheServerSamplerSettings() = runTest {
        server.enqueue(chatStream("[1] Hello\n"))
        val generation = GenerationParams(temperature = 0.7f, topK = 20, topP = 0.8f, penaltyRepeat = 1.1f, penaltyPresent = 1.5f)

        networkSlot(server.base(), model = "gemma3:12b", generation = generation)
            .translatePage(page(1 to "こんにちは", 2 to "さようなら"))

        val body = server.takeRequest().json()
        assertEquals(setOf("model", "messages", "stream", "temperature", "top_p", "max_tokens"), body.keySet())
        assertEquals("gemma3:12b", body["model"].asString)
        assertEquals(0.7, body["temperature"].asDouble, 1e-6)
        assertEquals(0.8, body["top_p"].asDouble, 1e-6)
        assertEquals(4096, body["max_tokens"].asInt)
        assertTrue(body["stream"].asBoolean)
        val message = body.getAsJsonArray("messages").single().asJsonObject
        assertEquals("user", message["role"].asString)
        assertEquals(
            "Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.\n" +
                "[1] こんにちは\n[2] さようなら\n",
            message["content"].asString
        )
    }

    @Test
    fun translatePage_samplerValueOutsideItsBoundIsSentAsTheDefault() = runTest {
        server.enqueue(chatStream("[1] Hello\n"))

        networkSlot(server.base(), generation = GenerationParams(temperature = 5f, topP = Float.NaN))
            .translatePage(page(1 to "こんにちは"))

        val body = server.takeRequest().json()
        assertEquals(0.2, body["temperature"].asDouble, 1e-6)
        assertEquals(0.9, body["top_p"].asDouble, 1e-6)
    }

    @Test
    fun translatePage_sendsTheKeyAsABearerTokenOnlyWhenOneIsSet() = runTest {
        server.enqueue(chatStream("[1] Hello\n"))
        server.enqueue(chatStream("[1] Hello\n"))
        server.enqueue(chatStream("[1] Hello\n"))

        networkSlot(server.base(), apiKey = "sk-test").translatePage(page(1 to "こんにちは"))
        networkSlot(server.base(), apiKey = "").translatePage(page(1 to "こんにちは"))
        networkSlot(server.base(), apiKey = "  ").translatePage(page(1 to "こんにちは"))

        assertEquals("Bearer sk-test", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun translatePage_thinkBlocksNeverReachATranslationEvenWhenTheirTagsSplitAcrossChunks() = runTest {
        server.enqueue(
            chatStream(
                "<thi", "nk>\n[1] Hi?\n[2] Bye?\n</th", "ink>\n[1] Hello\n",
                "<think>[2] no</think>[2] Good", "bye <", "3\n"
            )
        )
        val reported = mutableListOf<Pair<Int, String>>()

        val result = networkSlot(server.base()).translatePage(page(1 to "こんにちは", 2 to "さようなら")) { id, text ->
            reported += id to text
        }

        assertEquals(listOf(1 to "Hello", 2 to "Goodbye <3"), reported)
        assertEquals(mapOf(1 to "Hello", 2 to "Goodbye <3"), result.byId)
        assertEquals("[1] Hello\n[2] Goodbye <3", result.rawResponse)
    }

    @Test
    fun translatePage_onlyAReplyTheModelEndedAnswersItsUnterminatedLastLine() = runTest {
        val endings = mapOf(
            "stop" to chatStream("[1] Hello\n[2] Goodbye", finishReason = "stop"),
            "length" to chatStream("[1] Hello\n[2] Good", finishReason = "length"),
            "content_filter" to chatStream("[1] Hello\n[2] Good", finishReason = "content_filter"),
            "no ending" to MockResponse().setBody(chatEvents("[1] Hello\n[2] Good", finishReason = null))
        )
        val slot = networkSlot(server.base())
        for ((ending, reply) in endings) {
            server.enqueue(reply)

            val result = slot.translatePage(page(1 to "こんにちは", 2 to "さようなら"))

            val expected = if (ending == "stop") mapOf(1 to "Hello", 2 to "Goodbye") else mapOf(1 to "Hello")
            assertEquals(ending, expected, result.byId)
        }
    }

    @Test
    fun translatePage_pageWithNoBubblesSendsNothing() = runTest {
        server.enqueue(chatStream("[1] Hello\n"))

        val result = networkSlot(server.base()).translatePage(TranslatablePage(emptyList()))

        assertEquals(0, server.requestCount)
        assertEquals(TranslationOutcome.SUCCESS, result.outcome)
        assertEquals(emptyMap<Int, String>(), result.byId)
    }

    @Test
    fun translatePage_replyOfNothingButThoughtsIsBlank() = runTest {
        server.enqueue(chatStream("<think>[1] Hello?</think>", "\n  \n"))

        val result = networkSlot(server.base()).translatePage(page(1 to "こんにちは"))

        assertEquals(TranslationOutcome.BLANK, result.outcome)
        assertNull(result.errorCode)
        assertEquals(emptyMap<Int, String>(), result.byId)
    }

    @Test
    fun translatePage_firstLineForAnIdWinsAndIdsNotOnThePageAreIgnored() = runTest {
        server.enqueue(chatStream("[1] Hello\n[9] Ghost\n[1] Hi again\n[2] Goodbye\n"))
        val reported = mutableListOf<Pair<Int, String>>()

        val result = networkSlot(server.base()).translatePage(page(1 to "こんにちは", 2 to "さようなら")) { id, text ->
            reported += id to text
        }

        assertEquals(listOf(1 to "Hello", 2 to "Goodbye"), reported)
        assertEquals(mapOf(1 to "Hello", 2 to "Goodbye"), result.byId)
    }

    @Test
    fun translatePage_readsTheConfigurationAgainForEveryPage() = runTest {
        server.enqueue(chatStream("[1] Hello\n"))
        server.enqueue(chatStream("[1] Hello\n"))
        var config = NetworkTranslatorConfig(server.base(), apiKey = "", model = "first")
        val slot = NetworkTranslationSlot(OkHttpClient(), { config })

        slot.translatePage(page(1 to "こんにちは"))
        config = config.copy(apiKey = "sk-new", model = "second")
        slot.translatePage(page(1 to "こんにちは"))

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("first", first.json()["model"].asString)
        assertNull(first.getHeader("Authorization"))
        assertEquals("second", second.json()["model"].asString)
        assertEquals("Bearer sk-new", second.getHeader("Authorization"))
    }

    @Test
    fun slot_isReadyWithoutCallingTheServerAndStaysUsableAfterClosing() = runTest {
        val slot = networkSlot(server.base())

        assertTrue(slot.ensureReady())
        assertEquals(TranslationStatus.Ready, slot.status)
        slot.endSession()
        slot.close()
        server.enqueue(chatStream("[1] Hello\n"))

        assertEquals(mapOf(1 to "Hello"), slot.translatePage(page(1 to "こんにちは")).byId)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun config_textLeavesOutTheKeyAndTheAddress() {
        val text = NetworkTranslatorConfig("http://192.168.1.20:11434", "sk-secret", "qwen3:8b").toString()

        assertFalse(text, text.contains("sk-secret"))
        assertFalse(text, text.contains("192.168.1.20"))
        assertTrue(text, text.contains("qwen3:8b"))
    }
}

package com.yomu.app.service

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yomu.core.GenerationParams
import com.yomu.core.RuntimeLimits
import com.yomu.core.TranslationOutcome
import com.yomu.pipeline.PipelineResult
import com.yomu.pipeline.TranslationPipeline.Stage
import com.yomu.pipeline.translation.TranslatedBubble
import com.yomu.pipeline.translation.TranslationResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageReportTest {

    private val capture = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    private val origin = ReportOrigin(appVersion = "1.0", device = "samsung SM-S911B")
    private val settings = PageSettings(
        translationModel = "qwen2.5-1.5b",
        detectionModel = "bubble-detector",
        ocrModel = "manga-ocr",
        generation = GenerationParams(temperature = 0.3f),
        runtime = RuntimeLimits(threads = 4, contextTokens = 2048),
        detectionThreshold = 0.25f,
        fontScale = 1.2f
    )

    private fun page(
        bubbles: List<TranslatedBubble>,
        bounds: Map<Int, FloatArray>,
        rawResponse: String,
        outcome: TranslationOutcome = TranslationOutcome.SUCCESS,
        errorCode: String? = null,
        batchOverflowFallback: Boolean = false
    ) = PipelineResult(
        typesetBubbles = emptyList(),
        translationResult = TranslationResult(bubbles, rawResponse, 5_700L, outcome, errorCode, batchOverflowFallback),
        pageWidth = 1080,
        pageHeight = 2340,
        totalTimeMs = 9_000L,
        timeToFirstBubbleMs = 4_100L,
        stageTimesMs = linkedMapOf(
            Stage.BUBBLE_DETECTION to 350L,
            Stage.OCR to 2_400L,
            Stage.CONTEXT_ASSEMBLY to 50L,
            Stage.TRANSLATION to 6_200L
        ),
        bubbleBounds = bounds
    )

    private val twoBubblePage = page(
        bubbles = listOf(
            TranslatedBubble(0, "こんにちは", "Hello", answered = true),
            TranslatedBubble(1, "さようなら", "Goodbye", answered = true)
        ),
        bounds = linkedMapOf(0 to floatArrayOf(10f, 20f, 110f, 220f), 1 to floatArrayOf(300f, 40f, 420f, 260f)),
        rawResponse = "[0] Hello\n[1] Goodbye"
    )

    private fun report(page: PipelineResult, recalled: Boolean = false) =
        pageReportFiles(capture, page, settings, recalled, origin)

    private fun json(files: Map<String, ByteArray>): JsonObject =
        JsonParser().parse(String(files.getValue("page.json"))).asJsonObject

    private fun JsonObject.bubble(index: Int): JsonObject = getAsJsonArray("bubbles")[index].asJsonObject

    @Test
    fun `a report holds the capture, the page record and the raw reply`() {
        val files = report(twoBubblePage)

        assertEquals(listOf("capture.png", "page.json", "reply.txt"), files.keys.toList())
        assertArrayEquals(capture, files.getValue("capture.png"))
        assertEquals("[0] Hello\n[1] Goodbye", String(files.getValue("reply.txt")))
    }

    @Test
    fun `a normal page records each bubble's bounds, OCR text, translation and answer`() {
        val first = json(report(twoBubblePage)).bubble(0)

        assertEquals(0, first["id"].asInt)
        assertEquals(listOf(10f, 20f, 110f, 220f), first.getAsJsonArray("bounds").map { it.asFloat })
        assertEquals("こんにちは", first["ocrText"].asString)
        assertEquals("Hello", first["translation"].asString)
        assertTrue(first["answered"].asBoolean)
        assertEquals(2, json(report(twoBubblePage)).getAsJsonArray("bubbles").size())
    }

    @Test
    fun `a normal page records the settings that made it`() {
        val recorded = json(report(twoBubblePage)).getAsJsonObject("settings")

        assertEquals("qwen2.5-1.5b", recorded["translationModel"].asString)
        assertEquals("bubble-detector", recorded["detectionModel"].asString)
        assertEquals("manga-ocr", recorded["ocrModel"].asString)
        assertEquals(0.3f, recorded.getAsJsonObject("generation")["temperature"].asFloat)
        assertEquals(256, recorded.getAsJsonObject("generation")["maxTokens"].asInt)
        assertEquals(4, recorded.getAsJsonObject("runtime")["threads"].asInt)
        assertEquals(2048, recorded.getAsJsonObject("runtime")["contextTokens"].asInt)
        assertEquals(0.25f, recorded["detectionThreshold"].asFloat)
        assertEquals(1.2f, recorded["fontScale"].asFloat)
    }

    @Test
    fun `a normal page records its timings, outcome, build and device`() {
        val recorded = json(report(twoBubblePage))
        val timings = recorded.getAsJsonObject("timings")

        assertEquals(9_000L, timings["totalMs"].asLong)
        assertEquals(4_100L, timings["timeToFirstBubbleMs"].asLong)
        assertEquals(5_700L, timings["translationCallMs"].asLong)
        assertEquals(
            mapOf("BUBBLE_DETECTION" to 350L, "OCR" to 2_400L, "CONTEXT_ASSEMBLY" to 50L, "TRANSLATION" to 6_200L),
            timings.getAsJsonObject("stagesMs").entrySet().associate { it.key to it.value.asLong }
        )
        assertEquals("SUCCESS", recorded["outcome"].asString)
        assertEquals(JsonNull.INSTANCE, recorded["errorCode"])
        assertFalse(recorded["batchOverflowFallback"].asBoolean)
        assertFalse(recorded["recalled"].asBoolean)
        assertEquals("1.0", recorded["appVersion"].asString)
        assertEquals("samsung SM-S911B", recorded["device"].asString)
    }

    @Test
    fun `a partial page marks unanswered and unread bubbles and keeps its error`() {
        val partial = page(
            bubbles = listOf(
                TranslatedBubble(0, "こんにちは", "Hello", answered = true),
                TranslatedBubble(1, "さようなら", "さようなら", answered = false)
            ),
            bounds = linkedMapOf(
                0 to floatArrayOf(10f, 20f, 110f, 220f),
                1 to floatArrayOf(300f, 40f, 420f, 260f),
                2 to floatArrayOf(500f, 600f, 560f, 700f)
            ),
            rawResponse = "[0] Hello",
            outcome = TranslationOutcome.TIMEOUT,
            errorCode = "deadline"
        )

        val recorded = json(report(partial))

        assertEquals("TIMEOUT", recorded["outcome"].asString)
        assertEquals("deadline", recorded["errorCode"].asString)
        val unanswered = recorded.bubble(1)
        assertEquals("さようなら", unanswered["ocrText"].asString)
        assertEquals(JsonNull.INSTANCE, unanswered["translation"])
        assertFalse(unanswered["answered"].asBoolean)
        val unread = recorded.bubble(2)
        assertEquals(2, unread["id"].asInt)
        assertEquals(JsonNull.INSTANCE, unread["ocrText"])
        assertEquals(JsonNull.INSTANCE, unread["translation"])
        assertFalse(unread["answered"].asBoolean)
    }

    @Test
    fun `a batch-overflow page says it was answered per line`() {
        val overflowed = page(
            bubbles = listOf(TranslatedBubble(0, "こんにちは", "Hello", answered = true)),
            bounds = linkedMapOf(0 to floatArrayOf(10f, 20f, 110f, 220f)),
            rawResponse = "Hello",
            batchOverflowFallback = true
        )

        assertTrue(json(report(overflowed))["batchOverflowFallback"].asBoolean)
    }

    @Test
    fun `a recalled page describes its original translation`() {
        val files = report(twoBubblePage, recalled = true)
        val recorded = json(files)

        assertTrue(recorded["recalled"].asBoolean)
        assertEquals("Hello", recorded.bubble(0)["translation"].asString)
        assertEquals(9_000L, recorded.getAsJsonObject("timings")["totalMs"].asLong)
        assertEquals("[0] Hello\n[1] Goodbye", String(files.getValue("reply.txt")))
    }
}

package com.yomu.app.service

import com.yomu.app.translation.AppDestination
import com.yomu.app.translation.ReaderAction
import com.yomu.app.translation.ReaderMessage
import com.yomu.app.translation.Translator
import com.yomu.core.ERROR_MODEL_MISSING
import com.yomu.core.TranslationOutcome
import com.yomu.core.TranslationSlot
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.pipeline.PipelineResult
import com.yomu.pipeline.translation.TranslatedBubble
import com.yomu.pipeline.translation.TranslationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito

/** What the overlay draws and puts on the status line once a page is done (#509). */
class PageEndTest {
    private val third = Translator(
        "third", "Third", { true }, lazyOf(Mockito.mock(TranslationSlot::class.java)),
        readerMessages = mapOf(
            "third_quota" to ReaderMessage("Third is out of quota for today"),
            "third_signed_out" to ReaderMessage("Third signed you out", ReaderAction("Tap to sign in", AppDestination("settings/third")))
        )
    )
    private val onDevice = Translator.onDevice(Mockito.mock(LlamaTranslationBridge::class.java))
    private var asked = 0
    private val noting = Translator(
        "noting", "Noting", { true }, lazyOf(Mockito.mock(TranslationSlot::class.java)),
        readerMessages = mapOf("noting_down" to ReaderMessage("Noting is down")),
        pageNote = { "Noting answered from its cache".also { asked++ } }
    )

    @Test
    fun `a page another translator failed shows that translator's message`() {
        val page = page(TranslationOutcome.ERROR, "third_quota", answered = false)

        val end = pageEnd(page, third)

        assertNull(end.shown)
        assertEquals("Failed: Third is out of quota for today", end.statusLine)
    }

    @Test
    fun `a page another translator failed mid-reply is drawn with that translator's message`() {
        val page = page(TranslationOutcome.ERROR, "third_quota", answered = true)

        val end = pageEnd(page, third)

        assertSame(page, end.shown)
        assertEquals("Third is out of quota for today", end.statusLine)
    }

    @Test
    fun `a message with an action leaves its label on the status line and the action to tap`() {
        val failed = pageEnd(page(TranslationOutcome.ERROR, "third_signed_out", answered = false), third)
        val drawn = pageEnd(page(TranslationOutcome.ERROR, "third_signed_out", answered = true), third)

        assertEquals("Failed: Third signed you out\nTap to sign in", failed.statusLine)
        assertEquals("Third signed you out\nTap to sign in", drawn.statusLine)
        assertEquals(ReaderAction("Tap to sign in", AppDestination("settings/third")), failed.action)
        assertEquals(failed.action, drawn.action)
    }

    @Test
    fun `a message without an action, or a page with no failure, has nothing to tap`() {
        assertNull(pageEnd(page(TranslationOutcome.ERROR, "third_quota", answered = false), third).action)
        assertNull(pageEnd(page(TranslationOutcome.SUCCESS, null, answered = true), third).action)
    }

    @Test
    fun `a code the translator has no message for falls back to the phone's messages`() {
        val page = page(TranslationOutcome.NOT_LOADED, ERROR_MODEL_MISSING, answered = false)

        assertEquals("Failed: Translation model is missing — download it again in Settings", pageEnd(page, onDevice).statusLine)
        assertEquals("Failed: Translation model is missing — download it again in Settings", pageEnd(page, third).statusLine)
    }

    @Test
    fun `a translated page is drawn with its untranslated count`() {
        val page = page(TranslationOutcome.SUCCESS, null, answered = true, unanswered = 1)

        val end = pageEnd(page, third)

        assertSame(page, end.shown)
        assertEquals("1 of 2 bubbles not translated", end.statusLine)
    }

    @Test
    fun `a translated page shows its translator's note`() {
        val page = page(TranslationOutcome.SUCCESS, null, answered = true)

        val end = pageEnd(page, noting)

        assertSame(page, end.shown)
        assertEquals("Noting answered from its cache", end.statusLine)
        assertNull(end.failure)
        assertEquals(1, asked)
    }

    @Test
    fun `a note sits under the untranslated count`() {
        val end = pageEnd(page(TranslationOutcome.SUCCESS, null, answered = true, unanswered = 1), noting)

        assertEquals("1 of 2 bubbles not translated\nNoting answered from its cache", end.statusLine)
    }

    @Test
    fun `a failed page shows only its failure, drawn or not, and asks for no note`() {
        val failed = pageEnd(page(TranslationOutcome.ERROR, "noting_down", answered = false), noting)
        val drawn = pageEnd(page(TranslationOutcome.ERROR, "noting_down", answered = true), noting)

        assertEquals("Failed: Noting is down", failed.statusLine)
        assertEquals("Noting is down", drawn.statusLine)
        assertEquals(0, asked)
    }

    @Test
    fun `a translator with no note leaves a translated page's status line empty`() {
        val page = page(TranslationOutcome.SUCCESS, null, answered = true)

        assertNull(pageEnd(page, third).statusLine)
        assertNull(pageEnd(page, onDevice).statusLine)
        assertNull(pageEnd(page, null).statusLine)
    }

    private fun page(outcome: TranslationOutcome, errorCode: String?, answered: Boolean, unanswered: Int = 0): PipelineResult {
        val bubbles = listOf(TranslatedBubble(1, "こんにちは", if (answered) "Hello" else "こんにちは", answered)) +
            (1..unanswered).map { TranslatedBubble(1 + it, "またね", "またね", false) }
        return PipelineResult(emptyList(), TranslationResult(bubbles, "", 0L, outcome, errorCode), 100, 100, 0L)
    }
}

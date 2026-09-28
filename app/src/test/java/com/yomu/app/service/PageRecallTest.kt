package com.yomu.app.service

import com.yomu.app.overlay.OverlayBounds
import com.yomu.core.GenerationParams
import com.yomu.core.RuntimeLimits
import com.yomu.core.TranslationOutcome
import com.yomu.pipeline.PipelineResult
import com.yomu.pipeline.translation.TranslatedBubble
import com.yomu.pipeline.translation.TranslationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageRecallTest {

    private val width = 256
    private val height = 256
    private val statusBar = OverlayBounds(0f, 0f, width.toFloat(), 24f)
    private val button = OverlayBounds(216f, 216f, 248f, 248f)
    private val movedButton = OverlayBounds(8f, 216f, 40f, 248f)
    private val settings = PageSettings(
        translationModel = "qwen2.5-1.5b",
        detectionModel = "bubble-detector",
        ocrModel = "manga-ocr",
        generation = GenerationParams(),
        runtime = RuntimeLimits(threads = 4, contextTokens = 2048),
        detectionThreshold = 0.25f,
        fontScale = 1.0f
    )

    private fun gray(level: Int): Int = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    /** Page [number] is textured art with a bright square at its own place, clear of the status bar and button. */
    private fun page(number: Int, change: (x: Int, y: Int, level: Int) -> Int = { _, _, level -> level }): IntArray =
        IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val inMark = x / 32 == number % 8 && y / 32 == 1 + number / 8
            change(x, y, if (inMark) 255 else (x * 7 + y * 13) % 200)
        }.map(::gray).toIntArray()

    private fun inside(region: OverlayBounds, x: Int, y: Int) =
        x >= region.left && x < region.right && y >= region.top && y < region.bottom

    private fun fingerprint(pixels: IntArray, ignored: List<OverlayBounds> = listOf(statusBar, button)) =
        PageFingerprint.of(pixels, width, height, ignored)

    private fun result(label: String, bubbles: List<TranslatedBubble> = listOf(answered(0, label))) = PipelineResult(
        typesetBubbles = emptyList(),
        translationResult = TranslationResult(bubbles, label, 0L),
        pageWidth = width,
        pageHeight = height,
        totalTimeMs = 0L
    )

    private fun answered(id: Int, text: String) = TranslatedBubble(id, "こんにちは", text, answered = true)

    private fun unanswered(id: Int) = TranslatedBubble(id, "やっちょっと…!", "やっちょっと…!", answered = false)

    @Test
    fun `a page captured again with the same settings is recalled`() {
        val recall = PageRecall()
        recall.remember(fingerprint(page(0)), settings, result("page 0"))

        assertEquals(result("page 0"), recall.recall(fingerprint(page(0)), settings))
    }

    @Test
    fun `a page slightly brighter everywhere is still recalled`() {
        val recall = PageRecall()
        recall.remember(fingerprint(page(0)), settings, result("page 0"))

        val brighter = page(0) { _, _, level -> (level + 2).coerceAtMost(255) }

        assertEquals(result("page 0"), recall.recall(fingerprint(brighter), settings))
    }

    @Test
    fun `a page with one bubble changed is not recalled`() {
        val recall = PageRecall()
        recall.remember(fingerprint(page(0)), settings, result("page 0"))

        val changedBubble = page(0) { x, y, level -> if (x in 96 until 128 && y in 160 until 176) 255 - level else level }

        assertNull(recall.recall(fingerprint(changedBubble), settings))
    }

    @Test
    fun `a different page is not recalled`() {
        val recall = PageRecall()
        recall.remember(fingerprint(page(0)), settings, result("page 0"))

        assertNull(recall.recall(fingerprint(page(1)), settings))
    }

    @Test
    fun `the status bar and the floating button do not decide a match`() {
        val recall = PageRecall()
        recall.remember(fingerprint(page(0)), settings, result("page 0"))

        val newClockAndButton = page(0) { x, y, level ->
            if (inside(statusBar, x, y) || inside(button, x, y)) 255 - level else level
        }

        assertEquals(result("page 0"), recall.recall(fingerprint(newClockAndButton), settings))

        val nothingIgnored = PageRecall()
        nothingIgnored.remember(fingerprint(page(0), ignored = emptyList()), settings, result("page 0"))
        assertNull(nothingIgnored.recall(fingerprint(newClockAndButton, ignored = emptyList()), settings))
    }

    @Test
    fun `a floating button moved between captures does not decide a match`() {
        val recall = PageRecall()
        val buttonHere = page(0) { x, y, level -> if (inside(button, x, y)) 255 else level }
        val buttonMoved = page(0) { x, y, level -> if (inside(movedButton, x, y)) 255 else level }
        recall.remember(fingerprint(buttonHere, listOf(statusBar, button)), settings, result("page 0"))

        assertEquals(result("page 0"), recall.recall(fingerprint(buttonMoved, listOf(statusBar, movedButton)), settings))
    }

    @Test
    fun `changing any setting that shapes the page makes it a fresh translation`() {
        val recall = PageRecall()
        recall.remember(fingerprint(page(0)), settings, result("page 0"))

        val changed = listOf(
            settings.copy(translationModel = "cat-translate-1.4b"),
            settings.copy(detectionModel = "other-detector"),
            settings.copy(ocrModel = "other-ocr"),
            settings.copy(generation = GenerationParams(temperature = 0.7f)),
            settings.copy(runtime = RuntimeLimits(threads = 2, contextTokens = 2048)),
            settings.copy(runtime = RuntimeLimits(threads = 4, contextTokens = 4096)),
            settings.copy(detectionThreshold = 0.3f),
            settings.copy(fontScale = 1.2f)
        )

        for (setting in changed) assertNull("$setting", recall.recall(fingerprint(page(0)), setting))
        assertEquals(result("page 0"), recall.recall(fingerprint(page(0)), settings))
    }

    @Test
    fun `the least recently used page is dropped past thirty pages`() {
        val recall = PageRecall()
        for (number in 0 until 30) recall.remember(fingerprint(page(number)), settings, result("page $number"))

        recall.recall(fingerprint(page(0)), settings)
        recall.remember(fingerprint(page(30)), settings, result("page 30"))

        assertNull(recall.recall(fingerprint(page(1)), settings))
        for (number in listOf(0) + (2..30)) {
            assertEquals(result("page $number"), recall.recall(fingerprint(page(number)), settings))
        }
    }

    @Test
    fun `a page with a few bubbles the model could not answer is still recalled`() {
        val recall = PageRecall()
        val partial = result("page 0", listOf(answered(0, "Hello"), unanswered(1)))
        recall.remember(fingerprint(page(0)), settings, partial)

        assertEquals(partial, recall.recall(fingerprint(page(0)), settings))
    }

    @Test
    fun `a page the model did not finish or answered nowhere is not recalled, so capturing it again retries it`() {
        val recall = PageRecall()
        val timedOut = result("page 0").copy(
            translationResult = TranslationResult(listOf(answered(0, "Hello")), "[0] Hello", 0L, outcome = TranslationOutcome.TIMEOUT)
        )
        recall.remember(fingerprint(page(0)), settings, timedOut)
        recall.remember(fingerprint(page(1)), settings, result("page 1", listOf(unanswered(0), unanswered(1))))

        assertNull(recall.recall(fingerprint(page(0)), settings))
        assertNull(recall.recall(fingerprint(page(1)), settings))
    }

    @Test
    fun `clearing forgets every page`() {
        val recall = PageRecall()
        recall.remember(fingerprint(page(0)), settings, result("page 0"))
        recall.remember(fingerprint(page(1)), settings, result("page 1"))

        recall.clear()

        assertNull(recall.recall(fingerprint(page(0)), settings))
        assertNull(recall.recall(fingerprint(page(1)), settings))
    }
}

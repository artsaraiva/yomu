package com.yomu.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.yomu.core.PageTranslation
import com.yomu.pipeline.bubble.Bubble
import com.yomu.pipeline.bubble.BubbleDetector
import com.yomu.pipeline.context.ContextAssembler
import com.yomu.pipeline.ocr.OcrEngine
import com.yomu.pipeline.ocr.OcrResult
import com.yomu.pipeline.translation.FakeTranslationSlot
import com.yomu.pipeline.translation.TranslationEngine
import com.yomu.pipeline.typesetting.Typesetter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranslationPipelineTest {

    private val statusBar = RectF(0f, 0f, 400f, 40f)
    private val clock = Bubble(1, RectF(10f, 5f, 60f, 35f), 0.9f)
    private val speech = Bubble(2, RectF(100f, 100f, 180f, 300f), 0.9f)

    // The fake OCR sees only a crop, so each box above has its own width to be told apart by.
    private val readsByCropWidth = mapOf(50 to "12:00", 80 to "こんにちは")
    private val reads = mutableListOf<String>()
    private val slot = FakeTranslationSlot(PageTranslation(mapOf(2 to "Hello"), "", 1L))

    private fun pipeline(vararg boxes: Bubble) = TranslationPipeline(
        detector(boxes.toList()),
        ocr(),
        ContextAssembler(),
        TranslationEngine { slot },
        Typesetter()
    )

    private fun detector(boxes: List<Bubble>): BubbleDetector = Mockito.mock(BubbleDetector::class.java) { invocation ->
        when (invocation.method.name) {
            "isModelLoaded" -> true
            "detect" -> boxes
            else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }

    private fun ocr(): OcrEngine = Mockito.mock(OcrEngine::class.java) { invocation ->
        when (invocation.method.name) {
            "isModelLoaded" -> true
            "extractText" -> {
                val text = readsByCropWidth.getValue(invocation.getArgument<Bitmap>(0).width)
                reads += text
                OcrResult(text, 1f, floatArrayOf())
            }
            else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }

    private fun page(): Bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
    }

    @Test
    fun `a box centered in an ignored region never reaches OCR`() = runTest {
        pipeline(clock, speech).processPage(page(), ignoredRegions = listOf(statusBar))

        assertEquals(listOf("こんにちは"), reads)
    }
}

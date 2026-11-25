package com.yomu.app.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import com.yomu.pipeline.bubble.BalloonShape
import com.yomu.pipeline.typesetting.TypesetBubble
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TranslationRenderOverlayTest {
    /** Verifies the first OCR preview survives window creation and changes to the typeset background on arrival. */
    @Test
    fun firstPreviewCanBeTypesetOnSingleBubblePage() {
        val windowManager = mock(WindowManager::class.java)
        val overlay = TranslationRenderOverlay(RuntimeEnvironment.getApplication(), windowManager) { null }
        val bounds = OverlayBounds(10f, 10f, 110f, 70f)
        overlay.showOcrBubbles(listOf(OverlayBubbleState(1, bounds, "OCR")), 120, 80)

        val viewCaptor = ArgumentCaptor.forClass(View::class.java)
        verify(windowManager).addView(viewCaptor.capture(), any(WindowManager.LayoutParams::class.java))
        val view = viewCaptor.value.apply {
            measure(
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 120, 80)
        }
        assertEquals(Color.argb(0xAA, 0, 0, 0), pixelAt(view, 20, 40))

        overlay.showTypesetBubble(
            TypesetBubble(1, "Hello", "OCR", 12f, listOf("Hello"), floatArrayOf(10f, 10f, 110f, 70f))
        )
        assertEquals(0xF0FFFFFF.toInt(), pixelAt(view, 20, 40))
    }

    /** A shaped bubble paints its oval plus the glyph box, leaving the oval's corners to the page. */
    @Test
    fun shapedBubblePaintsItsOvalAndGlyphBox() {
        val windowManager = mock(WindowManager::class.java)
        val overlay = TranslationRenderOverlay(RuntimeEnvironment.getApplication(), windowManager) { null }
        overlay.showOcrBubbles(listOf(OverlayBubbleState(1, OverlayBounds(10f, 10f, 110f, 70f), "OCR")), 120, 80)
        val viewCaptor = ArgumentCaptor.forClass(View::class.java)
        verify(windowManager).addView(viewCaptor.capture(), any(WindowManager.LayoutParams::class.java))
        val view = viewCaptor.value.apply {
            measure(
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 120, 80)
        }
        val oval = floatArrayOf(10f, 10f, 110f, 70f)

        overlay.showTypesetBubble(
            TypesetBubble(
                1, "Hello", "OCR", 12f, listOf("Hello"), oval,
                shape = BalloonShape(oval, ellipse = true),
                coverBox = floatArrayOf(55f, 4f, 65f, 76f)
            )
        )

        assertEquals("oval centre", 0xF0FFFFFF.toInt(), pixelAt(view, 30, 40))
        assertEquals("oval corner stays page", Color.TRANSPARENT, pixelAt(view, 13, 13))
        assertEquals("glyph box past the oval", 0xF0FFFFFF.toInt(), pixelAt(view, 60, 6))
    }

    /** Draws the overlay into a fixed-size bitmap and returns the pixel used to check its bubble background. */
    private fun pixelAt(view: View, x: Int, y: Int): Int {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap.getPixel(x, y)
    }
}

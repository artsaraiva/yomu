package com.yomu.app.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import com.yomu.core.TargetLanguage
import com.yomu.pipeline.bubble.BalloonShape
import com.yomu.pipeline.typesetting.TypesetBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.atLeast
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
        val overlay = TranslationRenderOverlay(RuntimeEnvironment.getApplication(), windowManager, { _, _ -> true }) { null }
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
        val overlay = TranslationRenderOverlay(RuntimeEnvironment.getApplication(), windowManager, { _, _ -> true }) { null }
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

    @Test
    fun `a tapped bubble's card names the page's language`() {
        val page = finishedPage(mock(WindowManager::class.java))

        tap(page, 60f, 40f)

        assertTrue("Spanish" in texts(page))
    }

    @Test
    fun `the page takes the keyboard only while a name is being added`() {
        val windowManager = mock(WindowManager::class.java)
        val page = finishedPage(windowManager)
        tap(page, 60f, 40f)
        assertFalse(focusable(page, windowManager))

        clickLabelled(page, "Add a name from this bubble")
        assertTrue(focusable(page, windowManager))

        views(page).filterIsInstance<EditText>()[1].setText("Luffy")
        clickLabelled(page, "Save")
        assertEquals(listOf("OCR" to "Luffy"), saved)
        assertFalse(focusable(page, windowManager))
    }

    @Test
    fun `back closes the card and gives the keyboard back`() {
        val windowManager = mock(WindowManager::class.java)
        val page = finishedPage(windowManager)
        tap(page, 60f, 40f)
        clickLabelled(page, "Add a name from this bubble")

        page.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        page.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertEquals(0, page.childCount)
        assertFalse(focusable(page, windowManager))
    }

    private val saved = mutableListOf<Pair<String, String>>()

    /** A drawn Spanish page of one bubble, laid out at the size it was captured. */
    private fun finishedPage(windowManager: WindowManager): ViewGroup {
        val overlay = TranslationRenderOverlay(RuntimeEnvironment.getApplication(), windowManager, { term, rendering -> saved.add(term to rendering) }) { null }
        overlay.show(
            listOf(TypesetBubble(1, "Hola", "OCR", 12f, listOf("Hola"), floatArrayOf(10f, 10f, 110f, 70f))), 120, 80, TargetLanguage.SPANISH
        )
        val viewCaptor = ArgumentCaptor.forClass(View::class.java)
        verify(windowManager).addView(viewCaptor.capture(), any(WindowManager.LayoutParams::class.java))
        return (viewCaptor.value as ViewGroup).apply {
            measure(
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 120, 80)
            pixelAt(this, 0, 0)
        }
    }

    /** Whether the window's latest layout params let it take input focus. */
    private fun focusable(page: View, windowManager: WindowManager): Boolean {
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windowManager, atLeast(0)).updateViewLayout(eq(page), params.capture())
        val latest = params.allValues.lastOrNull() ?: return false
        return latest.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
    }

    private fun tap(page: View, x: Float, y: Float) {
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            page.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun views(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun texts(page: View): List<String> = views(page).filterIsInstance<TextView>().map { it.text.toString() }

    private fun clickLabelled(page: View, label: String) {
        val text = views(page).first { it is TextView && it.text.toString() == label }
        generateSequence(text) { it.parent as? View }.first { it.isClickable }.performClick()
    }

    /** Draws the overlay into a fixed-size bitmap and returns the pixel used to check its bubble background. */
    private fun pixelAt(view: View, x: Int, y: Int): Int {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap.getPixel(x, y)
    }
}

package com.yomu.app.overlay

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FloatingButtonOverlayTest {
    private val windowManager = mock(WindowManager::class.java)
    private val overlay = FloatingButtonOverlay(RuntimeEnvironment.getApplication(), windowManager)

    /** Windows of one type stack in the order they are added, so the layer must go in first to leave the button usable. */
    @Test
    fun touchLayerCoversTheScreenUnderTheButton() {
        val button = overlay.show(0, 0, onTap = {}, onDragEnd = { _, _ -> })

        val (layer, layerParams) = addedWindows()[0]
        assertSame(button, addedWindows()[1].first)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, layerParams.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, layerParams.height)
        assertEquals(View.GONE, layer.visibility)
    }

    @Test
    fun blockedTouchLayerSwallowsTouchesAndReportsTheirRelease() {
        overlay.show(0, 0, onTap = {}, onDragEnd = { _, _ -> })
        val layer = addedWindows()[0].first
        var releases = 0

        overlay.blockTouchesBelow { releases++ }
        assertEquals(View.VISIBLE, layer.visibility)
        assertTrue(layer.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN)))
        assertTrue(layer.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE)))
        assertEquals(0, releases)
        assertTrue(layer.dispatchTouchEvent(touch(MotionEvent.ACTION_UP)))
        assertEquals(1, releases)

        overlay.unblockTouchesBelow()
        assertEquals(View.GONE, layer.visibility)
    }

    private fun addedWindows(): List<Pair<View, WindowManager.LayoutParams>> {
        val views = ArgumentCaptor.forClass(View::class.java)
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windowManager, times(2)).addView(views.capture(), params.capture())
        return views.allValues.zip(params.allValues)
    }

    private fun touch(action: Int): MotionEvent =
        SystemClock.uptimeMillis().let { MotionEvent.obtain(it, it, action, 200f, 300f, 0) }
}

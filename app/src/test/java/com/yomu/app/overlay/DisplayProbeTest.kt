package com.yomu.app.overlay

import android.util.Size
import android.view.View
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The phone is 1080 × 2400, held upright. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xxhdpi")
class DisplayProbeTest {
    private val windowManager = mock(WindowManager::class.java)
    private val reported = mutableListOf<Size>()
    private val probe = DisplayProbe(RuntimeEnvironment.getApplication(), windowManager) { reported += it }

    /** On Android 12+ touches reach the reader app through another app's overlay only while it is see-through. */
    @Test
    fun windowCoversTheDisplayWithoutTakingTouchesOrFocus() {
        probe.attach()

        val params = addedWindows().single().second
        val passThrough = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
        assertEquals(0f, params.alpha)
        assertEquals(passThrough, params.flags and passThrough)
    }

    @Test
    fun eachLayoutReportsTheDisplaysSize() {
        probe.attach()
        val window = addedWindows().single().first

        window.layout(0, 0, 1080, 2400)
        window.layout(0, 0, 2400, 1080)

        assertEquals(listOf(Size(1080, 2400), Size(2400, 1080)), reported)
        assertEquals(Size(2400, 1080), probe.size)
    }

    /** Before Android 11 an overlay window stops short of a visible navigation bar, so the display itself is asked. */
    @Test
    @Config(sdk = [28])
    @Suppress("DEPRECATION")
    fun beforeAndroid11ALayoutReportsTheDisplaysRealSize() {
        val display = RuntimeEnvironment.getApplication().getSystemService(WindowManager::class.java).defaultDisplay
        `when`(windowManager.defaultDisplay).thenReturn(display)
        probe.attach()

        addedWindows().single().first.layout(0, 0, 1080, 2274)

        assertEquals(listOf(Size(1080, 2400)), reported)
    }

    @Test
    fun emptyLayoutIsNotASize() {
        probe.attach()

        addedWindows().single().first.layout(0, 0, 0, 0)

        assertEquals(emptyList<Size>(), reported)
        assertNull(probe.size)
    }

    /** A turn after reading stops must do nothing, even one already queued for the removed window. */
    @Test
    fun detachedWindowIsRemovedAndReportsNothing() {
        probe.attach()
        val window = addedWindows().single().first
        window.layout(0, 0, 1080, 2400)

        probe.detach()
        window.layout(0, 0, 2400, 1080)

        verify(windowManager).removeView(window)
        assertNull(probe.size)
        assertEquals(listOf(Size(1080, 2400)), reported)
    }

    @Test
    fun attachingAgainReplacesTheWindow() {
        probe.attach()
        val first = addedWindows().single().first

        probe.attach()

        verify(windowManager).removeView(first)
        assertEquals(2, addedWindows().size)
    }

    private fun addedWindows(): List<Pair<View, WindowManager.LayoutParams>> {
        val views = ArgumentCaptor.forClass(View::class.java)
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windowManager, atLeastOnce()).addView(views.capture(), params.capture())
        return views.allValues.zip(params.allValues)
    }
}

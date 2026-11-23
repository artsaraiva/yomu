package com.yomu.app.overlay

import android.graphics.PixelFormat
import android.util.Size
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.same
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** A 1080 × 2400 phone whose window manager is a mock that keeps each window the probe adds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class DisplayProbeTest {
    private val portrait = Size(1080, 2400)
    private val landscape = Size(2400, 1080)
    private val windowManager = mock(WindowManager::class.java)
    private val added = mutableListOf<AddedWindow>()
    private val reports = mutableListOf<Size>()
    private val probe = DisplayProbe(RuntimeEnvironment.getApplication(), windowManager) { reports += it }

    init {
        doAnswer { call ->
            added += AddedWindow(call.getArgument(0), call.getArgument(1))
            null
        }.`when`(windowManager).addView(any(), any())
    }

    @Test
    fun `attach adds one overlay window that fills the screen`() {
        probe.attach()

        val params = added.single().params
        assertEquals(LayoutParams.MATCH_PARENT, params.width)
        assertEquals(LayoutParams.MATCH_PARENT, params.height)
        assertEquals(LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(PixelFormat.TRANSLUCENT, params.format)
    }

    /** Android 12+ drops a touch that crosses overlays which are too opaque together, and the reader app needs every tap. */
    @Test
    fun `taps and key focus go past the window to the app underneath`() {
        probe.attach()

        val params = added.single().params
        assertEquals(0f, params.alpha)
        assertHasFlags(params, LayoutParams.FLAG_NOT_TOUCHABLE or LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    @Test
    fun `the window is placed in screen coordinates and may reach past the usual window limits`() {
        probe.attach()

        assertHasFlags(added.single().params, LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    /** From Android 11 the window's own size is reported, which is the display's only while nothing insets it. */
    @Test
    fun `system bars and the camera cutout leave the window uninset`() {
        probe.attach()

        val params = added.single().params
        assertEquals(0, params.fitInsetsTypes)
        assertEquals(LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, params.layoutInDisplayCutoutMode)
    }

    @Test
    fun `size is unknown until the window is first laid out`() {
        probe.attach()

        assertNull(probe.size)
        assertEquals(emptyList<Size>(), reports)
    }

    @Test
    fun `turning the screen reports the landscape size after the portrait one`() {
        probe.attach()
        val window = added.single()

        window.layOut(1080, 2400)
        window.layOut(2400, 1080)

        assertEquals(listOf(portrait, landscape), reports)
        assertEquals(landscape, probe.size)
    }

    @Test
    fun `a second layout at an unchanged size is not reported again`() {
        probe.attach()
        val window = added.single()

        window.layOut(1080, 2400)
        window.layOut(1080, 2400)

        assertEquals(listOf(portrait), reports)
    }

    /** Before Android 11 an overlay window ends where a shown navigation bar starts, 126 px short of the bottom here. */
    @Test
    @Config(sdk = [28])
    fun `before Android 11 the full display is reported rather than the shorter window`() {
        letTheMockAnswerWithThePhonesDisplay()
        probe.attach()

        added.single().layOut(1080, 2274)

        assertEquals(listOf(portrait), reports)
        assertEquals(portrait, probe.size)
    }

    @Test
    fun `a window with no width or no height reports nothing`() {
        probe.attach()
        val window = added.single()

        window.layOut(0, 2400)
        window.layOut(1080, 0)
        window.layOut(0, 0)

        assertEquals(emptyList<Size>(), reports)
        assertNull(probe.size)
    }

    @Test
    fun `detach takes the window away and forgets the size`() {
        probe.attach()
        val window = added.single()
        window.layOut(1080, 2400)

        probe.detach()

        verify(windowManager).removeView(window.view)
        assertNull(probe.size)
    }

    /** The system can still lay out a window it queued before reading stopped; a turn after stopping must change nothing. */
    @Test
    fun `a layout that reaches a detached window is ignored`() {
        probe.attach()
        val window = added.single()
        window.layOut(1080, 2400)
        probe.detach()

        window.layOut(2400, 1080)

        assertEquals(listOf(portrait), reports)
        assertNull(probe.size)
    }

    @Test
    fun `detach before any attach leaves the window manager alone`() {
        probe.detach()

        verifyNoInteractions(windowManager)
        assertNull(probe.size)
    }

    @Test
    fun `a second detach has nothing left to remove`() {
        probe.attach()
        probe.detach()

        probe.detach()

        verify(windowManager, times(1)).removeView(any())
    }

    @Test
    fun `attaching again takes the old window away before adding a new one`() {
        probe.attach()
        val old = added.single()

        probe.attach()

        val new = added.last()
        assertEquals(2, added.size)
        val order = inOrder(windowManager)
        order.verify(windowManager).removeView(old.view)
        order.verify(windowManager).addView(same(new.view), any())
    }

    @Test
    fun `after attaching again only the new window's layouts count`() {
        probe.attach()
        val old = added.single()
        probe.attach()
        val new = added.last()

        old.layOut(2400, 1080)
        new.layOut(1080, 2400)

        assertEquals(listOf(portrait), reports)
        assertEquals(portrait, probe.size)
    }

    private fun assertHasFlags(params: LayoutParams, flags: Int) = assertEquals(flags, params.flags and flags)

    @Suppress("DEPRECATION")
    private fun letTheMockAnswerWithThePhonesDisplay() {
        val phone: Display = RuntimeEnvironment.getApplication().getSystemService(WindowManager::class.java).defaultDisplay
        `when`(windowManager.defaultDisplay).thenReturn(phone)
    }

    /** A window as the probe handed it to the window manager. */
    private class AddedWindow(val view: View, val params: LayoutParams) {
        /** Plays the system: lays the window out at [width] × [height] from the screen's top-left corner. */
        fun layOut(width: Int, height: Int) = view.layout(0, 0, width, height)
    }
}

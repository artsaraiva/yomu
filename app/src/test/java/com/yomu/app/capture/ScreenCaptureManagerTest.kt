package com.yomu.app.capture

import android.graphics.Bitmap
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Looper
import android.util.Size
import android.view.Surface
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** The phone is 1080 × 2400 at 480 dpi, held upright when reading starts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xxhdpi", shadows = [ShadowCapturedImage::class])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScreenCaptureManagerTest {
    private val manager = ScreenCaptureManager(RuntimeEnvironment.getApplication())
    private val projection = mock(MediaProjection::class.java)
    private val mirror = mock(VirtualDisplay::class.java)
    /** The surface the mirror draws the screen into: like the system's, the last one it was given. */
    private var mirrorSurface: Surface? = null
    /** The surface the system fitted the screen into, which it does when the mirror is made or resized. */
    private var fittedSurface: Surface? = null
    private var stops = 0

    init {
        doAnswer { mirrorSurface = it.getArgument(5); fittedSurface = mirrorSurface; mirror }.`when`(projection)
            .createVirtualDisplay(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any())
        doAnswer { mirrorSurface = it.getArgument(0); null }.`when`(mirror).setSurface(any())
        doAnswer { fittedSurface = mirrorSurface; null }.`when`(mirror).resize(anyInt(), anyInt(), anyInt())
    }

    /** Locking the screen ends sharing with no tap involved, so the stop must be reported as it happens (#353). */
    @Test
    fun projectionEndedBySystemIsReportedAndCaptureGoesInactive() {
        manager.startProjection(projection) { stops++ }

        registeredCallback().onStop()

        assertEquals(1, stops)
        assertFalse(manager.isProjectionActive)
    }

    @Test
    fun projectionStoppedByYomuIsNotReportedAsEndedBySystem() {
        manager.startProjection(projection) { stops++ }

        manager.stopProjection()
        registeredCallback().onStop()

        assertEquals(0, stops)
    }

    @Test
    fun replacedProjectionEndingLeavesTheNewOneCapturing() {
        val replacement = mock(MediaProjection::class.java)
        manager.startProjection(projection) { stops++ }
        manager.startProjection(replacement) { stops++ }

        registeredCallback().onStop()

        assertEquals(0, stops)
        assertTrue(manager.isProjectionActive)
    }

    /** A page turned to landscape must not be squeezed into the portrait frame reading started with. */
    @Test
    fun turnedDisplayIsCapturedAtItsNewSize() {
        manager.startProjection(projection) {}

        manager.resizeTo(Size(2400, 1080))

        assertEquals(Size(2400, 1080), nextCaptureSize())
        verify(mirror).resize(2400, 1080, 480)
    }

    /** Fitted into a surface it no longer draws into, the turned screen lands shrunk in a corner of the frame. */
    @Test
    fun turnedDisplayIsFittedIntoTheSurfaceItIsCapturedFrom() {
        manager.startProjection(projection) {}

        manager.resizeTo(Size(2400, 1080))

        assertSame(mirrorSurface, fittedSurface)
    }

    /** A page captured or drawn before the turn is the old shape, so whoever asked has to drop it. */
    @Test
    fun resizingTheCaptureIsReported() {
        manager.startProjection(projection) {}

        assertTrue(manager.resizeTo(Size(2400, 1080)))
    }

    /** The display's first report is the size reading started at; its reader holds the frame a still screen is captured from (#402). */
    @Test
    fun displayOfTheSizeAlreadyCapturedChangesNothing() {
        manager.startProjection(projection) {}
        drawFrame()

        val resized = manager.resizeTo(Size(1080, 2400))

        assertFalse(resized)
        assertEquals(Size(1080, 2400), stillScreenCaptureSize())
    }

    /** The probe window can still report a layout queued before reading stopped. */
    @Test
    fun displayChangeAfterSharingStoppedIsNotAResize() {
        manager.startProjection(projection) {}
        manager.stopProjection()

        assertFalse(manager.resizeTo(Size(2400, 1080)))
    }

    private fun registeredCallback(): MediaProjection.Callback {
        val callback = ArgumentCaptor.forClass(MediaProjection.Callback::class.java)
        verify(projection).registerCallback(callback.capture(), any())
        return callback.value
    }

    /** Plays the system: draws one frame into the surface the mirror draws the screen into. */
    private fun drawFrame() {
        mirrorSurface?.let { it.unlockCanvasAndPost(it.lockCanvas(null)) }
    }

    /** Asks for a capture, has the mirror draw one frame after the request, and returns the captured frame's size. */
    private fun nextCaptureSize(): Size? {
        var captured: Bitmap? = null
        manager.captureScreen { captured = it }
        shadowOf(Looper.getMainLooper()).idle()
        drawFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_WAIT_MS))
        return captured?.let { Size(it.width, it.height) }
    }

    /** Asks for a capture while nothing new is drawn, so the frame already waiting in the reader is the one captured. */
    private fun stillScreenCaptureSize(): Size? {
        var captured: Bitmap? = null
        manager.captureScreen { captured = it }
        shadowOf(Looper.getMainLooper()).idle()
        // The capture times its wait for a new frame by the wall clock, which the paused looper doesn't move.
        Thread.sleep(STILL_SCREEN_WAIT_MS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_WAIT_MS))
        return captured?.let { Size(it.width, it.height) }
    }

    private companion object {
        /** Longer than the capture's poll interval, so the frame drawn after the request is picked up. */
        const val FRAME_WAIT_MS = 100L

        /** Longer than the capture waits for a new frame before it takes the one already in the reader. */
        const val STILL_SCREEN_WAIT_MS = 350L
    }
}

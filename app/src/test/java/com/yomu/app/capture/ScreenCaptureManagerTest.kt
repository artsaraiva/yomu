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
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The phone is 1080 × 2400 at 480 dpi, held upright when reading starts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xxhdpi", shadows = [ShadowCapturedImage::class])
class ScreenCaptureManagerTest {
    private val manager = ScreenCaptureManager(RuntimeEnvironment.getApplication())
    private val projection = mock(MediaProjection::class.java)
    private val mirror = mock(VirtualDisplay::class.java)
    /** The surface the mirror draws the screen into: like the system's, the last one it was given. */
    private var mirrorSurface: Surface? = null
    private var stops = 0

    init {
        doAnswer { mirrorSurface = it.getArgument(5); mirror }.`when`(projection)
            .createVirtualDisplay(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any())
        doAnswer { mirrorSurface = it.getArgument(0); null }.`when`(mirror).setSurface(any())
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

        manager.matchDisplay(Size(2400, 1080))

        assertEquals(Size(2400, 1080), nextCaptureSize())
        verify(mirror).resize(2400, 1080, 480)
    }

    /** The display's first report is the size reading started at; its reader holds the frame a still screen is captured from (#402). */
    @Test
    fun displayOfTheSizeAlreadyCapturedLeavesTheMirrorAlone() {
        manager.startProjection(projection) {}

        manager.matchDisplay(Size(1080, 2400))

        verifyNoInteractions(mirror)
    }

    private fun registeredCallback(): MediaProjection.Callback {
        val callback = ArgumentCaptor.forClass(MediaProjection.Callback::class.java)
        verify(projection).registerCallback(callback.capture(), any())
        return callback.value
    }

    /** Asks for a capture, has the mirror draw one frame after the request, and returns the captured frame's size. */
    private fun nextCaptureSize(): Size? {
        var captured: Bitmap? = null
        manager.captureScreen { captured = it }
        shadowOf(Looper.getMainLooper()).idle()
        mirrorSurface?.let { it.unlockCanvasAndPost(it.lockCanvas(null)) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_WAIT_MS))
        return captured?.let { Size(it.width, it.height) }
    }

    private companion object {
        /** Longer than the capture's poll interval, so the frame drawn after the request is picked up. */
        const val FRAME_WAIT_MS = 100L
    }
}

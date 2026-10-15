package com.yomu.app.capture

import android.media.projection.MediaProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenCaptureManagerTest {
    private val manager = ScreenCaptureManager(RuntimeEnvironment.getApplication())
    private val projection = mock(MediaProjection::class.java)
    private var stops = 0

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

    private fun registeredCallback(): MediaProjection.Callback {
        val callback = ArgumentCaptor.forClass(MediaProjection.Callback::class.java)
        verify(projection).registerCallback(callback.capture(), any())
        return callback.value
    }
}

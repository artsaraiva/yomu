package com.yomu.app.overlay

import android.view.View
import android.view.WindowManager
import org.junit.Assert.assertEquals
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
class QuickSettingsPopupTest {
    private val windowManager = mock(WindowManager::class.java)
    private var releaseOutside: (() -> Unit)? = null
    private var unblocks = 0
    private val popup = QuickSettingsPopup(
        context = RuntimeEnvironment.getApplication(),
        windowManager = windowManager,
        modelName = { "Model" },
        onFontSizeChanged = {},
        onThresholdChanged = {},
        hasLastPage = { false },
        onReportRequested = {},
        onOpenAppRequested = {},
        onStopRequested = {},
        blockTouchesBelow = { releaseOutside = it },
        unblockTouchesBelow = { unblocks++ }
    )

    /** An outside tap must close the popup through the touch layer, not by watching it pass through to the app (#352). */
    @Test
    fun releaseOutsideClosesThePopupAndUnblocksTouches() {
        popup.show(0, 0)
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windowManager).addView(any(View::class.java), params.capture())
        assertEquals(0, params.value.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)

        checkNotNull(releaseOutside).invoke()
        assertEquals(1, unblocks)

        popup.show(0, 0)
        verify(windowManager, times(2)).addView(any(View::class.java), any(WindowManager.LayoutParams::class.java))
    }
}

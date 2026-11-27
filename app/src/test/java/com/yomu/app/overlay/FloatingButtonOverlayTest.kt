package com.yomu.app.overlay

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
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
        assertSame(button, addedWindows()[2].first)
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

    /** Quick settings must close on touch-down, a frame or more before a tap starts a capture that could still show it. */
    @Test
    fun pressReportsTouchDownBeforeTheGestureEnds() {
        var presses = 0
        var taps = 0
        val button = overlay.show(0, 0, onTap = { taps++ }, onDragEnd = { _, _ -> }, onPress = { presses++ })

        button.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN))
        assertEquals(1, presses)
        assertEquals(0, taps)
    }

    /** The capsule goes in under the button, so the button stays the stop target, and lets every touch through (#552). */
    @Test
    fun progressCapsuleWaitsHiddenUnderTheButtonAndTakesNoTouches() {
        overlay.show(0, 0, onTap = {}, onDragEnd = { _, _ -> })

        val (capsule, capsuleParams) = addedWindows()[1]
        assertEquals(View.GONE, capsule.visibility)
        assertTrue(capsuleParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
    }

    @Test
    fun progressShowsBesideTheButtonAndIsAnnouncedPolitely() {
        overlay.show(0, 200, onTap = {}, onDragEnd = { _, _ -> })
        val (capsule, capsuleParams) = addedWindows()[1]
        val buttonParams = addedWindows()[2].second

        overlay.showProgress("Reading Japanese text", step = 2)

        assertEquals(View.VISIBLE, capsule.visibility)
        assertEquals("Reading Japanese text. Step 2 of 3", capsule.contentDescription)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, capsule.accessibilityLiveRegion)
        assertEquals(buttonParams.x, capsuleParams.x)
        assertEquals(buttonParams.y, capsuleParams.y)
        assertTrue(capsuleParams.width > buttonParams.width)
    }

    /** 320 px wide here: a button at the right edge has its room on the left, so the text must keep clear of the right end. */
    @Test
    fun capsuleGrowsLeftFromAButtonAtTheRightEdge() {
        overlay.show(1000, 200, onTap = {}, onDragEnd = { _, _ -> })
        val (capsule, capsuleParams) = addedWindows()[1]
        val buttonParams = addedWindows()[2].second

        overlay.showProgress("Translating", step = 3)

        assertEquals(buttonParams.x + buttonParams.width, capsuleParams.x + capsuleParams.width)
        assertTrue(capsuleParams.x < buttonParams.x)
        assertTrue((capsule as ProgressCapsuleView).buttonAtRight)
    }

    /** A right-to-left locale counts both windows' x from the right edge, so the same numbers put the button on the left end. */
    @Test
    @Config(sdk = [28], qualifiers = "ar")
    fun capsuleReadsClearOfTheButtonInARightToLeftLocale() {
        overlay.show(1000, 200, onTap = {}, onDragEnd = { _, _ -> })
        val capsule = addedWindows()[1].first as ProgressCapsuleView

        overlay.showProgress("Translating", step = 3)

        assertFalse(capsule.buttonAtRight)
    }

    @Test
    fun aRepeatedStageIsNotLaidOutAgain() {
        overlay.show(0, 200, onTap = {}, onDragEnd = { _, _ -> })
        val capsule = addedWindows()[1].first

        overlay.showProgress("Reading Japanese text", step = 2)
        overlay.showProgress("Reading Japanese text", step = 2)
        overlay.showProgress("Translating", step = 3)

        verify(windowManager, times(2)).updateViewLayout(org.mockito.Mockito.eq(capsule), org.mockito.Mockito.any())
    }

    @Test
    fun hiddenProgressLeavesNoCapsule() {
        overlay.show(0, 200, onTap = {}, onDragEnd = { _, _ -> })
        overlay.showProgress("Translating", step = 3)

        overlay.hideProgress()

        assertEquals(View.GONE, addedWindows()[1].first.visibility)
    }

    @Test
    fun capsuleFollowsTheButtonWhileItIsDragged() {
        val button = overlay.show(0, 200, onTap = {}, onDragEnd = { _, _ -> })
        val capsuleParams = addedWindows()[1].second
        overlay.showProgress("Translating", step = 3)

        button.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN))
        button.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE, y = 400f))

        assertEquals(addedWindows()[2].second.y, capsuleParams.y)
        assertEquals(300, capsuleParams.y)
    }

    @Test
    fun removingTheButtonRemovesItsCapsule() {
        overlay.show(0, 200, onTap = {}, onDragEnd = { _, _ -> })
        val capsule = addedWindows()[1].first

        overlay.remove()

        verify(windowManager).removeView(capsule)
    }

    private fun addedWindows(): List<Pair<View, WindowManager.LayoutParams>> {
        val views = ArgumentCaptor.forClass(View::class.java)
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windowManager, times(3)).addView(views.capture(), params.capture())
        return views.allValues.zip(params.allValues)
    }

    private fun touch(action: Int, y: Float = 300f): MotionEvent =
        SystemClock.uptimeMillis().let { MotionEvent.obtain(it, it, action, 200f, y, 0) }
}

package com.yomu.pipeline.bubble

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spike #471: a leaked balloon falls back to its ellipse instead of a glyph counter. */
@RunWith(RobolectricTestRunner::class)
class BalloonShapeTest {

    @Test
    fun `an open balloon whose only enclosed region is a glyph counter falls back to the balloon ellipse`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val page = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        page.eraseColor(white)
        // A 口 glyph: a hollow 20 px square in the middle of an open (all-white) balloon area.
        for (i in 90..110) { page.setPixel(i, 90, black); page.setPixel(i, 110, black); page.setPixel(90, i, black); page.setPixel(110, i, black) }

        val plans = renderPlans(page, mapOf(1 to floatArrayOf(88f, 88f, 112f, 112f)), mapOf(1 to floatArrayOf(40f, 40f, 160f, 160f)))

        assertEquals(ShapeSource.BALLOON_ELLIPSE, plans.getValue(1).shape?.source)
    }
}

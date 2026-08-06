package com.yomu.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloseZoneOverlayTest {

    @Test
    fun `zone bounds are centered horizontally and above bottom margin`() {
        val bounds = CloseZoneGeometry.zoneBounds(
            screenWidth = 1080,
            screenHeight = 2400,
            density = 2f
        )

        assertEquals(540, bounds.centerX)
        assertEquals(40 * 2, bounds.radiusPx)
        val expectedCenterY = 2400 - (16 * 2) - (40 * 2)
        assertEquals(expectedCenterY, bounds.centerY)
    }

    @Test
    fun `button center is offset by half size`() {
        val center = CloseZoneGeometry.buttonCenter(
            buttonX = 100,
            buttonY = 200,
            buttonSize = 56
        )

        assertEquals(128, center.first)
        assertEquals(228, center.second)
    }

    @Test
    fun `button center inside zone returns true`() {
        val bounds = CloseZoneGeometry.zoneBounds(
            screenWidth = 1080,
            screenHeight = 2400,
            density = 2f
        )

        val inside = CloseZoneGeometry.isWithinZone(
            buttonCenterX = bounds.centerX,
            buttonCenterY = bounds.centerY,
            zoneBounds = bounds
        )

        assertTrue(inside)
    }

    @Test
    fun `button center outside zone returns false`() {
        val bounds = CloseZoneGeometry.zoneBounds(
            screenWidth = 1080,
            screenHeight = 2400,
            density = 2f
        )

        val outside = CloseZoneGeometry.isWithinZone(
            buttonCenterX = bounds.centerX,
            buttonCenterY = bounds.centerY - bounds.radiusPx - 1,
            zoneBounds = bounds
        )

        assertFalse(outside)
    }

    @Test
    fun `button center exactly at zone radius returns true`() {
        val bounds = CloseZoneGeometry.zoneBounds(
            screenWidth = 1080,
            screenHeight = 2400,
            density = 2f
        )

        val atEdge = CloseZoneGeometry.isWithinZone(
            buttonCenterX = bounds.centerX,
            buttonCenterY = bounds.centerY - bounds.radiusPx,
            zoneBounds = bounds
        )

        assertTrue(atEdge)
    }

    @Test
    fun `radius helpers scale with density`() {
        assertEquals(80, CloseZoneGeometry.baseRadiusPx(2f))
        assertEquals(96, CloseZoneGeometry.snapRadiusPx(2f))
        assertEquals(112, CloseZoneGeometry.maxViewRadiusPx(2f))
        assertEquals(400, CloseZoneGeometry.activationRadiusPx(2f))
    }
}

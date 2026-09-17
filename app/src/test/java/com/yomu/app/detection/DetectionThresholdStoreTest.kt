package com.yomu.app.detection

import com.yomu.app.translation.MapSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionThresholdStoreTest {

    private val prefs = MapSharedPreferences()
    private val store = DetectionThresholdStore(prefs)

    @Test
    fun `nothing stored loads the default`() {
        assertEquals(0.25f, store.load())
    }

    @Test
    fun `every step from 0_10 to 0_60 saves and loads back`() {
        (0..10).map { 0.10f + it * 0.05f }.forEach { value ->
            assertTrue("$value", store.save(value))
            assertEquals(value, DetectionThresholdStore(prefs).load())
        }
    }

    @Test
    fun `out-of-range, off-step and non-finite values are refused and storage is untouched`() {
        store.save(0.4f)
        val before = prefs.all

        listOf(0.05f, 0.65f, Math.nextDown(0.10f), Math.nextUp(0.60f), 0.33f, Float.NaN).forEach {
            assertFalse("$it", store.save(it))
        }

        assertEquals(before, prefs.all)
    }

    @Test
    fun `a stored out-of-range or wrongly typed value loads as the default`() {
        prefs.values[DetectionThresholdStore.KEY] = 0.9f
        assertEquals(0.25f, store.load())

        prefs.values[DetectionThresholdStore.KEY] = "corrupt"
        assertEquals(0.25f, store.load())
    }

    @Test
    fun `snap lands on an accepted step inside the range`() {
        assertEquals(0.25f, DetectionThresholdStore.snap(0.26f))
        assertEquals(0.10f, DetectionThresholdStore.snap(0.01f))
        assertEquals(0.60f, DetectionThresholdStore.snap(0.9f))
        (0..100).map { 0.10f + it * 0.005f }.forEach {
            assertTrue("$it", DetectionThresholdStore.accepts(DetectionThresholdStore.snap(it)))
        }
    }

    @Test
    fun `slider positions round-trip to accepted values across the range`() {
        assertEquals(DetectionThresholdStore.MIN, DetectionThresholdStore.atStep(0))
        assertEquals(DetectionThresholdStore.MAX, DetectionThresholdStore.atStep(DetectionThresholdStore.STEPS))
        assertEquals(DetectionThresholdStore.MIN, DetectionThresholdStore.atStep(-1))
        assertEquals(DetectionThresholdStore.MAX, DetectionThresholdStore.atStep(DetectionThresholdStore.STEPS + 1))
        (0..DetectionThresholdStore.STEPS).forEach { step ->
            val value = DetectionThresholdStore.atStep(step)
            assertTrue("$value", DetectionThresholdStore.accepts(value))
            assertEquals(step, DetectionThresholdStore.stepOf(value))
        }
        assertEquals(DetectionThresholdStore.DEFAULT, DetectionThresholdStore.atStep(DetectionThresholdStore.stepOf(DetectionThresholdStore.DEFAULT)))
    }

    @Test
    fun `reset restores the default`() {
        store.save(0.5f)

        store.reset()

        assertEquals(0.25f, store.load())
    }
}

package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelStatus
import com.yomu.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStateTest {
    @Test
    fun `reading is on only when ready and service is running`() {
        assertEquals(ReadingStatus.NotReady, resolveReadingStatus(false, false))
        assertEquals(ReadingStatus.NotReady, resolveReadingStatus(false, true))
        assertEquals(ReadingStatus.Off, resolveReadingStatus(true, false))
        assertEquals(ReadingStatus.On, resolveReadingStatus(true, true))
    }

    @Test
    fun `readiness requires both reading models and overlay permission`() {
        val ready = mapOf(
            Constants.BUBBLE_DETECTION_MODEL_ID to ModelStatus.READY,
            Constants.MANGA_OCR_MODEL_ID to ModelStatus.READY
        )
        assertEquals(Readiness.Ready, resolveReadiness(ready, true))
        assertEquals(Readiness.NeedsPermission, resolveReadiness(ready, false))
        assertEquals(Readiness.NeedsModels, resolveReadiness(emptyMap(), true))
        assertEquals(Readiness.NeedsBoth, resolveReadiness(emptyMap(), false))
        assertEquals(Readiness.NeedsModels, resolveReadiness(ready - Constants.MANGA_OCR_MODEL_ID, true))
        ModelStatus.entries.filter { it != ModelStatus.READY }.forEach { status ->
            val incomplete = ready + (Constants.BUBBLE_DETECTION_MODEL_ID to status)
            assertEquals(Readiness.NeedsModels, resolveReadiness(incomplete, true))
            assertEquals(Readiness.NeedsBoth, resolveReadiness(incomplete, false))
        }
        assertEquals(Readiness.Ready, resolveReadiness(ready + ("optional" to ModelStatus.ERROR), true))
    }
}

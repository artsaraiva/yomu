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
    fun `readiness requires both reading models, the translator and overlay permission`() {
        val translator = Constants.QWEN25_15B_MODEL_ID
        val ready = mapOf(
            Constants.BUBBLE_DETECTION_MODEL_ID to ModelStatus.READY,
            Constants.MANGA_OCR_MODEL_ID to ModelStatus.READY,
            translator to ModelStatus.READY
        )
        assertEquals(Readiness.Ready, resolveReadiness(ready, translator, true))
        assertEquals(Readiness.NeedsPermission, resolveReadiness(ready, translator, false))
        assertEquals(Readiness.NeedsModels, resolveReadiness(emptyMap(), translator, true))
        assertEquals(Readiness.NeedsBoth, resolveReadiness(emptyMap(), translator, false))
        assertEquals(Readiness.NeedsModels, resolveReadiness(ready - Constants.MANGA_OCR_MODEL_ID, translator, true))
        ModelStatus.entries.filter { it != ModelStatus.READY }.forEach { status ->
            val incomplete = ready + (Constants.BUBBLE_DETECTION_MODEL_ID to status)
            assertEquals(Readiness.NeedsModels, resolveReadiness(incomplete, translator, true))
            assertEquals(Readiness.NeedsBoth, resolveReadiness(incomplete, translator, false))
        }
        assertEquals(Readiness.Ready, resolveReadiness(ready + ("optional" to ModelStatus.ERROR), translator, true))
    }

    @Test
    fun `a missing translator means models are needed`() {
        val translator = Constants.QWEN25_15B_MODEL_ID
        val readers = mapOf(
            Constants.BUBBLE_DETECTION_MODEL_ID to ModelStatus.READY,
            Constants.MANGA_OCR_MODEL_ID to ModelStatus.READY
        )
        assertEquals(Readiness.NeedsModels, resolveReadiness(readers, translator, true))
        assertEquals(Readiness.NeedsModels, resolveReadiness(readers + (translator to ModelStatus.DOWNLOADING), translator, true))
        // A different deliverable being ready does not stand in for the selected one.
        assertEquals(
            Readiness.NeedsModels,
            resolveReadiness(readers + (Constants.CAT_TRANSLATION_MODEL_ID to ModelStatus.READY), translator, true)
        )
    }
}

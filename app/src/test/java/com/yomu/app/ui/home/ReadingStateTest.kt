package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelStatus
import com.yomu.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStateTest {
    private val detector = "selected_detector"
    private val reader = "selected_reader"
    private val translator = Constants.QWEN25_15B_MODEL_ID

    private fun readiness(models: Map<String, ModelStatus>, permission: Boolean) =
        resolveReadiness(models, detector, reader, translator, permission, phoneTranslates = true)

    @Test
    fun `reading is on only when ready and service is running`() {
        assertEquals(ReadingStatus.NotReady, resolveReadingStatus(false, false))
        assertEquals(ReadingStatus.NotReady, resolveReadingStatus(false, true))
        assertEquals(ReadingStatus.Off, resolveReadingStatus(true, false))
        assertEquals(ReadingStatus.On, resolveReadingStatus(true, true))
    }

    @Test
    fun `readiness requires the selected detector, reader, translator and overlay permission`() {
        val ready = mapOf(
            detector to ModelStatus.READY,
            reader to ModelStatus.READY,
            translator to ModelStatus.READY
        )
        assertEquals(Readiness.Ready, readiness(ready, true))
        assertEquals(Readiness.NeedsPermission, readiness(ready, false))
        assertEquals(Readiness.NeedsModels, readiness(emptyMap(), true))
        assertEquals(Readiness.NeedsBoth, readiness(emptyMap(), false))
        assertEquals(Readiness.NeedsModels, readiness(ready - reader, true))
        ModelStatus.entries.filter { it != ModelStatus.READY }.forEach { status ->
            val incomplete = ready + (detector to status)
            assertEquals(Readiness.NeedsModels, readiness(incomplete, true))
            assertEquals(Readiness.NeedsBoth, readiness(incomplete, false))
        }
    }

    @Test
    fun `unselected models in any status do not affect readiness`() {
        val ready = mapOf(
            detector to ModelStatus.READY,
            reader to ModelStatus.READY,
            translator to ModelStatus.READY
        )
        val unselected = listOf(
            Constants.BUBBLE_DETECTION_MODEL_ID,
            Constants.HAYAI_OCR_MODEL_ID,
            Constants.CAT_TRANSLATION_MODEL_ID
        )
        ModelStatus.entries.forEach { status ->
            val others = unselected.associateWith { status }
            assertEquals(Readiness.Ready, readiness(ready + others, true))
            assertEquals(Readiness.NeedsModels, readiness(ready - detector + others, true))
        }
    }

    @Test
    fun `a slot cleared by deleting its model needs models`() {
        val ready = mapOf(
            detector to ModelStatus.READY,
            reader to ModelStatus.READY,
            translator to ModelStatus.READY
        )
        assertEquals(Readiness.NeedsModels, resolveReadiness(ready, detector, reader, null, true, phoneTranslates = true))
        assertEquals(Readiness.NeedsModels, resolveReadiness(ready, null, reader, translator, true, phoneTranslates = true))
        assertEquals(Readiness.NeedsBoth, resolveReadiness(ready, detector, null, translator, false, phoneTranslates = true))
    }

    @Test
    fun `a missing translator means models are needed`() {
        val readers = mapOf(detector to ModelStatus.READY, reader to ModelStatus.READY)
        assertEquals(Readiness.NeedsModels, readiness(readers, true))
        assertEquals(Readiness.NeedsModels, readiness(readers + (translator to ModelStatus.DOWNLOADING), true))
    }

    @Test
    fun `no translation model is needed while another translator takes the pages`() {
        val readers = mapOf(detector to ModelStatus.READY, reader to ModelStatus.READY)

        assertEquals(Readiness.Ready, resolveReadiness(readers, detector, reader, null, true, phoneTranslates = false))
        assertEquals(Readiness.NeedsPermission, resolveReadiness(readers, detector, reader, translator, false, phoneTranslates = false))
        assertEquals(Readiness.NeedsModels, resolveReadiness(readers, detector, reader, null, true, phoneTranslates = true))
    }

    @Test
    fun `detection and OCR stay required whichever translator takes the pages`() {
        val detectorOnly = mapOf(detector to ModelStatus.READY)

        assertEquals(Readiness.NeedsModels, resolveReadiness(detectorOnly, detector, reader, null, true, phoneTranslates = false))
        assertEquals(Readiness.NeedsBoth, resolveReadiness(detectorOnly, detector, null, null, false, phoneTranslates = false))
    }

    @Test
    fun `readiness says which half is in place`() {
        assertEquals(listOf(true, false, true, false), Readiness.entries.map { it.modelsReady })
        assertEquals(listOf(true, true, false, false), Readiness.entries.map { it.hasOverlayPermission })
    }
}

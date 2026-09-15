package com.yomu.app.service

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.MapSharedPreferences
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.core.Constants
import com.yomu.ml.LlamaTranslationBridge
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class ModelSlotSelectionTest {

    private val prefs = MapSharedPreferences()
    private val selection = ModelSlotSelection(
        TranslationModelSelection(Mockito.mock(LlamaTranslationBridge::class.java), prefs, File("models")),
        ReadingModelSelection(prefs)
    )
    private val eightGb = 8L * 1024 * 1024 * 1024

    @Test
    fun `picking a model that isn't downloaded records a pending choice and leaves the stored selection unchanged`() = runTest {
        assertTrue(selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.AVAILABLE, eightGb))

        assertEquals(Constants.CAT_TRANSLATION_MODEL_ID, selection.pendingId(ModelType.LLM))
        assertEquals(LlmModelCatalog.DEFAULT.id, selection.selectedId(ModelType.LLM))
        assertNull(prefs.values[Constants.PREF_LLM_MODEL])
    }

    @Test
    fun `a pending choice waits while its download runs`() = runTest {
        selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.AVAILABLE, eightGb)

        selection.commitPending(mapOf(Constants.CAT_TRANSLATION_MODEL_ID to ModelStatus.DOWNLOADING))

        assertEquals(Constants.CAT_TRANSLATION_MODEL_ID, selection.pendingId(ModelType.LLM))
        assertEquals(LlmModelCatalog.DEFAULT.id, selection.selectedId(ModelType.LLM))
    }

    @Test
    fun `reaching READY commits the pending choice`() = runTest {
        selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.AVAILABLE, eightGb)

        selection.commitPending(mapOf(Constants.CAT_TRANSLATION_MODEL_ID to ModelStatus.READY))

        assertNull(selection.pendingId(ModelType.LLM))
        assertEquals(Constants.CAT_TRANSLATION_MODEL_ID, selection.selectedId(ModelType.LLM))
        assertEquals(Constants.CAT_TRANSLATION_MODEL_ID, prefs.values[Constants.PREF_LLM_MODEL])
    }

    @Test
    fun `picking a READY model commits immediately and replaces a pending choice`() = runTest {
        selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.DOWNLOADING, eightGb)

        assertTrue(selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_14B_MODEL_ID, ModelStatus.READY, eightGb))
        selection.commitPending(mapOf(Constants.CAT_TRANSLATION_MODEL_ID to ModelStatus.READY))

        assertNull(selection.pendingId(ModelType.LLM))
        assertEquals(Constants.CAT_TRANSLATION_14B_MODEL_ID, selection.selectedId(ModelType.LLM))
    }

    @Test
    fun `a deliverable outside the fit budget can't be selected`() = runTest {
        val oneGb = 1L * 1024 * 1024 * 1024

        assertFalse(selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_14B_MODEL_ID, ModelStatus.READY, oneGb))
        assertFalse(selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_14B_MODEL_ID, ModelStatus.AVAILABLE, oneGb))

        assertNull(selection.pendingId(ModelType.LLM))
        assertEquals(LlmModelCatalog.DEFAULT.id, selection.selectedId(ModelType.LLM))
    }

    @Test
    fun `a model from another slot or outside the registry can't be selected`() = runTest {
        assertFalse(selection.pick(ModelType.OCR, Constants.BUBBLE_DETECTION_MODEL_ID, ModelStatus.READY, eightGb))
        assertFalse(selection.pick(ModelType.LLM, "retired-model", ModelStatus.AVAILABLE, eightGb))

        assertNull(selection.pendingId(ModelType.OCR))
        assertNull(selection.pendingId(ModelType.LLM))
    }

    @Test
    fun `pending choices are kept per slot`() = runTest {
        selection.pick(ModelType.OCR, Constants.MANGA_OCR_MODEL_ID, ModelStatus.AVAILABLE, eightGb)

        assertEquals(Constants.MANGA_OCR_MODEL_ID, selection.pendingId(ModelType.OCR))
        assertNull(selection.pendingId(ModelType.DETECTION))
        assertNull(selection.pendingId(ModelType.LLM))

        selection.commitPending(mapOf(Constants.MANGA_OCR_MODEL_ID to ModelStatus.READY))

        assertNull(selection.pendingId(ModelType.OCR))
        assertEquals(Constants.MANGA_OCR_MODEL_ID, prefs.values[Constants.PREF_OCR_MODEL])
    }

    @Test
    fun `dropping a pending choice leaves the stored selection`() = runTest {
        selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.DOWNLOADING, eightGb)

        selection.dropPending(Constants.CAT_TRANSLATION_MODEL_ID)
        selection.commitPending(mapOf(Constants.CAT_TRANSLATION_MODEL_ID to ModelStatus.READY))

        assertNull(selection.pendingId(ModelType.LLM))
        assertEquals(LlmModelCatalog.DEFAULT.id, selection.selectedId(ModelType.LLM))
    }

    @Test
    fun `every slot lists its curated deliverables with a size and licence`() {
        assertEquals(LlmModelCatalog.ALL.map { it.id }, selection.deliverables(ModelType.LLM).map { it.id })
        for (type in listOf(ModelType.DETECTION, ModelType.OCR)) {
            assertEquals(
                ModelManager.REGISTRY.filter { it.type == type }.map { it.id },
                selection.deliverables(type).map { it.id }
            )
        }
        for (deliverable in ModelType.entries.flatMap { selection.deliverables(it) }) {
            assertTrue(deliverable.name, deliverable.licence.isNotBlank())
            assertTrue(deliverable.name, deliverable.sizeBytes > 0)
        }
    }

    @Test
    fun `a deliverable's size counts its additional files`() {
        val ocr = selection.deliverables(ModelType.OCR).single { it.id == Constants.MANGA_OCR_MODEL_ID }
        val entity = ModelManager.REGISTRY.single { it.id == Constants.MANGA_OCR_MODEL_ID }

        assertEquals(entity.fileSize + ModelManager.additionalFiles(entity.id).sumOf { it.size }, ocr.sizeBytes)
    }

    @Test
    fun `fit is judged by the translation budget and an unknown memory size fits`() {
        val oneGb = 1L * 1024 * 1024 * 1024

        assertFalse(ModelSlotSelection.fits(Constants.CAT_TRANSLATION_14B_MODEL_ID, oneGb))
        assertTrue(ModelSlotSelection.fits(Constants.CAT_TRANSLATION_14B_MODEL_ID, 0L))
        assertTrue(ModelSlotSelection.fits(LlmModelCatalog.DEFAULT.id, oneGb))
        assertTrue(ModelSlotSelection.fits(Constants.MANGA_OCR_MODEL_ID, oneGb))
    }
}

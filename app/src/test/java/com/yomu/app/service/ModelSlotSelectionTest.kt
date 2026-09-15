package com.yomu.app.service

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.MapSharedPreferences
import com.yomu.app.translation.ResourceLimit
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.core.Constants
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.pipeline.TranslationPipeline
import com.yomu.pipeline.bubble.BubbleDetector
import com.yomu.pipeline.context.ContextAssembler
import com.yomu.pipeline.ocr.OcrEngine
import com.yomu.pipeline.translation.TranslationEngine
import com.yomu.pipeline.typesetting.Typesetter
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
    private val llama = Mockito.mock(LlamaTranslationBridge::class.java)
    private val detector = Mockito.mock(BubbleDetector::class.java)
    private val ocr = Mockito.mock(OcrEngine::class.java)
    private val modelManager = Mockito.mock(ModelManager::class.java)
    private val selection = newSelection()

    private fun newSelection(): ModelSlotSelection {
        val translation = TranslationModelSelection(llama, prefs, File("models"))
        val pipeline = TranslationPipeline(
            detector,
            ocr,
            ContextAssembler(),
            TranslationEngine(translation::current, translation::close),
            Mockito.mock(Typesetter::class.java)
        )
        return ModelSlotSelection(translation, ReadingModelSelection(prefs), pipeline, modelManager)
    }
    private val oneGb = 1L * 1024 * 1024 * 1024
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
        selection.delete(Constants.MANGA_OCR_MODEL_ID)
        selection.pick(ModelType.OCR, Constants.MANGA_OCR_MODEL_ID, ModelStatus.AVAILABLE, eightGb)
        selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.AVAILABLE, eightGb)

        assertEquals(Constants.MANGA_OCR_MODEL_ID, selection.pendingId(ModelType.OCR))
        assertNull(selection.pendingId(ModelType.DETECTION))
        assertEquals(Constants.CAT_TRANSLATION_MODEL_ID, selection.pendingId(ModelType.LLM))

        selection.commitPending(mapOf(Constants.MANGA_OCR_MODEL_ID to ModelStatus.READY))

        assertNull(selection.pendingId(ModelType.OCR))
        assertEquals(Constants.CAT_TRANSLATION_MODEL_ID, selection.pendingId(ModelType.LLM))
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
    fun `re-picking the stored selection drops a pending switch`() = runTest {
        selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.DOWNLOADING, eightGb)

        assertTrue(selection.pick(ModelType.LLM, LlmModelCatalog.DEFAULT.id, ModelStatus.AVAILABLE, eightGb))

        assertNull(selection.pendingId(ModelType.LLM))
        assertEquals(LlmModelCatalog.DEFAULT.id, selection.selectedId(ModelType.LLM))
    }

    @Test
    fun `deleting the selected translation model clears its slot and unloads it`() = runTest {
        selection.delete(LlmModelCatalog.DEFAULT.id)

        assertNull(selection.selectedId(ModelType.LLM))
        Mockito.verify(llama).close()
        assertTrue(deleted(LlmModelCatalog.DEFAULT.id))
    }

    @Test
    fun `deleting a selected reading model clears only its slot and releases it`() = runTest {
        selection.delete(Constants.MANGA_OCR_MODEL_ID)

        assertNull(selection.selectedId(ModelType.OCR))
        assertEquals(Constants.BUBBLE_DETECTION_MODEL_ID, selection.selectedId(ModelType.DETECTION))
        assertEquals(LlmModelCatalog.DEFAULT.id, selection.selectedId(ModelType.LLM))
        Mockito.verify(ocr).release()
        Mockito.verifyNoInteractions(detector, llama)
        assertTrue(deleted(Constants.MANGA_OCR_MODEL_ID))
    }

    @Test
    fun `deleting a model that isn't selected leaves every slot and unloads nothing`() = runTest {
        selection.delete(Constants.CAT_TRANSLATION_MODEL_ID)

        assertEquals(LlmModelCatalog.DEFAULT.id, selection.selectedId(ModelType.LLM))
        Mockito.verifyNoInteractions(detector, ocr, llama)
        assertTrue(deleted(Constants.CAT_TRANSLATION_MODEL_ID))
    }

    @Test
    fun `a cleared slot stays empty until a model is picked`() = runTest {
        selection.delete(LlmModelCatalog.DEFAULT.id)

        assertNull(newSelection().selectedId(ModelType.LLM))

        selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.READY, eightGb)

        assertEquals(Constants.CAT_TRANSLATION_MODEL_ID, selection.selectedId(ModelType.LLM))
    }

    @Test
    fun `slots are ready only when every selected model is READY`() = runTest {
        val statuses = listOf(Constants.BUBBLE_DETECTION_MODEL_ID, Constants.MANGA_OCR_MODEL_ID, LlmModelCatalog.DEFAULT.id)
            .associateWith { ModelStatus.READY }

        assertTrue(selection.ready(statuses))
        assertFalse(selection.ready(statuses + (Constants.MANGA_OCR_MODEL_ID to ModelStatus.DOWNLOADING)))

        selection.delete(Constants.MANGA_OCR_MODEL_ID)

        assertFalse(selection.ready(statuses))
    }

    @Test
    fun `the download size counts each slot's chosen model that isn't downloaded`() = runTest {
        selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.AVAILABLE, eightGb)
        val size = { id: String -> ModelType.entries.flatMap(selection::deliverables).single { it.id == id }.sizeBytes }

        assertEquals(
            listOf(Constants.BUBBLE_DETECTION_MODEL_ID, Constants.MANGA_OCR_MODEL_ID, Constants.CAT_TRANSLATION_MODEL_ID),
            ModelType.entries.map(selection::chosenId)
        )
        assertEquals(
            size(Constants.MANGA_OCR_MODEL_ID) + size(Constants.CAT_TRANSLATION_MODEL_ID),
            selection.downloadBytes(mapOf(Constants.BUBBLE_DETECTION_MODEL_ID to ModelStatus.READY))
        )
    }

    @Test
    fun `a cleared slot is set up with its curated default`() = runTest {
        selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.READY, eightGb)
        selection.delete(Constants.CAT_TRANSLATION_MODEL_ID)

        assertNull(selection.selectedId(ModelType.LLM))
        assertEquals(LlmModelCatalog.DEFAULT.id, selection.chosenId(ModelType.LLM))
        assertEquals(
            LlmModelCatalog.DEFAULT.sizeBytes,
            selection.downloadBytes(mapOf(Constants.BUBBLE_DETECTION_MODEL_ID to ModelStatus.READY, Constants.MANGA_OCR_MODEL_ID to ModelStatus.READY))
        )
    }

    private fun deleted(id: String): Boolean =
        Mockito.mockingDetails(modelManager).invocations.any { it.method.name == "deleteModel" && it.arguments[0] == id }

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
        val share = LlmModelCatalog.DEFAULT_RAM_PERCENT
        assertFalse(ModelSlotSelection.fits(Constants.CAT_TRANSLATION_14B_MODEL_ID, oneGb, share))
        assertTrue(ModelSlotSelection.fits(Constants.CAT_TRANSLATION_14B_MODEL_ID, 0L, share))
        assertTrue(ModelSlotSelection.fits(LlmModelCatalog.DEFAULT.id, oneGb, share))
        assertTrue(ModelSlotSelection.fits(Constants.MANGA_OCR_MODEL_ID, oneGb, share))
    }

    @Test
    fun `the reader's stored RAM share gates what can be picked`() = runTest {
        val fourGb = 4L * 1024 * 1024 * 1024
        prefs.values[ResourceLimit.RAM_PERCENT.key] = 30

        assertFalse(selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_14B_MODEL_ID, ModelStatus.READY, fourGb))

        prefs.values[ResourceLimit.RAM_PERCENT.key] = 60
        assertTrue(selection.pick(ModelType.LLM, Constants.CAT_TRANSLATION_14B_MODEL_ID, ModelStatus.READY, fourGb))
    }
}

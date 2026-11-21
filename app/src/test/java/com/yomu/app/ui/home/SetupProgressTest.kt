package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.ModelManager
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.translation.FitBudget
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.core.Constants
import com.yomu.core.RuntimeLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupProgressTest {
    private val mb = 1024L * 1024
    private val detector = SlotDeliverable(Constants.BUBBLE_DETECTION_MODEL_ID, "RT-DETR v4-s", 40 * mb, "Apache-2.0")
    private val reader = SlotDeliverable(Constants.MANGA_OCR_MODEL_ID, "manga-ocr", 100 * mb, "Apache-2.0")
    private val translator = SlotDeliverable(LlmModelCatalog.DEFAULT.id, "Qwen2.5 1.5B Instruct", 900 * mb, "Apache-2.0", multilingual = true)
    private val huge = SlotDeliverable(Constants.QWEN35_9B_MODEL_ID, "Qwen3.5 9B", 5_400 * mb, "Apache-2.0", multilingual = true)
    private val eightGb = FitBudget(8L * 1024 * mb, LlmModelCatalog.DEFAULT_FIT_BUDGET_PERCENT, RuntimeLimits.DEFAULT_CONTEXT_TOKENS)

    private val fresh = HomeUiState(
        deliverables = mapOf(
            ModelType.DETECTION to listOf(detector),
            ModelType.OCR to listOf(reader),
            ModelType.LLM to listOf(translator, huge)
        ),
        chosenIds = mapOf(ModelType.DETECTION to detector.id, ModelType.OCR to reader.id, ModelType.LLM to translator.id),
        fitBudget = eightGb
    )

    private fun model(id: String, type: ModelType, status: ModelStatus) =
        ModelEntity(id, id, type, "$id.bin", 1L, "", "", status, version = "1", isRequired = true)

    private fun HomeUiState.states() = setupRows().map { it.state }

    @Test
    fun `setup lists each slot's chosen model in download order`() {
        val rows = fresh.setupRows()

        assertEquals(listOf("Detection", "OCR", "Translation"), rows.map { it.slot })
        assertEquals(listOf(detector, reader, translator), rows.map { it.deliverable })
    }

    @Test
    fun `models not on the phone yet are missing`() {
        assertEquals(List(3) { SetupModelState.Missing }, fresh.states())
    }

    @Test
    fun `a downloaded model is done`() {
        val state = fresh.copy(models = listOf(model(detector.id, ModelType.DETECTION, ModelStatus.READY)))

        assertEquals(listOf(SetupModelState.Done, SetupModelState.Missing, SetupModelState.Missing), state.states())
    }

    @Test
    fun `the model downloading now shows how many bytes have arrived`() {
        val state = fresh.copy(
            setupStarted = true,
            setupDownload = ModelManager.DownloadProgress(reader.id, 25 * mb, 100 * mb, 25)
        )

        assertEquals(SetupModelState.Downloading(25 * mb, 100 * mb), state.states()[1])
    }

    @Test
    fun `only the model whose download failed is marked failed`() {
        val state = fresh.copy(
            setupStarted = true,
            setupError = true,
            setupFailedId = reader.id,
            models = listOf(model(detector.id, ModelType.DETECTION, ModelStatus.READY))
        )

        assertEquals(listOf(SetupModelState.Done, SetupModelState.Failed, SetupModelState.Missing), state.states())
    }

    @Test
    fun `a chosen model outside the fit budget is too big, even when downloaded`() {
        val state = fresh.copy(
            chosenIds = fresh.chosenIds + (ModelType.LLM to huge.id),
            fitBudget = eightGb.copy(totalMemBytes = 4L * 1024 * mb),
            models = listOf(model(huge.id, ModelType.LLM, ModelStatus.READY))
        )

        assertEquals(SetupModelState.TooBig, state.states()[2])
    }

    @Test
    fun `a row's status shows the size, then waiting, bytes, done, failed or too big`() {
        val row = fresh.setupRows()[2]

        assertEquals("900.00 MB", row.statusText(running = false))
        assertEquals("Waiting · 900.00 MB", row.statusText(running = true))
        assertEquals("225.00 MB of 900.00 MB", row.copy(state = SetupModelState.Downloading(225 * mb, 900 * mb)).statusText(running = true))
        assertEquals("Done ✓", row.copy(state = SetupModelState.Done).statusText(running = true))
        assertEquals("Failed", row.copy(state = SetupModelState.Failed).statusText(running = false))
        assertEquals("Too big for this phone.", row.copy(state = SetupModelState.TooBig).statusText(running = false))
    }

    @Test
    fun `a row's announcement changes when its state changes, not as bytes arrive`() {
        val row = fresh.setupRows()[2]
        val early = row.copy(state = SetupModelState.Downloading(1 * mb, 900 * mb))
        val late = row.copy(state = SetupModelState.Downloading(800 * mb, 900 * mb))

        assertEquals("Translation, Qwen2.5 1.5B Instruct, not downloaded", row.announcement())
        assertEquals("Translation, Qwen2.5 1.5B Instruct, downloading", early.announcement())
        assertEquals(early.announcement(), late.announcement())
        assertEquals("Translation, Qwen2.5 1.5B Instruct, downloaded", row.copy(state = SetupModelState.Done).announcement())
    }

    @Test
    fun `the first step names what is left to download until every model is done`() {
        val missing = fresh.copy(setupDownloadBytes = 900 * mb)
        val done = fresh.copy(models = listOf(
            model(detector.id, ModelType.DETECTION, ModelStatus.READY),
            model(reader.id, ModelType.OCR, ModelStatus.READY),
            model(translator.id, ModelType.LLM, ModelStatus.READY)
        ))

        assertEquals(SetupStep("Download models · 900.00 MB", done = false), missing.setupSteps()[0])
        assertEquals(SetupStep("Models downloaded", done = true), done.setupSteps()[0])
    }

    @Test
    fun `the second step is done once Yomu may draw over other apps`() {
        val label = "Allow display over other apps"

        assertEquals(SetupStep(label, done = false), fresh.copy(readiness = Readiness.NeedsBoth).setupSteps()[1])
        assertEquals(SetupStep(label, done = false), fresh.copy(readiness = Readiness.NeedsPermission).setupSteps()[1])
        assertEquals(SetupStep(label, done = true), fresh.copy(readiness = Readiness.NeedsModels).setupSteps()[1])
        assertEquals(SetupStep(label, done = true), fresh.copy(readiness = Readiness.Ready).setupSteps()[1])
    }

    @Test
    fun `setup is running only between tapping Download and the last model finishing or failing`() {
        assertFalse(fresh.setupRunning)
        assertTrue(fresh.copy(setupStarted = true).setupRunning)
        assertFalse(fresh.copy(setupStarted = true, setupError = true).setupRunning)
        assertFalse(fresh.copy(setupStarted = true, setupDownloadsReady = true).setupRunning)
    }
}

package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.ModelManager
import com.yomu.app.service.PickerGroup
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.translation.FitBudget
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.Translator
import com.yomu.app.translation.TranslatorPick
import com.yomu.app.translation.TranslatorPickerEntry
import com.yomu.core.Constants
import com.yomu.core.RuntimeLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupProgressTest {
    private val mb = 1024L * 1024
    private val detector = SlotDeliverable(Constants.BUBBLE_DETECTION_MODEL_ID, "RT-DETR v4-s", 40 * mb, "Apache-2.0")
    private val reader = SlotDeliverable(Constants.HAYAI_OCR_MODEL_ID, "hayai", 100 * mb, "Apache-2.0")
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

    private val server = PickerGroup(
        Translator.NETWORK_ID, "Your server",
        listOf(TranslatorPickerEntry("Set up your server", null, checked = false) { TranslatorPick.Refused })
    )

    private fun model(id: String, type: ModelType, status: ModelStatus) =
        ModelEntity(id, id, type, "$id.bin", 1L, "", "", status, version = "1", isRequired = true)

    private fun HomeUiState.states() = setupRows().map { it.state }

    @Test
    fun `setup lists each slot's chosen model in download order`() {
        val rows = fresh.setupRows()

        assertEquals(listOf("Bubble finder", "Japanese reader", "Translator"), rows.map { it.slot })
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
    fun `a row shows its size, and the translator its model too`() {
        val rows = fresh.setupRows()

        assertEquals("40.00 MB", rows[0].detail())
        assertEquals("Qwen2.5 1.5B Instruct · 900.00 MB", rows[2].detail())
        assertEquals("Qwen2.5 1.5B Instruct · Failed", rows[2].copy(state = SetupModelState.Failed).detail())
        assertEquals("Qwen2.5 1.5B Instruct · Too big for this phone", rows[2].copy(state = SetupModelState.TooBig).detail())
    }

    @Test
    fun `the kit is one download, counting finished models whole and the running one by its bytes`() {
        val state = fresh.copy(
            setupStarted = true,
            models = listOf(model(detector.id, ModelType.DETECTION, ModelStatus.READY)),
            setupDownload = ModelManager.DownloadProgress(reader.id, 25 * mb, 100 * mb, 25)
        )
        val kit = state.setupKit()

        assertEquals(1_040 * mb, kit.totalBytes)
        assertEquals(65 * mb, kit.downloadedBytes)
        assertEquals(0.0625f, kit.fraction, 0.0001f)
        assertEquals("65.00 MB of 1.02 GB", kit.progressText())
        assertFalse(kit.downloaded)
    }

    @Test
    fun `a kit not started shows its whole size`() {
        assertEquals("1.02 GB", fresh.setupKit().progressText())
        assertEquals(0f, fresh.setupKit().fraction, 0f)
    }

    @Test
    fun `the kit is downloaded once every model in it is`() {
        val done = fresh.copy(models = listOf(
            model(detector.id, ModelType.DETECTION, ModelStatus.READY),
            model(reader.id, ModelType.OCR, ModelStatus.READY),
            model(translator.id, ModelType.LLM, ModelStatus.READY)
        ))

        assertTrue(done.setupKit().downloaded)
        assertEquals("Downloaded · 1.02 GB", done.setupKit().progressText())
    }

    @Test
    fun `the kit's announcement changes when the download starts and ends, not as bytes arrive`() {
        val early = fresh.copy(setupStarted = true, setupDownload = ModelManager.DownloadProgress(translator.id, 1 * mb, 900 * mb, 0))
        val late = fresh.copy(setupStarted = true, setupDownload = ModelManager.DownloadProgress(translator.id, 800 * mb, 900 * mb, 88))
        val done = fresh.copy(setupStarted = true, setupDownloadsReady = true, models = listOf(
            model(detector.id, ModelType.DETECTION, ModelStatus.READY),
            model(reader.id, ModelType.OCR, ModelStatus.READY),
            model(translator.id, ModelType.LLM, ModelStatus.READY)
        ))

        assertEquals("Reading kit, not downloaded", fresh.setupKit().announcement())
        assertEquals("Reading kit, downloading", early.setupKit().announcement())
        assertEquals(early.setupKit().announcement(), late.setupKit().announcement())
        assertEquals("Reading kit, downloaded", done.setupKit().announcement())
        assertEquals("Reading kit, download failed", fresh.copy(setupStarted = true, setupError = true).setupKit().announcement())
    }

    @Test
    fun `the kit names the model too big for this phone`() {
        val state = fresh.copy(
            chosenIds = fresh.chosenIds + (ModelType.LLM to huge.id),
            fitBudget = eightGb.copy(totalMemBytes = 4L * 1024 * mb)
        )

        assertEquals(huge, state.setupKit().tooBig?.deliverable)
        assertEquals(null, fresh.setupKit().tooBig)
    }

    @Test
    fun `setup is running only between tapping Download and the last model finishing or failing`() {
        assertFalse(fresh.setupRunning)
        assertTrue(fresh.copy(setupStarted = true).setupRunning)
        assertFalse(fresh.copy(setupStarted = true, setupError = true).setupRunning)
        assertFalse(fresh.copy(setupStarted = true, setupDownloadsReady = true).setupRunning)
    }

    @Test
    fun `setup that hands the pages to another translator lists only the reading models`() {
        val state = fresh.copy(translatorGroups = listOf(server), setupTranslatorId = server.translatorId)

        assertEquals(listOf("Bubble finder", "Japanese reader"), state.setupRows().map { it.slot })
        assertEquals("140.00 MB", state.setupKit().progressText())
    }

    @Test
    fun `a translator already taking the pages leaves the phone model out of setup`() {
        val state = fresh.copy(phoneTranslates = false)

        assertEquals(listOf("Bubble finder", "Japanese reader"), state.setupRows().map { it.slot })
    }

    @Test
    fun `setup offers each translator with picker entries in place of the phone model`() {
        val state = fresh.copy(translatorGroups = listOf(server))

        assertEquals(listOf(server), state.setupTranslatorOffers())
        assertEquals(server, state.copy(setupTranslatorId = server.translatorId).setupTranslatorGroup())
        assertTrue("nothing to offer once one is chosen", state.copy(setupTranslatorId = server.translatorId).setupTranslatorOffers().isEmpty())
    }
}

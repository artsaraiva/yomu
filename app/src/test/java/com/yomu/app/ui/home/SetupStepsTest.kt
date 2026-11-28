package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
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

/** Setup is one stepper (#547): the first step not done is open, the done ones collapse and the rest wait. */
class SetupStepsTest {
    private val mb = 1024L * 1024
    private val detector = SlotDeliverable(Constants.BUBBLE_DETECTION_MODEL_ID, "RT-DETR v4-s", 40 * mb, "Apache-2.0")
    private val reader = SlotDeliverable(Constants.HAYAI_OCR_MODEL_ID, "hayai", 100 * mb, "Apache-2.0")
    private val translator = SlotDeliverable(LlmModelCatalog.DEFAULT.id, "Qwen2.5 1.5B Instruct", 900 * mb, "Apache-2.0", multilingual = true)

    private val fresh = HomeUiState(
        deliverables = mapOf(ModelType.DETECTION to listOf(detector), ModelType.OCR to listOf(reader), ModelType.LLM to listOf(translator)),
        chosenIds = mapOf(ModelType.DETECTION to detector.id, ModelType.OCR to reader.id, ModelType.LLM to translator.id),
        fitBudget = FitBudget(8L * 1024 * mb, LlmModelCatalog.DEFAULT_FIT_BUDGET_PERCENT, RuntimeLimits.DEFAULT_CONTEXT_TOKENS)
    )

    private val server = PickerGroup(
        Translator.NETWORK_ID, "Your server",
        listOf(TranslatorPickerEntry("Set up your server", null, checked = false) { TranslatorPick.Refused })
    )

    private fun ready(deliverable: SlotDeliverable, type: ModelType) =
        ModelEntity(deliverable.id, deliverable.id, type, "${deliverable.id}.bin", 1L, "", "", ModelStatus.READY, version = "1", isRequired = true)

    private val allModels = listOf(ready(detector, ModelType.DETECTION), ready(reader, ModelType.OCR), ready(translator, ModelType.LLM))

    private fun HomeUiState.phases() = SetupStepId.entries.map { setupPhases().getValue(it) }

    @Test
    fun `a first run opens on the language and leaves the other steps waiting`() {
        assertEquals(listOf(SetupPhase.Active, SetupPhase.Later, SetupPhase.Later), fresh.phases())
    }

    @Test
    fun `choosing the language opens the download`() {
        val state = fresh.copy(setupLanguageChosen = true)

        assertEquals(listOf(SetupPhase.Done, SetupPhase.Active, SetupPhase.Later), state.phases())
    }

    @Test
    fun `the downloaded kit opens the permission step`() {
        val state = fresh.copy(setupLanguageChosen = true, models = allModels, readiness = Readiness.NeedsPermission)

        assertEquals(listOf(SetupPhase.Done, SetupPhase.Done, SetupPhase.Active), state.phases())
        assertFalse(state.setupAllDone)
    }

    @Test
    fun `a permission granted earlier shows done under the step still open`() {
        val state = fresh.copy(setupLanguageChosen = true, readiness = Readiness.NeedsModels)

        assertEquals(listOf(SetupPhase.Done, SetupPhase.Active, SetupPhase.Done), state.phases())
    }

    @Test
    fun `reopening the language makes it the open step again`() {
        val state = fresh.copy(setupLanguageChosen = false, models = allModels, readiness = Readiness.Ready)

        assertEquals(listOf(SetupPhase.Active, SetupPhase.Done, SetupPhase.Done), state.phases())
        assertFalse(state.setupAllDone)
    }

    @Test
    fun `setup is all done once the language is chosen, the kit is in and Yomu may draw over other apps`() {
        assertTrue(fresh.copy(setupLanguageChosen = true, models = allModels, readiness = Readiness.Ready).setupAllDone)
    }

    @Test
    fun `a translator changed after the download reopens the kit`() {
        val other = SlotDeliverable(Constants.QWEN35_2B_MODEL_ID, "Qwen3.5 2B", 1_300 * mb, "Apache-2.0", multilingual = true)
        val state = fresh.copy(
            setupLanguageChosen = true, models = allModels, readiness = Readiness.Ready,
            deliverables = fresh.deliverables + (ModelType.LLM to listOf(translator, other)),
            chosenIds = fresh.chosenIds + (ModelType.LLM to other.id)
        )

        assertEquals(SetupPhase.Active, state.setupPhases().getValue(SetupStepId.Kit))
    }

    @Test
    fun `a kit handed to another translator stays open until that translator takes the pages`() {
        val readers = allModels.take(2)
        val chosen = fresh.copy(
            setupLanguageChosen = true, models = readers, readiness = Readiness.NeedsModels,
            translatorGroups = listOf(server), setupTranslatorId = server.translatorId
        )

        assertEquals(SetupPhase.Active, chosen.setupPhases().getValue(SetupStepId.Kit))
        assertEquals(SetupPhase.Done, chosen.copy(phoneTranslates = false, readiness = Readiness.Ready).setupPhases().getValue(SetupStepId.Kit))
    }
}

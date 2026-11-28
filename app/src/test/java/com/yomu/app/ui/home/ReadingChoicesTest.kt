package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.ModelSlotSelection
import com.yomu.app.service.PickerGroup
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.translation.TranslatorPick
import com.yomu.app.translation.TranslatorPickerEntry
import com.yomu.app.ui.settings.deliverableStatus
import com.yomu.core.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingChoicesTest {
    private val qwen = SlotDeliverable("qwen", "Qwen2.5 1.5B Instruct", 1L, "Apache-2.0", multilingual = true)
    private val cat = SlotDeliverable("cat", "CAT-Translate 1.4B", 1L, "MIT", multilingual = false)
    private val state = HomeUiState(deliverables = mapOf(ModelType.LLM to listOf(qwen, cat)))

    @Test
    fun `the translator row names the selected model and says it runs on this phone`() {
        assertEquals("Qwen2.5 1.5B Instruct · on this phone", state.copy(translationSelectedId = "qwen").translatorRowLabel())
    }

    @Test
    fun `a pending pick names the model that takes over when its download finishes`() {
        assertEquals(
            "Qwen2.5 1.5B Instruct · on this phone · CAT-Translate 1.4B when downloaded",
            state.copy(translationSelectedId = "qwen", translationPendingId = "cat").translatorRowLabel()
        )
    }

    @Test
    fun `an empty translation slot says no model is selected`() {
        assertEquals("No model selected", state.translatorRowLabel())
    }

    @Test
    fun `the phone group lists the downloaded models, one downloading, and the pick waiting to take over`() {
        val hy = SlotDeliverable("hy", "Hy-MT2 1.8B", 1L, "Tencent", multilingual = true)
        val gemma = SlotDeliverable("gemma", "Gemma 4 E2B", 1L, "Gemma", multilingual = false)
        val bonsai = SlotDeliverable("bonsai", "Ternary-Bonsai 4B", 1L, "Apache-2.0", multilingual = false)
        val catalog = state.copy(
            deliverables = mapOf(ModelType.LLM to listOf(qwen, cat, hy, gemma, bonsai)),
            models = listOf(entity("qwen", ModelStatus.READY), entity("hy", ModelStatus.DOWNLOADING), entity("bonsai", ModelStatus.ERROR)),
            translationSelectedId = "qwen",
            translationPendingId = "cat"
        )

        assertEquals(listOf(qwen, cat, hy), catalog.phoneRows())
    }

    @Test
    fun `while a translator's entry is checked the row names it and its group, and no phone row is checked`() {
        val onServer = state.copy(translationSelectedId = "qwen", translatorGroups = listOf(server(checked = "gemma3:4b")))

        assertEquals("gemma3:4b · Your server", onServer.translatorRowLabel())
        assertFalse("the phone model stays selected but doesn't take the page", onServer.checked(qwen))
    }

    @Test
    fun `while the phone translates, its selected model's row is the checked one`() {
        val onPhone = state.copy(translationSelectedId = "qwen", translatorGroups = listOf(server(checked = null)))

        assertTrue(onPhone.checked(qwen))
        assertFalse(onPhone.checked(cat))
        assertEquals("Qwen2.5 1.5B Instruct · on this phone", onPhone.translatorRowLabel())
    }

    @Test
    fun `while a translator's entry is checked every language can be chosen, even with no Multilingual phone model`() {
        val onServer = state.copy(deliverables = mapOf(ModelType.LLM to listOf(cat)), translatorGroups = listOf(server(checked = "gemma3:4b")))

        assertNull(onServer.languageUnavailable(TargetLanguage.FRENCH))
    }

    @Test
    fun `a deliverable's status says whether it is downloaded, in use or taking over`() {
        assertEquals("Not downloaded", deliverableStatus(null, null, selected = false, pending = false))
        assertEquals("Not downloaded · Takes over when downloaded", deliverableStatus(ModelStatus.AVAILABLE, null, selected = false, pending = true))
        assertEquals("Downloading 40% · Takes over when downloaded", deliverableStatus(ModelStatus.DOWNLOADING, 40, selected = false, pending = true))
        assertEquals("Downloaded", deliverableStatus(ModelStatus.READY, null, selected = false, pending = false))
        assertEquals("In use", deliverableStatus(ModelStatus.READY, null, selected = true, pending = false))
        assertEquals("Couldn't download", deliverableStatus(ModelStatus.ERROR, null, selected = false, pending = false))
    }

    @Test
    fun `the language row names the target language`() {
        assertEquals("Japanese → English", state.languageRowLabel())
        assertEquals("Japanese → Spanish", state.copy(targetLanguage = TargetLanguage.SPANISH).languageRowLabel())
    }

    @Test
    fun `an English-only model is unavailable with the reason while the language is not English`() {
        val spanish = state.copy(targetLanguage = TargetLanguage.SPANISH)

        assertEquals("Can't write Spanish", spanish.languageBlock(cat))
        assertNull(spanish.languageBlock(qwen))
        assertNull(state.languageBlock(cat))
    }

    @Test
    fun `the language sheet offers only English when no Multilingual model fits`() {
        val englishOnly = state.copy(deliverables = mapOf(ModelType.LLM to listOf(cat)))

        assertEquals(ModelSlotSelection.NO_MULTILINGUAL_FIT, englishOnly.languageUnavailable(TargetLanguage.FRENCH))
        assertNull(englishOnly.languageUnavailable(TargetLanguage.ENGLISH))
        assertNull(state.languageUnavailable(TargetLanguage.FRENCH))
    }

    private fun entity(id: String, status: ModelStatus) =
        ModelEntity(id, id, ModelType.LLM, "$id.gguf", 1L, "", "", status, version = "1", isRequired = false)

    private fun server(checked: String?) = PickerGroup(
        "network", "Your server",
        listOf("gemma3:4b", "qwen2.5:7b").map { name -> TranslatorPickerEntry(name, null, name == checked) { TranslatorPick.Select } }
    )
}

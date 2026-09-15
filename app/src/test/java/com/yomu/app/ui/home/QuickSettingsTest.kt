package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.ui.settings.deliverableStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSettingsTest {
    private val qwen = SlotDeliverable("qwen", "Qwen2.5 1.5B Instruct", 1L, "Apache-2.0")
    private val cat = SlotDeliverable("cat", "CAT-Translate 1.4B", 1L, "MIT")
    private val state = HomeUiState(deliverables = mapOf(ModelType.LLM to listOf(qwen, cat)))

    @Test
    fun `the model chip names the selected translation model`() {
        assertEquals("Qwen2.5 1.5B Instruct", state.copy(translationSelectedId = "qwen").translationChipLabel())
    }

    @Test
    fun `a pending pick names the model that takes over when its download finishes`() {
        assertEquals(
            "Qwen2.5 1.5B Instruct · CAT-Translate 1.4B when downloaded",
            state.copy(translationSelectedId = "qwen", translationPendingId = "cat").translationChipLabel()
        )
    }

    @Test
    fun `an empty translation slot says no model is selected`() {
        assertEquals("No model selected", state.translationChipLabel())
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
}

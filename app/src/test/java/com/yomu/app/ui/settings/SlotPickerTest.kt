package com.yomu.app.ui.settings

import com.yomu.app.service.SlotDeliverable
import org.junit.Assert.assertEquals
import org.junit.Test

class SlotPickerTest {
    private val gb = 1024L * 1024 * 1024

    @Test
    fun `a translation model's detail line shows its language label after the speed label`() {
        val qwen = SlotDeliverable("qwen", "Qwen2.5 1.5B Instruct", gb, "Apache-2.0", multilingual = true)
        val cat = SlotDeliverable("cat", "CAT-Translate 1.4B", gb, "MIT", experimental = true, multilingual = false)

        assertEquals("1.00 GB · Fast · Multilingual · Apache-2.0 · Fits this device", deliverableDetail(qwen, fits = true))
        assertEquals("1.00 GB · Fast · English only · MIT · Experimental · Needs more memory", deliverableDetail(cat, fits = false))
    }

    @Test
    fun `a reading model's detail line carries no language label`() {
        val ocr = SlotDeliverable("ocr", "manga-ocr", gb, "Apache-2.0 (manga-ocr)")

        assertEquals("1.00 GB · Fast · Apache-2.0 (manga-ocr) · Fits this device", deliverableDetail(ocr, fits = true))
    }

    @Test
    fun `an entry blocked by the target language says why`() {
        val cat = SlotDeliverable("cat", "CAT-Translate 1.4B", gb, "MIT", multilingual = false)

        assertEquals(
            "1.00 GB · Fast · English only · MIT · Fits this device · Can't write Spanish",
            deliverableDetail(cat, fits = true, languageBlock = "Can't write Spanish")
        )
    }

    @Test
    fun `deleting the model in use warns that reading stops while the phone translates`() {
        assertEquals(
            DeletePrompt("Delete the model in use?", "Yomu can't translate or read until you pick and download a model."),
            deleteSelectedPrompt(phoneModelIdle = false)
        )
    }

    @Test
    fun `deleting the phone model while a server translates says reading stays on`() {
        assertEquals(
            DeletePrompt(
                "Delete this model?",
                "It isn't translating now, so reading stays on. If the server stops being set up, its pages go to the " +
                    "phone, and Yomu can't translate until you pick and download a model."
            ),
            deleteSelectedPrompt(phoneModelIdle = true)
        )
    }
}

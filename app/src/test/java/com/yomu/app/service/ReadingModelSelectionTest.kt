package com.yomu.app.service

import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.MapSharedPreferences
import com.yomu.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingModelSelectionTest {

    private val prefs = MapSharedPreferences()
    private val selection = ReadingModelSelection(prefs)

    @Test
    fun `an unset slot falls back to its curated default`() {
        assertEquals(Constants.BUBBLE_DETECTION_MODEL_ID, selection.selected(ModelType.DETECTION).id)
        assertEquals(Constants.MANGA_OCR_MODEL_ID, selection.selected(ModelType.OCR).id)
    }

    @Test
    fun `a selection is stored per slot`() {
        val ocr = ModelManager.REGISTRY.first { it.type == ModelType.OCR }

        selection.select(ocr)

        assertEquals(ocr.id, prefs.values[Constants.PREF_OCR_MODEL])
        assertEquals(ocr, selection.selected(ModelType.OCR))
        assertEquals(null, prefs.values[Constants.PREF_DETECTION_MODEL])
    }

    @Test
    fun `an unknown or wrong-slot stored id falls back to the slot default`() {
        prefs.values[Constants.PREF_DETECTION_MODEL] = "retired-model"
        prefs.values[Constants.PREF_OCR_MODEL] = Constants.BUBBLE_DETECTION_MODEL_ID

        assertEquals(Constants.BUBBLE_DETECTION_MODEL_ID, selection.selected(ModelType.DETECTION).id)
        assertEquals(Constants.MANGA_OCR_MODEL_ID, selection.selected(ModelType.OCR).id)
    }

    @Test
    fun `a wrongly typed stored id falls back to the slot default`() {
        prefs.values[Constants.PREF_DETECTION_MODEL] = 42

        assertEquals(Constants.BUBBLE_DETECTION_MODEL_ID, selection.selected(ModelType.DETECTION).id)
    }
}

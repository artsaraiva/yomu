package com.yomu.app.ui.models

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCapabilityTest {

    @Test
    fun `bubble detection id maps to bubble detection capability`() {
        val model = model(id = Constants.BUBBLE_DETECTION_MODEL_ID, type = ModelType.VISION)

        assertEquals(ModelCapability.BUBBLE_DETECTION, model.capability())
    }

    @Test
    fun `vision manga ocr model maps to ocr capability`() {
        val model = model(id = Constants.MANGA_OCR_MODEL_ID, type = ModelType.VISION)

        assertEquals(ModelCapability.OCR, model.capability())
    }

    @Test
    fun `ml kit model maps to translation engine capability`() {
        val model = model(id = Constants.ML_KIT_JA_EN_MODEL_ID, type = ModelType.TRANSLATION)

        assertEquals(ModelCapability.TRANSLATION_ENGINE, model.capability())
    }

    @Test
    fun `qwen llm model maps to translation engine capability`() {
        val model = model(id = Constants.QWEN_TRANSLATION_MODEL_ID, type = ModelType.LLM)

        assertEquals(ModelCapability.TRANSLATION_ENGINE, model.capability())
    }

    private fun model(id: String, type: ModelType): ModelEntity {
        return ModelEntity(
            id = id,
            name = id,
            type = type,
            fileName = "$id.bin",
            fileSize = 1L,
            downloadUrl = "",
            checksum = "",
            status = ModelStatus.AVAILABLE,
            version = "1.0",
            isRequired = false
        )
    }
}

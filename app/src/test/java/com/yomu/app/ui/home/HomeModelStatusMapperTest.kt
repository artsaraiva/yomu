package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeModelStatusMapperTest {

    @Test
    fun `empty models returns not downloaded`() {
        val result = HomeViewModel.ModelStatusMapper.map(emptyList())

        assertEquals("Not downloaded", result.first)
        assertEquals(0, result.second)
    }

    @Test
    fun `all required ready returns ready`() {
        val models = listOf(
            model("a", true, ModelStatus.READY),
            model("b", true, ModelStatus.READY),
            model("c", false, ModelStatus.AVAILABLE)
        )

        val result = HomeViewModel.ModelStatusMapper.map(models)

        assertEquals("Ready", result.first)
        assertEquals(2, result.second)
    }

    @Test
    fun `partial required ready returns fraction`() {
        val models = listOf(
            model("a", true, ModelStatus.READY),
            model("b", true, ModelStatus.AVAILABLE),
            model("c", true, ModelStatus.DOWNLOADING)
        )

        val result = HomeViewModel.ModelStatusMapper.map(models)

        assertEquals("1/3 required ready", result.first)
        assertEquals(1, result.second)
    }

    @Test
    fun `no required ready returns not downloaded`() {
        val models = listOf(
            model("a", true, ModelStatus.AVAILABLE),
            model("b", true, ModelStatus.ERROR)
        )

        val result = HomeViewModel.ModelStatusMapper.map(models)

        assertEquals("Not downloaded", result.first)
        assertEquals(0, result.second)
    }

    private fun model(id: String, required: Boolean, status: ModelStatus): ModelEntity {
        return ModelEntity(
            id = id,
            name = id,
            type = ModelType.VISION,
            fileName = "$id.bin",
            fileSize = 1L,
            downloadUrl = "",
            checksum = "",
            status = status,
            version = "1.0",
            isRequired = required
        )
    }
}

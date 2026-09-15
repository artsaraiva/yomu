package com.yomu.app.service

import android.content.Context
import com.yomu.app.db.ModelDao
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.core.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class ModelManagerRefreshTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeModelDao : ModelDao {
        val rows = MutableStateFlow<Map<String, ModelEntity>>(emptyMap())
        override fun getAllModels(): Flow<List<ModelEntity>> = MutableStateFlow(rows.value.values.toList())
        override suspend fun getModelById(id: String) = rows.value[id]
        override suspend fun getModelsByType(type: String) = rows.value.values.filter { it.type.name == type }
        override suspend fun getModelsByStatus(status: ModelStatus) = rows.value.values.filter { it.status == status }
        override suspend fun insertModel(model: ModelEntity) { rows.value = rows.value + (model.id to model) }
        override suspend fun updateModel(model: ModelEntity) = insertModel(model)
        override suspend fun deleteModel(model: ModelEntity) { rows.value = rows.value - model.id }
        override suspend fun updateModelStatus(id: String, status: ModelStatus, updatedAt: Long) = Unit
        override suspend fun updateDownloadProgress(id: String, progress: Int) = Unit
    }

    @Test
    fun `refresh deletes a persisted row no longer in the registry along with its file`() = runTest {
        val filesDir = tmp.newFolder()
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(filesDir)
        val dao = FakeModelDao()
        val stale = ModelManager.REGISTRY.first { it.type == ModelType.LLM }
            .copy(id = "hunyuan_mt_7b_v1", fileName = "hunyuan_mt_7b_q3_k_m.gguf", status = ModelStatus.READY)
        dao.insertModel(stale)
        val llmDir = File(filesDir, "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}").apply { mkdirs() }
        val staleFile = File(llmDir, stale.fileName).apply { writeText("weights") }

        ModelManager(context, dao, OkHttpClient()).refreshModelList()

        assertFalse(staleFile.exists())
        assertFalse(stale.id in dao.rows.value)
        assertEquals(ModelManager.REGISTRY.map { it.id }.toSet(), dao.rows.value.keys)
    }

    @Test
    fun `model files list the main file then its additional files in the vision directory`() {
        val filesDir = tmp.newFolder()
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(filesDir)
        val ocr = ModelManager.REGISTRY.single { it.id == Constants.MANGA_OCR_MODEL_ID }
        val visionDir = File(filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}")

        val files = ModelManager(context, FakeModelDao(), OkHttpClient()).modelFiles(ocr)

        assertEquals(
            listOf(Constants.OCR_ENCODER_MODEL, Constants.OCR_DECODER_MODEL, Constants.OCR_VOCAB_FILE).map { File(visionDir, it) },
            files
        )
    }
}

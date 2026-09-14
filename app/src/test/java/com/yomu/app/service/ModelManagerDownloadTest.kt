package com.yomu.app.service

import android.content.Context
import com.yomu.app.db.ModelDao
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File
import java.io.InputStream

class ModelManagerDownloadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dao = RecordingModelDao()

    private val endlessDownload = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(EndlessStream().source().buffer().asResponseBody())
                .build()
        }
        .build()

    private val manager = ModelManager(mock(Context::class.java), dao, endlessDownload)

    @Test
    fun `cancelling a download deletes its partial files and makes the model available again`() = runBlocking {
        val primary = File(tmp.root, "model.onnx")
        val additional = File(tmp.root, "decoder.onnx").apply { writeText("left from an earlier try") }
        val tasks = listOf(
            ModelManager.DownloadTask(primary, "https://example.com/model.onnx", "", 1_000L),
            ModelManager.DownloadTask(additional, "https://example.com/decoder.onnx", "", 1_000L)
        )
        val started = CompletableDeferred<Unit>()

        val download = launch { manager.downloadTasks("model", tasks) { started.complete(Unit) } }
        started.await()
        withTimeout(5_000) { download.cancelAndJoin() }

        assertFalse(primary.exists())
        assertFalse(additional.exists())
        assertEquals(ModelStatus.AVAILABLE, dao.statuses["model"])
        assertEquals(0, dao.progress["model"])
    }

    private class EndlessStream : InputStream() {
        override fun read(): Int = 0
        override fun read(b: ByteArray, off: Int, len: Int): Int = len
    }

    private class RecordingModelDao : ModelDao {
        val statuses = mutableMapOf<String, ModelStatus>()
        val progress = mutableMapOf<String, Int>()

        override fun getAllModels(): Flow<List<ModelEntity>> = flowOf(emptyList())
        override suspend fun getModelById(id: String): ModelEntity? = null
        override suspend fun getModelsByType(type: String): List<ModelEntity> = emptyList()
        override suspend fun getModelsByStatus(status: ModelStatus): List<ModelEntity> = emptyList()
        override suspend fun insertModel(model: ModelEntity) = Unit
        override suspend fun updateModel(model: ModelEntity) = Unit
        override suspend fun deleteModel(model: ModelEntity) = Unit
        override suspend fun updateModelStatus(id: String, status: ModelStatus, updatedAt: Long) {
            statuses[id] = status
        }
        override suspend fun updateDownloadProgress(id: String, progress: Int) {
            this.progress[id] = progress
        }
    }
}

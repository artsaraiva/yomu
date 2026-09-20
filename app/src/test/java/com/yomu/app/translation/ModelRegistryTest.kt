package com.yomu.app.translation

import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.ModelManager
import com.yomu.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegistryTest {

    private val registry = ModelManager.REGISTRY

    @Test
    fun `model ids are unique`() {
        val duplicates = registry.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate ids: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `every http row carries a sha256 checksum`() {
        val sha256 = Regex("[0-9a-f]{64}")
        registry.filter { it.downloadUrl.startsWith("http") }.forEach {
            assertTrue("${it.id}: '${it.checksum}'", sha256.matches(it.checksum))
        }
    }

    @Test
    fun `every http url is pinned to a revision sha`() {
        val pinned = Regex("https://huggingface\\.co/[^/]+/[^/]+/resolve/[0-9a-f]{40}/.+")
        val urls = registry.map { it.downloadUrl } +
            registry.flatMap { ModelManager.additionalFiles(it.id) }.map { it.url }
        urls.filter { it.startsWith("http") }.forEach {
            assertTrue(it, pinned.matches(it))
        }
    }

    @Test
    fun `llm rows are exactly the catalog entries`() {
        val llmRowIds = registry.filter { it.type == ModelType.LLM }.map { it.id }.toSet()
        assertEquals(LlmModelCatalog.ALL.map { it.id }.toSet(), llmRowIds)
    }

    @Test
    fun `Qwen3_5 2B downloads the pinned unsloth Q4_K_M`() {
        val row = registry.single { it.id == Constants.QWEN35_2B_MODEL_ID }

        assertEquals(
            "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/f6d5376be1edb4d416d56da11e5397a961aca8ae/Qwen3.5-2B-Q4_K_M.gguf",
            row.downloadUrl
        )
        assertEquals("aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223", row.checksum)
        assertEquals(1_280_835_840L, row.fileSize)
    }

    @Test
    fun `Ministral 3 downloads the pinned first-party Q4_K_M`() {
        val threeB = registry.single { it.id == Constants.MINISTRAL3_3B_MODEL_ID }
        assertEquals(
            "https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF/resolve/" +
                "eb599d408350ea2bb60452cb86be7c7b2fc28227/Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
            threeB.downloadUrl
        )
        assertEquals("9ed150d4367e68df0ac8e1540f6ddc65b42d0ee26378329d1ecbca60f93fc5f8", threeB.checksum)
        assertEquals(2_147_023_008L, threeB.fileSize)

        val eightB = registry.single { it.id == Constants.MINISTRAL3_8B_MODEL_ID }
        assertEquals(
            "https://huggingface.co/mistralai/Ministral-3-8B-Instruct-2512-GGUF/resolve/" +
                "0102285ad796bd99af90f58de616092e5630e970/Ministral-3-8B-Instruct-2512-Q4_K_M.gguf",
            eightB.downloadUrl
        )
        assertEquals("33e7a72cf5e6e2cfc2f2847075acc013d68bba023e35310cef86b5cf8fdca761", eightB.checksum)
        assertEquals(5_198_911_904L, eightB.fileSize)
    }

    @Test
    fun `every slot has exactly one curated default of its own type`() {
        assertEquals(ModelType.entries.toSet(), ModelManager.SLOT_DEFAULTS.keys)
        ModelManager.SLOT_DEFAULTS.forEach { (type, id) ->
            assertEquals(type, registry.single { it.id == id }.type)
        }
        assertEquals(LlmModelCatalog.DEFAULT.id, ModelManager.SLOT_DEFAULTS[ModelType.LLM])
    }

    @Test
    fun `every row downloads over http`() {
        val nonHttp = registry.map { it.downloadUrl }.filterNot { it.startsWith("http") }
        assertTrue("non-http urls: $nonHttp", nonHttp.isEmpty())
    }
}

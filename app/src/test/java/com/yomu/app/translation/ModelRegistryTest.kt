package com.yomu.app.translation

import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.ModelManager
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

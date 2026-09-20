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
    fun `every Qwen entry downloads the pinned unsloth Q4_K_M`() {
        // No first-party GGUF exists for either line (Qwen's own repos return 401), so the research
        // addendum's host rule lands on unsloth. Repo, commit, bytes and sha256 as pinned there.
        val pins = mapOf(
            Constants.QWEN35_08B_MODEL_ID to Triple(
                "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/6ab461498e2023f6e3c1baea90a8f0fe38ab64d0/Qwen3.5-0.8B-Q4_K_M.gguf",
                "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517",
                532_517_120L
            ),
            Constants.QWEN35_2B_MODEL_ID to Triple(
                "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/f6d5376be1edb4d416d56da11e5397a961aca8ae/Qwen3.5-2B-Q4_K_M.gguf",
                "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
                1_280_835_840L
            ),
            Constants.QWEN35_4B_MODEL_ID to Triple(
                "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/e87f176479d0855a907a41277aca2f8ee7a09523/Qwen3.5-4B-Q4_K_M.gguf",
                "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
                2_740_937_888L
            ),
            Constants.QWEN35_9B_MODEL_ID to Triple(
                "https://huggingface.co/unsloth/Qwen3.5-9B-GGUF/resolve/3885219b6810b007914f3a7950a8d1b469d598a5/Qwen3.5-9B-Q4_K_M.gguf",
                "03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8",
                5_680_522_464L
            ),
            Constants.QWEN3_4B_2507_MODEL_ID to Triple(
                "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/a06e946bb6b655725eafa393f4a9745d460374c9/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
                "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597",
                2_497_281_120L
            )
        )

        pins.forEach { (id, pin) ->
            val row = registry.single { it.id == id }
            val (url, checksum, size) = pin
            assertEquals(id, url, row.downloadUrl)
            assertEquals(id, checksum, row.checksum)
            assertEquals(id, size, row.fileSize)
        }
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

package com.yomu.app.translation

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
    fun `the ML Kit sentinel is the only non-http download url`() {
        val nonHttp = registry.map { it.downloadUrl }.filterNot { it.startsWith("http") }
        assertEquals(listOf("google-mlkit-translate-ja-en"), nonHttp)
    }
}

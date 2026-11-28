package com.yomu.app.ui.settings

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.translation.DeliverableSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How the Translator page lists the phone's models and names what takes the pages (#548). */
class TranslatorModelsTest {
    private val gb = 1024L * 1024 * 1024
    private val catalog = listOf("a", "b", "c", "d", "e", "f").map { SlotDeliverable(it, "Model $it", gb, "MIT", multilingual = true) }

    private fun group(
        statuses: Map<String, ModelStatus> = emptyMap(),
        busy: Set<String> = emptySet(),
        tooBig: Set<String> = emptySet()
    ) = phoneModels(catalog, statuses, busy) { it.id !in tooBig }

    private fun PhoneModels.ids() = listOf(downloaded, toDownload, more, tooBig).map { group -> group.map { it.id } }

    @Test
    fun `downloaded models lead, the next few are offered and the rest fold away`() {
        val groups = group(statuses = mapOf("c" to ModelStatus.READY))

        assertEquals(listOf(listOf("c"), listOf("a", "b", "d"), listOf("e", "f"), emptyList()), groups.ids())
    }

    @Test
    fun `models that need more memory than the phone has are set apart`() {
        val groups = group(tooBig = setOf("a", "f"))

        assertEquals(listOf(emptyList(), listOf("b", "c", "d"), listOf("e"), listOf("a", "f")), groups.ids())
    }

    @Test
    fun `a downloaded model stays with the downloaded ones when it no longer fits, so it can be deleted`() {
        val groups = group(statuses = mapOf("a" to ModelStatus.READY), tooBig = setOf("a"))

        assertEquals(listOf("a"), groups.downloaded.map { it.id })
        assertEquals(emptyList<String>(), groups.tooBig.map { it.id })
    }

    @Test
    fun `a model that is downloading, waiting or failed is never folded away`() {
        val groups = group(statuses = mapOf("f" to ModelStatus.ERROR), busy = setOf("e"))

        assertEquals(listOf("a", "b", "c", "e", "f"), groups.toDownload.map { it.id })
        assertEquals(listOf("d"), groups.more.map { it.id })
    }

    @Test
    fun `a model that stops fitting while it downloads keeps its row, and with it Cancel`() {
        val groups = group(busy = setOf("f"), tooBig = setOf("f"))

        assertEquals(listOf("a", "b", "c", "f"), groups.toDownload.map { it.id })
        assertEquals(emptyList<String>(), groups.tooBig.map { it.id })
    }

    @Test
    fun `every model is in exactly one group`() {
        val groups = group(statuses = mapOf("b" to ModelStatus.READY, "d" to ModelStatus.DOWNLOADING), busy = setOf("d"), tooBig = setOf("e"))

        assertEquals(catalog.map { it.id }.sorted(), groups.ids().flatten().sorted())
    }

    @Test
    fun `a model's tags are its speed and the languages it writes`() {
        val six = SlotDeliverable("q", "Qwen", gb, "Apache-2.0", multilingual = true)
        val english = SlotDeliverable("c", "CAT", gb, "MIT", speedOverride = DeliverableSpeed.SLOW, multilingual = false)

        assertEquals(listOf("Fast", "6 languages"), modelTags(six))
        assertEquals(listOf("Slow", "English only"), modelTags(english))
    }

    @Test
    fun `a downloaded model's line says whether it is in use, then its size`() {
        val model = catalog.first()

        assertEquals("In use · 1.00 GB", downloadedLine(model, inUse = true, languageBlock = null))
        assertEquals("Downloaded · 1.00 GB", downloadedLine(model, inUse = false, languageBlock = null))
        assertEquals("Downloaded · 1.00 GB · Can't write Spanish", downloadedLine(model, inUse = false, languageBlock = "Can't write Spanish"))
    }

    @Test
    fun `a model to download says nothing until something is happening to it`() {
        assertNull(downloadNote(ModelStatus.AVAILABLE, progress = null, pending = false, languageBlock = null))
        assertEquals("Downloading 42% · Takes over when downloaded", downloadNote(ModelStatus.DOWNLOADING, 42, pending = true, languageBlock = null))
        assertEquals("Couldn't download", downloadNote(ModelStatus.ERROR, null, pending = false, languageBlock = null))
        assertEquals("Can't write Spanish", downloadNote(ModelStatus.AVAILABLE, null, pending = false, languageBlock = "Can't write Spanish"))
    }

    @Test
    fun `a model's detail holds what left its card`() {
        val plain = SlotDeliverable("q", "Qwen", gb, "Apache-2.0", multilingual = true)

        assertEquals(listOf("1.00 GB", "Licence: Apache-2.0"), modelFacts(plain, fits = true))
        assertEquals(
            listOf("1.00 GB", "Licence: Apache-2.0", "Experimental", "Needs more memory than this phone has"),
            modelFacts(plain.copy(experimental = true), fits = false)
        )
    }

    @Test
    fun `the folded rows say how many models they hold`() {
        assertEquals("Show 9 more", moreLabel(9))
        assertEquals("3 models need more memory than this phone has", tooBigLabel(3))
        assertEquals("1 model needs more memory than this phone has", tooBigLabel(1))
    }

    @Test
    fun `the page opens on the side that takes the next page`() {
        assertEquals(TranslatorSide.Phone, activeSide(translatesOnServer = false))
        assertEquals(TranslatorSide.Server, activeSide(translatesOnServer = true))
    }

    @Test
    fun `a side that doesn't have the pages yet says who does and what hands them over`() {
        assertNull(sideNote(TranslatorSide.Phone, phoneTranslates = true, translatesOnServer = false, serverReady = false))
        assertNull(sideNote(TranslatorSide.Server, phoneTranslates = false, translatesOnServer = true, serverReady = true))
        assertEquals(
            "Your server translates the pages until you pick a model here.",
            sideNote(TranslatorSide.Phone, phoneTranslates = false, translatesOnServer = true, serverReady = true)
        )
        assertEquals(
            "Another translator has the pages until you pick a model here.",
            sideNote(TranslatorSide.Phone, phoneTranslates = false, translatesOnServer = false, serverReady = false)
        )
        assertEquals(
            "Pages translate on this phone until your server is connected and a model is picked.",
            sideNote(TranslatorSide.Server, phoneTranslates = true, translatesOnServer = false, serverReady = false)
        )
        assertEquals(
            "Your server is ready, and pages still translate on this phone.",
            sideNote(TranslatorSide.Server, phoneTranslates = true, translatesOnServer = false, serverReady = true)
        )
    }
}

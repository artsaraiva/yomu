package com.yomu.app.ui.settings

import com.yomu.app.translation.MapSharedPreferences
import com.yomu.app.translation.NameGlossaryStore
import com.yomu.core.GlossaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NameGlossaryViewModelTest {

    private val store = NameGlossaryStore(MapSharedPreferences())

    @Test
    fun `the screen opens on the stored glossary`() {
        store.save("ルフィ", "Luffy")

        assertEquals(listOf(GlossaryEntry("ルフィ", "Luffy")), NameGlossaryViewModel(store).entries.value)
    }

    @Test
    fun `saved and deleted entries show at once`() {
        val viewModel = NameGlossaryViewModel(store)

        viewModel.save("ルフィ", "Luffy", replacing = null)
        viewModel.save("ゾロ", "Zoro", replacing = null)
        viewModel.delete("ルフィ")

        assertEquals(listOf(GlossaryEntry("ゾロ", "Zoro")), viewModel.entries.value)
    }

    @Test
    fun `a refused entry reports it was not saved`() {
        assertFalse(NameGlossaryViewModel(store).save(" ", "Luffy", replacing = null))
    }
}

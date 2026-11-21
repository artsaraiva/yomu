package com.yomu.app.ui.settings

import com.yomu.app.service.SaveToRecentStore
import com.yomu.app.translation.MapSharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveToRecentViewModelTest {

    private val prefs = MapSharedPreferences()

    @Test
    fun `the switch opens on the stored value and a flip shows at once and is kept`() {
        SaveToRecentStore(prefs).save(false)
        val viewModel = SaveToRecentViewModel(prefs)
        assertFalse(viewModel.enabled.value)

        viewModel.setEnabled(true)

        assertTrue(viewModel.enabled.value)
        assertTrue(SaveToRecentStore(prefs).load())
    }
}

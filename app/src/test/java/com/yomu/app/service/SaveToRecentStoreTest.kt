package com.yomu.app.service

import com.yomu.app.translation.MapSharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveToRecentStoreTest {

    private val prefs = MapSharedPreferences()
    private val store = SaveToRecentStore(prefs)

    @Test
    fun `nothing stored loads saving on`() {
        assertTrue(store.load())
    }

    @Test
    fun `turning saving off and on again is kept`() {
        store.save(false)
        assertFalse(SaveToRecentStore(prefs).load())

        store.save(true)
        assertTrue(SaveToRecentStore(prefs).load())
    }

    @Test
    fun `a wrongly typed stored value loads saving on`() {
        prefs.values[SaveToRecentStore.KEY] = "off"

        assertTrue(store.load())
    }
}

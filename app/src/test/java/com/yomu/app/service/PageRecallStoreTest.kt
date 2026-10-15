package com.yomu.app.service

import com.yomu.app.translation.MapSharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRecallStoreTest {

    private val prefs = MapSharedPreferences()
    private val store = PageRecallStore(prefs)

    @Test
    fun `nothing stored loads recall on`() {
        assertTrue(store.load())
    }

    @Test
    fun `turning recall off and on again is kept`() {
        store.save(false)
        assertFalse(PageRecallStore(prefs).load())

        store.save(true)
        assertTrue(PageRecallStore(prefs).load())
    }

    @Test
    fun `a wrongly typed stored value loads recall on`() {
        prefs.values[PageRecallStore.KEY] = "off"

        assertTrue(store.load())
    }
}

package com.yomu.app.translation

import com.yomu.core.RuntimeLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceLimitsStoreTest {

    private val prefs = MapSharedPreferences()
    private val store = ResourceLimitsStore(prefs)

    @Test
    fun `nothing stored loads every default`() {
        assertEquals(RuntimeLimits(), store.runtime())
        assertEquals(60, store.load(ResourceLimit.FIT_BUDGET_PERCENT))
    }

    @Test
    fun `every offered value saves and loads back`() {
        ResourceLimit.entries.forEach { limit ->
            limit.options.forEach { value ->
                assertTrue("$limit $value", store.save(limit, value))
                assertEquals(value, ResourceLimitsStore(prefs).load(limit))
            }
        }
    }

    @Test
    fun `saved threads and context reach the runtime limits`() {
        store.save(ResourceLimit.THREADS, 1)
        store.save(ResourceLimit.CONTEXT_TOKENS, 1536)

        assertEquals(RuntimeLimits(threads = 1, contextTokens = 1536), store.runtime())
    }

    @Test
    fun `values outside the offered options are refused and storage is untouched`() {
        store.save(ResourceLimit.FIT_BUDGET_PERCENT, 45)
        val before = prefs.all

        assertFalse(store.save(ResourceLimit.THREADS, 0))
        assertFalse(store.save(ResourceLimit.THREADS, RuntimeLimits.MAX_THREADS + 1))
        assertFalse(store.save(ResourceLimit.CONTEXT_TOKENS, 3000))
        assertFalse(store.save(ResourceLimit.FIT_BUDGET_PERCENT, 95))
        assertFalse(store.save(ResourceLimit.FIT_BUDGET_PERCENT, 42))

        assertEquals(before, prefs.all)
    }

    @Test
    fun `a stored unoffered or wrongly typed value loads as the default`() {
        prefs.values[ResourceLimit.CONTEXT_TOKENS.key] = 999
        assertEquals(RuntimeLimits.DEFAULT_CONTEXT_TOKENS, store.load(ResourceLimit.CONTEXT_TOKENS))

        prefs.values[ResourceLimit.THREADS.key] = "corrupt"
        assertEquals(RuntimeLimits.DEFAULT_THREADS, store.load(ResourceLimit.THREADS))
    }

    @Test
    fun `reset restores every default`() {
        ResourceLimit.entries.forEach { store.save(it, it.options.first()) }

        store.reset()

        ResourceLimit.entries.forEach { assertEquals(it.default, store.load(it)) }
    }

    @Test
    fun `fitBudget carries the stored percent and context size`() {
        store.save(ResourceLimit.FIT_BUDGET_PERCENT, 45)
        store.save(ResourceLimit.CONTEXT_TOKENS, 1536)

        assertEquals(FitBudget(8L, 45, 1536), store.fitBudget(8L))
    }
}

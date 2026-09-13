package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationProfileStoreTest {

    private val prefs = MapSharedPreferences()
    private val store = GenerationProfileStore(prefs)

    @Test
    fun `a saved in-range profile loads back identically`() {
        assertTrue(store.save(GenerationBound.TEMPERATURE, 0.7f))
        assertTrue(store.save(GenerationBound.TOP_K, 12f))
        assertTrue(store.save(GenerationBound.TOP_P, 0.5f))

        val loaded = GenerationProfileStore(prefs).load()

        assertEquals(GenerationParams(temperature = 0.7f, topK = 12, topP = 0.5f), loaded.params)
        assertTrue(loaded.recovered.isEmpty())
    }

    @Test
    fun `an out-of-range save is refused and storage is untouched`() {
        store.save(GenerationBound.TOP_K, 12f)
        val before = prefs.all

        GenerationBound.entries.forEach { bound ->
            assertFalse(bound.name, store.save(bound, Math.nextUp(bound.max)))
            assertFalse(bound.name, store.save(bound, Math.nextDown(bound.min)))
            assertFalse(bound.name, store.save(bound, Float.NaN))
        }

        assertEquals(before, prefs.all)
    }

    @Test
    fun `a missing value loads as the default without a recovery report`() {
        val loaded = store.load()

        assertEquals(GenerationParams(), loaded.params)
        assertTrue(loaded.recovered.isEmpty())
    }

    @Test
    fun `a stored out-of-range value loads as the default and is reported`() {
        prefs.values[GenerationProfileStore.key(GenerationBound.TOP_K)] = 500f

        val loaded = store.load()

        assertEquals(GenerationParams(), loaded.params)
        assertEquals(listOf(GenerationBound.TOP_K), loaded.recovered)
    }

    @Test
    fun `a non-finite or wrongly typed stored value recovers`() {
        prefs.values[GenerationProfileStore.key(GenerationBound.TEMPERATURE)] = Float.NaN
        prefs.values[GenerationProfileStore.key(GenerationBound.TOP_P)] = "corrupt"

        val loaded = store.load()

        assertEquals(GenerationParams(), loaded.params)
        assertEquals(listOf(GenerationBound.TEMPERATURE, GenerationBound.TOP_P), loaded.recovered)
    }

    @Test
    fun `good fields survive next to recovered ones`() {
        store.save(GenerationBound.TOP_P, 0.5f)
        prefs.values[GenerationProfileStore.key(GenerationBound.TEMPERATURE)] = -1f

        val loaded = store.load()

        assertEquals(GenerationParams(topP = 0.5f), loaded.params)
        assertEquals(listOf(GenerationBound.TEMPERATURE), loaded.recovered)
    }

    @Test
    fun `reset restores every field`() {
        store.save(GenerationBound.TEMPERATURE, 1f)
        store.save(GenerationBound.TOP_K, 1f)
        prefs.values[GenerationProfileStore.key(GenerationBound.TOP_P)] = 9f

        store.reset()

        assertEquals(GenerationProfileStore.Loaded(GenerationParams(), emptyList()), store.load())
    }
}

/** Map-backed [SharedPreferences]: getFloat throws on a wrongly typed value, as the platform does. */
internal class MapSharedPreferences : SharedPreferences {
    val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
    override fun contains(key: String): Boolean = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableListOf<(MutableMap<String, Any?>) -> Unit>()
        private fun put(key: String, value: Any?) = apply { pending += { it[key] = value } }
        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = apply { pending += { it.remove(key) } }
        override fun clear() = apply { pending += { it.clear() } }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() { pending.forEach { it(values) }; pending.clear() }
    }
}

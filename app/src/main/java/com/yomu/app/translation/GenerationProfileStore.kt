package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams

/**
 * The reader's global generation profile (#192), persisted field by field against [GenerationBound].
 * Save refuses anything the bounds reject, so nothing invalid reaches storage. Load recovers a
 * stored value that is non-finite, out of range or wrongly typed to the default and names it in
 * [Loaded.recovered], so the caller raises one warning. An absent value is simply the default: a
 * fresh install has nothing to warn about.
 */
class GenerationProfileStore(private val prefs: SharedPreferences) {

    data class Loaded(val params: GenerationParams, val recovered: List<GenerationBound>)

    fun load(): Loaded {
        val recovered = mutableListOf<GenerationBound>()
        val params = GenerationBound.entries.fold(GenerationParams()) { params, bound ->
            if (!prefs.contains(key(bound))) return@fold params
            val stored = runCatching { prefs.getFloat(key(bound), bound.default) }.getOrNull()
            if (stored != null && bound.accepts(stored)) {
                bound.write(params, stored)
            } else {
                recovered += bound
                params
            }
        }
        return Loaded(params, recovered)
    }

    fun save(bound: GenerationBound, value: Float): Boolean {
        if (!bound.accepts(value)) return false
        prefs.edit().putFloat(key(bound), value).apply()
        return true
    }

    fun reset() = forget(GenerationBound.entries)

    fun forget(bounds: List<GenerationBound>) {
        prefs.edit().apply { bounds.forEach { remove(key(it)) } }.apply()
    }

    companion object {
        fun key(bound: GenerationBound): String = "generation_${bound.name.lowercase()}"
    }
}

package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.RuntimeLimits

/** A reader-set cap on what the translation model may use (#79); [options] are the only values save accepts. */
enum class ResourceLimit(val key: String, val default: Int, val options: List<Int>) {
    THREADS("llm_threads", RuntimeLimits.DEFAULT_THREADS, (1..RuntimeLimits.MAX_THREADS).toList()),
    CONTEXT_TOKENS("llm_context_tokens", RuntimeLimits.DEFAULT_CONTEXT_TOKENS, RuntimeLimits.CONTEXT_TOKEN_OPTIONS),
    RAM_PERCENT("llm_ram_percent", LlmModelCatalog.DEFAULT_RAM_PERCENT, (30..90 step 5).toList())
}

/** Mirrors `DetectionThresholdStore`: save refuses anything not offered, load falls back to the default. */
class ResourceLimitsStore(private val prefs: SharedPreferences) {

    fun load(limit: ResourceLimit): Int =
        runCatching { prefs.getInt(limit.key, limit.default) }.getOrNull()?.takeIf { it in limit.options } ?: limit.default

    fun save(limit: ResourceLimit, value: Int): Boolean {
        if (value !in limit.options) return false
        prefs.edit().putInt(limit.key, value).apply()
        return true
    }

    fun reset() {
        prefs.edit().apply { ResourceLimit.entries.forEach { remove(it.key) } }.apply()
    }

    fun runtime(): RuntimeLimits = RuntimeLimits(load(ResourceLimit.THREADS), load(ResourceLimit.CONTEXT_TOKENS))
}

package com.yomu.app.translation

enum class TranslationEngineType(val id: String, val label: String, val description: String) {
    ML_KIT("ml_kit", "ML Kit", "Fast on-device baseline (Google). Temporary — quality not final."),
    OPUS_MT("opus_mt", "OPUS-MT", "Local-first JA→EN. ~115MB. Good quality, no telemetry."),
    LLM("llm", "Local LLM", "On-device JA→EN. Pick a curated model below; default Qwen2.5-1.5B.");

    companion object {
        fun fromId(id: String): TranslationEngineType =
            entries.firstOrNull { it.id == id } ?: ML_KIT
    }
}

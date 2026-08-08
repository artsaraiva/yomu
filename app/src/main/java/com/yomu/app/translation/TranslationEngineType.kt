package com.yomu.app.translation

enum class TranslationEngineType(val id: String, val label: String, val description: String) {
    ML_KIT("ml_kit", "ML Kit", "Fast on-device baseline (Google). Temporary — quality not final."),
    OPUS_MT("opus_mt", "OPUS-MT", "Local-first JA→EN. ~115MB. Good quality, no telemetry."),
    LLM("llm", "Local LLM (CAT-Translate)", "On-device JA→EN translation model. 0.8B, MIT-licensed, purpose-built for translation.");

    companion object {
        fun fromId(id: String): TranslationEngineType =
            entries.firstOrNull { it.id == id } ?: ML_KIT
    }
}

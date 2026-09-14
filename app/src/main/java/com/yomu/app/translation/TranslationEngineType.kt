package com.yomu.app.translation

import androidx.annotation.StringRes
import com.yomu.app.R

/**
 * Whether an engine can take the page-level call the context architecture needs.
 *
 * A [GATE] engine takes the page-level call; a [FLOOR] engine can only be asked one bubble at a
 * time, so it never runs that architecture (CONTEXT.md, "Floor engine").
 */
enum class EngineRole { GATE, FLOOR }

enum class TranslationEngineType(
    val id: String,
    val role: EngineRole,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int
) {
    ML_KIT("ml_kit", EngineRole.FLOOR, R.string.engine_ml_kit_label, R.string.engine_ml_kit_description),
    OPUS_MT("opus_mt", EngineRole.FLOOR, R.string.engine_opus_mt_label, R.string.engine_opus_mt_description),
    LLM("llm", EngineRole.GATE, R.string.engine_llm_label, R.string.engine_llm_description);

    companion object {
        fun fromId(id: String): TranslationEngineType =
            entries.firstOrNull { it.id == id } ?: ML_KIT
    }
}

package com.yomu.app.db

import com.yomu.app.db.entities.TranslationCacheEntity
import com.yomu.ml.TranslationOutput
import com.yomu.pipeline.translation.buildCacheKey
import com.yomu.pipeline.translation.normalizeForCache

class TranslationCacheRepository(
    private val dao: TranslationCacheDao
) : com.yomu.pipeline.translation.TranslationCacheRepository {

    override suspend fun get(engineId: String, modelId: String?, sourceText: String): TranslationOutput? {
        val key = buildCacheKey(engineId, modelId, sourceText)
        val entity = dao.get(key) ?: return null
        return TranslationOutput(
            translatedText = entity.translatedText,
            confidence = entity.confidence,
            durationMs = 0L
        )
    }

    override suspend fun put(engineId: String, modelId: String?, sourceText: String, output: TranslationOutput) {
        val normalized = normalizeForCache(sourceText)
        val entity = TranslationCacheEntity(
            cacheKey = buildCacheKey(engineId, modelId, sourceText),
            engineId = engineId,
            modelId = modelId,
            normalizedSourceText = normalized,
            translatedText = output.translatedText,
            confidence = output.confidence,
            createdAt = System.currentTimeMillis()
        )
        dao.put(entity)
    }
}

package com.yomu.app.di

import android.content.Context
import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.ml.LlamaBridge
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.OnnxRuntime
import com.yomu.ml.TranslationBridge
import com.yomu.ml.opusmt.OpusMtTranslationBridge
import com.yomu.app.translation.MlKitTranslationBridge
import com.yomu.app.translation.TranslationEngineSelector
import com.yomu.pipeline.TranslationPipeline
import com.yomu.pipeline.bubble.BubbleDetector
import com.yomu.pipeline.context.ContextAssembler
import com.yomu.pipeline.ocr.OcrEngine
import com.yomu.pipeline.translation.TranslationEngine
import com.yomu.pipeline.typesetting.Typesetter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import com.yomu.app.translation.LlmModelCatalog
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PipelineModule {

    @Provides
    @Singleton
    fun provideOnnxRuntime(@ApplicationContext context: Context): OnnxRuntime {
        return OnnxRuntime(context)
    }

    @Provides
    @Singleton
    fun provideBubbleDetector(onnxRuntime: OnnxRuntime): BubbleDetector {
        return BubbleDetector(onnxRuntime)
    }

    @Provides
    @Singleton
    fun provideOcrEngine(onnxRuntime: OnnxRuntime): OcrEngine {
        return OcrEngine(onnxRuntime)
    }

    @Provides
    @Singleton
    fun provideContextAssembler(): ContextAssembler {
        return ContextAssembler()
    }

    @Provides
    @Singleton
    fun provideLlamaBridge(@ApplicationContext context: Context): LlamaBridge {
        return LlamaBridge(context)
    }

    // filesDir/models/llm — where curated translation GGUFs are staged (#90).
    private fun llmModelsDir(context: Context): File =
        File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}")

    @Provides
    @Singleton
    fun provideLlamaTranslationBridge(
        llamaBridge: LlamaBridge,
        @ApplicationContext context: Context,
        sharedPreferences: SharedPreferences
    ): LlamaTranslationBridge {
        // Initial model is the persisted LLM choice, or the ADR-0010 default (Qwen2.5-1.5B) when
        // nothing is picked. Runtime-selected from here on via TranslationEngineSelector (#90 part A).
        val selected = LlmModelCatalog.selectedOrDefault(
            sharedPreferences.getString(Constants.PREF_LLM_MODEL, null)
        )
        val modelPath = File(llmModelsDir(context), selected.ggufFileName).absolutePath
        return LlamaTranslationBridge(llamaBridge, modelPath, selected.idKeyedBatch)
    }

    @Provides
    @Singleton
    fun provideTranslationEngineSelector(
        mlKitBridge: MlKitTranslationBridge,
        opusMtBridge: OpusMtTranslationBridge,
        llamaBridge: LlamaTranslationBridge,
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context
    ): TranslationEngineSelector {
        return TranslationEngineSelector(
            mlKitBridge,
            opusMtBridge,
            llamaBridge,
            sharedPreferences,
            llmModelsDir(context)
        )
    }

    @Provides
    @Singleton
    fun provideTranslationBridge(selector: TranslationEngineSelector): TranslationBridge = selector

    @Provides
    @Singleton
    fun provideTranslationEngine(selector: TranslationEngineSelector): TranslationEngine {
        // ponytail: engineId is fixed at DI construction; cache keys won't change mid-session
        // if the user switches engines. Acceptable for Phase 1.
        // modelId is left null on purpose (#90 A.3): the persistent cache is read/written only on the
        // per-line ML Kit/OPUS path (translateBubbles). Both LLM paths (translateBatch/translatePerLine)
        // bypass the cache entirely, so switching curated LLMs can never serve another model's cached
        // lines. buildCacheKey already takes modelId — thread the selected LLM id here if the LLM path
        // ever starts caching.
        return TranslationEngine(selector, selector.engineId)
    }

    @Provides
    @Singleton
    fun provideTypesetter(): Typesetter {
        return Typesetter()
    }

    @Provides
    @Singleton
    fun provideTranslationPipeline(
        bubbleDetector: BubbleDetector,
        ocrEngine: OcrEngine,
        contextAssembler: ContextAssembler,
        translationEngine: TranslationEngine,
        typesetter: Typesetter
    ): TranslationPipeline {
        return TranslationPipeline(
            bubbleDetector,
            ocrEngine,
            contextAssembler,
            translationEngine,
            typesetter
        )
    }
}

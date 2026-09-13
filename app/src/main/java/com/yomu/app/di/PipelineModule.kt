package com.yomu.app.di

import android.content.Context
import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.ml.LlamaBridge
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.OnnxRuntime
import com.yomu.ml.opusmt.OpusMtTranslationBridge
import com.yomu.app.translation.EngineSelection
import com.yomu.app.translation.MlKitTranslationBridge
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
import com.yomu.app.translation.GenerationProfileStore
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.storedLlmModelId
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
        val selected = LlmModelCatalog.selectedOrDefault(sharedPreferences.storedLlmModelId())
        val generation = GenerationProfileStore(sharedPreferences).load().params
        return LlamaTranslationBridge(llamaBridge, LlmModelCatalog.profileFor(selected, llmModelsDir(context), generation))
    }

    @Provides
    @Singleton
    fun provideEngineSelection(
        mlKitBridge: MlKitTranslationBridge,
        opusMtBridge: OpusMtTranslationBridge,
        llamaBridge: LlamaTranslationBridge,
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context
    ): EngineSelection {
        return EngineSelection(
            mlKitBridge,
            opusMtBridge,
            llamaBridge,
            sharedPreferences,
            llmModelsDir(context)
        )
    }

    @Provides
    @Singleton
    fun provideTranslationEngine(selection: EngineSelection): TranslationEngine {
        return TranslationEngine(selection::current, selection::close)
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

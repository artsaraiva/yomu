package com.yomu.app.di

import android.content.Context
import com.yomu.core.Constants
import com.yomu.ml.LlamaBridge
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.OnnxRuntime
import com.yomu.ml.TranslationBridge
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
import javax.inject.Named
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

    @Provides
    @Singleton
    @Named("llama_model_path")
    fun provideLlamaModelPath(@ApplicationContext context: Context): String {
        return File(
            context.filesDir,
            "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}/${Constants.TRANSLATION_MODEL_4BIT}"
        ).absolutePath
    }

    @Provides
    @Singleton
    fun provideLlamaTranslationBridge(
        llamaBridge: LlamaBridge,
        @Named("llama_model_path") modelPath: String
    ): LlamaTranslationBridge {
        return LlamaTranslationBridge(llamaBridge, modelPath)
    }

    @Provides
    @Singleton
    fun provideTranslationBridge(): TranslationBridge {
        return MlKitTranslationBridge()
    }

    // To enable local LLM translation, replace provideTranslationBridge with:
    // @Provides
    // @Singleton
    // fun provideTranslationBridge(
    //     llamaTranslationBridge: LlamaTranslationBridge
    // ): TranslationBridge = llamaTranslationBridge

    @Provides
    @Singleton
    fun provideTranslationEngine(translationBridge: TranslationBridge): TranslationEngine {
        return TranslationEngine(translationBridge)
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

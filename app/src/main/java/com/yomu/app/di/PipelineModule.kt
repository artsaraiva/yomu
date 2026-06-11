package com.yomu.app.di

import android.content.Context
import com.yomu.ml.LlamaBridge
import com.yomu.ml.OnnxRuntime
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
    fun provideLlamaBridge(@ApplicationContext context: Context): LlamaBridge {
        return LlamaBridge(context)
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
    fun provideTranslationEngine(llamaBridge: LlamaBridge): TranslationEngine {
        return TranslationEngine(llamaBridge)
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

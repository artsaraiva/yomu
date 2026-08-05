package com.yomu.app.di

import android.content.Context
import com.yomu.core.Constants
import com.yomu.ml.OnnxRuntime
import com.yomu.ml.opusmt.OpusMtTranslator
import com.yomu.ml.opusmt.OpusMtTranslationBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OpusMtModule {

    private fun modelDir(context: Context): File {
        return File(
            context.filesDir,
            "${Constants.MODELS_DIR}/${Constants.TRANSLATION_MODELS_DIR}"
        )
    }

    @Provides
    @Singleton
    fun provideOpusMtTranslator(
        onnxRuntime: OnnxRuntime,
        @ApplicationContext context: Context
    ): OpusMtTranslator {
        val dir = modelDir(context)
        return OpusMtTranslator(
            onnxRuntime,
            File(dir, Constants.OPUS_MT_ENCODER_MODEL).absolutePath,
            File(dir, Constants.OPUS_MT_DECODER_MODEL).absolutePath,
            File(dir, Constants.OPUS_MT_TOKENIZER).absolutePath
        )
    }

    @Provides
    @Singleton
    fun provideOpusMtTranslationBridge(
        onnxRuntime: OnnxRuntime,
        @ApplicationContext context: Context
    ): OpusMtTranslationBridge {
        val dir = modelDir(context)
        return OpusMtTranslationBridge(
            onnxRuntime,
            File(dir, Constants.OPUS_MT_ENCODER_MODEL).absolutePath,
            File(dir, Constants.OPUS_MT_DECODER_MODEL).absolutePath,
            File(dir, Constants.OPUS_MT_TOKENIZER).absolutePath
        )
    }
}

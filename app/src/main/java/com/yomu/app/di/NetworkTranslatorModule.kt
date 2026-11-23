package com.yomu.app.di

import com.yomu.ml.ServerModelList
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** The network translator and the Providers screen's Connect (ADR-0023). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkTranslatorModule {

    @Provides
    @Singleton
    fun provideServerModelList(client: OkHttpClient): ServerModelList = ServerModelList(client)
}

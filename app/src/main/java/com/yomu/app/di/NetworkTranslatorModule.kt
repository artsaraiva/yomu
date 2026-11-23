package com.yomu.app.di

import android.content.SharedPreferences
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.app.translation.Translator
import com.yomu.ml.ServerModelList
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** The network translator and the Providers screen's Connect (ADR-0023). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkTranslatorModule {

    /** Registered beside the on-device translator; nothing chooses it until the reader can (#490). */
    @Provides
    @Singleton
    @IntoSet
    fun provideNetworkTranslator(store: ServerSettingsStore, prefs: SharedPreferences, client: OkHttpClient): Translator =
        Translator.network(store, prefs, client)

    @Provides
    @Singleton
    fun provideServerModelList(client: OkHttpClient): ServerModelList = ServerModelList(client)
}

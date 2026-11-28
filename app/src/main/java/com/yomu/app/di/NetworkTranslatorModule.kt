package com.yomu.app.di

import android.content.SharedPreferences
import androidx.hilt.navigation.compose.hiltViewModel
import com.yomu.app.translation.NetworkTranslatorEntries
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.app.translation.Translator
import com.yomu.app.ui.navigation.TranslatorScreen
import com.yomu.app.ui.settings.ServerSetupScreen
import com.yomu.ml.ServerModelList
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** The network translator, its server setup screen and that screen's Connect (ADR-0023). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkTranslatorModule {

    /** Registered beside the on-device translator; nothing chooses it until the reader can (#490). */
    @Provides
    @Singleton
    @IntoSet
    fun provideNetworkTranslator(store: ServerSettingsStore, prefs: SharedPreferences, client: OkHttpClient): Translator =
        Translator.network(store, prefs, client)

    /**
     * The server's setup, which the network translator's picker entry, Home action and failures open. Setup shows
     * it as it is; inside Settings the app shows the Translator page's server side in its place (#548).
     */
    @Provides
    @IntoSet
    fun provideServerSetupScreen(): TranslatorScreen =
        TranslatorScreen(NetworkTranslatorEntries.PROVIDERS) { onBack -> ServerSetupScreen(hiltViewModel(), onBack) }

    @Provides
    @Singleton
    fun provideServerModelList(client: OkHttpClient): ServerModelList = ServerModelList(client)
}

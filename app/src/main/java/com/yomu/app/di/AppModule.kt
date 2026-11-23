package com.yomu.app.di

import android.content.Context
import android.content.SharedPreferences
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.app.translation.androidKeystoreKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("yomu_prefs", Context.MODE_PRIVATE)
    }

    /** One per process: a key the Keystore would not seal is held in this instance until the app stops. */
    @Provides
    @Singleton
    fun provideServerSettingsStore(
        @ApplicationContext context: Context,
        prefs: SharedPreferences
    ): ServerSettingsStore {
        val keyPrefs = context.getSharedPreferences(ServerSettingsStore.KEY_FILE, Context.MODE_PRIVATE)
        return ServerSettingsStore(prefs = prefs, keyPrefs = keyPrefs) { androidKeystoreKey() }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

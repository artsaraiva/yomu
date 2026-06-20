package com.yomu.app.di

import android.content.Context
import androidx.room.Room
import com.yomu.app.db.AppDatabase
import com.yomu.app.db.HistoryDao
import com.yomu.app.db.ModelDao
import com.yomu.app.db.TranslationSessionDao
import com.yomu.core.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideModelDao(database: AppDatabase): ModelDao {
        return database.modelDao()
    }

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    fun provideTranslationSessionDao(database: AppDatabase): TranslationSessionDao {
        return database.translationSessionDao()
    }
}

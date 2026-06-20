package com.yomu.app.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.db.entities.TranslationSessionEntity

@Database(
    entities = [ModelEntity::class, TranslationEntity::class, TranslationSessionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun historyDao(): HistoryDao
    abstract fun translationSessionDao(): TranslationSessionDao
}

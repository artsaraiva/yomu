package com.yomu.app.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.TranslationCacheEntity
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.db.entities.TranslationSessionEntity

// TranslationCacheEntity is retained after the cache deletion (#125, ADR-0002): dropping an entity
// forces version = 4, and this database builds with fallbackToDestructiveMigration(), which would
// wipe real translation history to remove a table that has never held a row.
@Database(
    entities = [
        ModelEntity::class,
        TranslationEntity::class,
        TranslationSessionEntity::class,
        TranslationCacheEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun historyDao(): HistoryDao
    abstract fun translationSessionDao(): TranslationSessionDao
}

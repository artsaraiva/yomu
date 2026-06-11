package com.yomu.app.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.TranslationEntity

@Database(
    entities = [ModelEntity::class, TranslationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun historyDao(): HistoryDao
}

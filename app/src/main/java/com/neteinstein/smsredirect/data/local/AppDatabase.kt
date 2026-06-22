package com.neteinstein.smsredirect.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RuleEntity::class, AppSettingsEntity::class, ForwardHistoryEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    abstract fun appSettingsDao(): AppSettingsDao

    abstract fun forwardHistoryDao(): ForwardHistoryDao
}

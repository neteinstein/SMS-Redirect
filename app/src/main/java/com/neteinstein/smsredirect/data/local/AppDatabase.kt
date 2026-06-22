package com.neteinstein.smsredirect.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RuleEntity::class, AppSettingsEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun appSettingsDao(): AppSettingsDao
}

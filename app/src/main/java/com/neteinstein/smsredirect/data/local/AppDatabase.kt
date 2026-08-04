package com.neteinstein.smsredirect.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RuleEntity::class,
        AppSettingsEntity::class,
        ForwardHistoryEntity::class,
        ScheduledSmsEntity::class,
        ScheduledSmsHistoryEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    abstract fun appSettingsDao(): AppSettingsDao

    abstract fun forwardHistoryDao(): ForwardHistoryDao

    abstract fun scheduledSmsDao(): ScheduledSmsDao

    abstract fun scheduledSmsHistoryDao(): ScheduledSmsHistoryDao

    companion object {
        /**
         * Only the `showNotifications` column's SQL default changed (true -> false); every
         * other column keeps its previous default. Rebuilds the table instead of relying on
         * destructive fallback so existing rows (e.g. a user's keepHistory/keepScheduleHistory
         * opt-outs) survive the upgrade.
         */
        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS app_settings_new (
                            id INTEGER NOT NULL,
                            sendWhileRoaming INTEGER NOT NULL DEFAULT 0,
                            keepHistory INTEGER NOT NULL DEFAULT 1,
                            defaultPrefix TEXT NOT NULL DEFAULT '+351',
                            forwardPrefix TEXT NOT NULL DEFAULT 'SMS Redirect:',
                            showNotifications INTEGER NOT NULL DEFAULT 0,
                            keepScheduleHistory INTEGER NOT NULL DEFAULT 1,
                            PRIMARY KEY(id)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO app_settings_new
                            (id, sendWhileRoaming, keepHistory, defaultPrefix, forwardPrefix, showNotifications, keepScheduleHistory)
                        SELECT id, sendWhileRoaming, keepHistory, defaultPrefix, forwardPrefix, showNotifications, keepScheduleHistory
                        FROM app_settings
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE app_settings")
                    db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
                }
            }
    }
}

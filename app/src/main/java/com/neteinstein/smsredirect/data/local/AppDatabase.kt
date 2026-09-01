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
        BackupSettingsEntity::class,
        SmsBackupStateEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    abstract fun appSettingsDao(): AppSettingsDao

    abstract fun forwardHistoryDao(): ForwardHistoryDao

    abstract fun scheduledSmsDao(): ScheduledSmsDao

    abstract fun scheduledSmsHistoryDao(): ScheduledSmsHistoryDao

    abstract fun backupSettingsDao(): BackupSettingsDao

    abstract fun smsBackupStateDao(): SmsBackupStateDao

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

        /**
         * Adds the Gmail SMS backup feature and the landing-screen preference.
         *
         * Two new tables, plus a rebuild of `app_settings` -- the rebuild exists only to move the
         * `forwardPrefix` SQL default from 'SMS Redirect:' to 'SMS Toolbox:' after the rename.
         * SQLite cannot alter a column default in place, and Room compares the entity's declared
         * `@ColumnInfo(defaultValue = ...)` against the real schema, so an ALTER would not do.
         * Existing rows keep whatever prefix the user actually has -- only the default moves.
         * `landingScreen` rides along on that same rebuild: it isn't listed in the INSERT...SELECT
         * column lists below, so every existing row picks up the new column's SQL default.
         *
         * `MIGRATION_8_9` above is deliberately left alone: it reconstructs the historical v9
         * schema and must keep the old default to stay a faithful 8 -> 9 step.
         */
        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS app_settings_v10 (
                            id INTEGER NOT NULL,
                            sendWhileRoaming INTEGER NOT NULL DEFAULT 0,
                            keepHistory INTEGER NOT NULL DEFAULT 1,
                            defaultPrefix TEXT NOT NULL DEFAULT '+351',
                            forwardPrefix TEXT NOT NULL DEFAULT 'SMS Toolbox:',
                            showNotifications INTEGER NOT NULL DEFAULT 0,
                            keepScheduleHistory INTEGER NOT NULL DEFAULT 1,
                            landingScreen TEXT NOT NULL DEFAULT 'BACKUP',
                            PRIMARY KEY(id)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO app_settings_v10
                            (id, sendWhileRoaming, keepHistory, defaultPrefix, forwardPrefix, showNotifications, keepScheduleHistory)
                        SELECT id, sendWhileRoaming, keepHistory, defaultPrefix, forwardPrefix, showNotifications, keepScheduleHistory
                        FROM app_settings
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE app_settings")
                    db.execSQL("ALTER TABLE app_settings_v10 RENAME TO app_settings")

                    // No row is seeded here. The columns carry no SQL defaults (see
                    // BackupSettingsEntity), and SmsBackupSettingsRepositoryImpl already treats a
                    // missing row as "all defaults".
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS backup_settings (
                            id INTEGER NOT NULL,
                            gmailAddress TEXT NOT NULL,
                            gmailAppPasswordCipher TEXT NOT NULL,
                            subjectTemplate TEXT NOT NULL,
                            gmailLabel TEXT NOT NULL,
                            backupPreviousEnabled INTEGER NOT NULL,
                            backupEnabled INTEGER NOT NULL,
                            backupStartedAtMillis INTEGER NOT NULL,
                            PRIMARY KEY(id)
                        )
                        """.trimIndent(),
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS sms_backup_state (
                            stableKey TEXT NOT NULL,
                            backedUpAtMillis INTEGER NOT NULL,
                            PRIMARY KEY(stableKey)
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}

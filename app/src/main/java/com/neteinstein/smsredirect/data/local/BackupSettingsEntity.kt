package com.neteinstein.smsredirect.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row configuration for the Gmail SMS backup feature, following the same `id = 1`
 * pattern as [AppSettingsEntity].
 *
 * Kept in its own table rather than as extra columns on `app_settings` so the v10 migration is
 * a plain CREATE TABLE, and so [com.neteinstein.smsredirect.domain.repository.SettingsRepository]
 * does not grow another two dozen flat accessors.
 *
 * No column carries a SQL default. The real defaults (subject template, "SMS" label) are applied
 * in the repository, mirroring the existing `?: "+351"` fallback style, and the repository already
 * handles a missing row -- so nothing needs seeding.
 *
 * Declaring no defaults is deliberate: Room compares the migration's DDL against the schema it
 * generates from these annotations, and a mismatch crashes every upgrading user on open. Omitting
 * `@ColumnInfo(defaultValue = ...)` removes any question of how Room quotes a default containing
 * `$`, parentheses or an empty string.
 */
@Entity(tableName = "backup_settings")
data class BackupSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val gmailAddress: String = "",
    /**
     * The Gmail app password encrypted by `SecretCipher`. Never store or log the plaintext, and
     * never add this column to `ExportBackupUseCase` -- that JSON leaves the device via a share
     * sheet.
     */
    val gmailAppPasswordCipher: String = "",
    /** Blank means "use the domain default". */
    val subjectTemplate: String = "",
    /** Blank means "use the domain default" ("SMS"). */
    val gmailLabel: String = "",
    val backupPreviousEnabled: Boolean = false,
    val backupEnabled: Boolean = false,
    /**
     * Watermark used when [backupPreviousEnabled] is false: only messages at or after this
     * timestamp are eligible. Zero means no watermark.
     */
    val backupStartedAtMillis: Long = 0L,
)

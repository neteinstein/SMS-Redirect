package com.neteinstein.smsredirect.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per SMS that has been successfully appended to Gmail.
 *
 * The primary key is a synthesized stable key (see `BackupSmsMessage.stableKey`) rather than
 * the `content://sms` `_id`: SQLite reuses provider ids after a delete, and they do not survive
 * a device restore, so an id-keyed table would eventually skip or duplicate messages.
 *
 * Only the key is stored, never the message body -- the key is a one-way hash.
 */
@Entity(tableName = "sms_backup_state")
data class SmsBackupStateEntity(
    @PrimaryKey val stableKey: String,
    val backedUpAtMillis: Long,
)

package com.neteinstein.smsredirect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SmsBackupStateDao {
    /**
     * Batch membership check. Callers filter a whole chunk of candidate messages with one query
     * instead of one query per message, which matters during a multi-thousand-message backfill.
     */
    @Query("SELECT stableKey FROM sms_backup_state WHERE stableKey IN (:stableKeys)")
    suspend fun existingKeys(stableKeys: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SmsBackupStateEntity>)

    @Query("SELECT COUNT(*) FROM sms_backup_state")
    suspend fun count(): Int

    @Query("DELETE FROM sms_backup_state")
    suspend fun clear()
}

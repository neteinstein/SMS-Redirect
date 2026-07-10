package com.neteinstein.smsredirect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledSmsHistoryDao {
    @Query("SELECT * FROM scheduled_sms_history ORDER BY sentAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScheduledSmsHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ScheduledSmsHistoryEntity)

    @Query(
        "DELETE FROM scheduled_sms_history " +
            "WHERE id NOT IN (" +
            "SELECT id FROM scheduled_sms_history ORDER BY sentAtEpochMs DESC LIMIT :limit" +
            ")",
    )
    suspend fun trimToLatest(limit: Int)

    @Query("DELETE FROM scheduled_sms_history")
    suspend fun deleteAll()
}

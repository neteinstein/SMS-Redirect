package com.neteinstein.smsredirect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardHistoryDao {
    @Query("SELECT * FROM forward_history ORDER BY forwardedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ForwardHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ForwardHistoryEntity)

    @Query(
        "DELETE FROM forward_history " +
            "WHERE id NOT IN (" +
            "SELECT id FROM forward_history ORDER BY forwardedAtEpochMs DESC LIMIT :limit" +
            ")",
    )
    suspend fun trimToLatest(limit: Int)

    @Query("DELETE FROM forward_history")
    suspend fun deleteAll()
}

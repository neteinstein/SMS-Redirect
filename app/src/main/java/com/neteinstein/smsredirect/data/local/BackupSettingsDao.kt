package com.neteinstein.smsredirect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupSettingsDao {
    @Query("SELECT * FROM backup_settings WHERE id = 1")
    fun observeSettings(): Flow<BackupSettingsEntity?>

    @Query("SELECT * FROM backup_settings WHERE id = 1")
    suspend fun getSettings(): BackupSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: BackupSettingsEntity)
}

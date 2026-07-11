package com.neteinstein.smsredirect.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledSmsDao {
    @Query("SELECT * FROM scheduled_sms ORDER BY sortOrder ASC, id DESC")
    fun getScheduledSmsList(): Flow<List<ScheduledSmsEntity>>

    @Query("SELECT * FROM scheduled_sms WHERE id = :id")
    suspend fun getScheduledSms(id: Long): ScheduledSmsEntity?

    @Query("SELECT * FROM scheduled_sms ORDER BY sortOrder ASC, id DESC")
    suspend fun getScheduledSmsSnapshot(): List<ScheduledSmsEntity>

    @Query("SELECT COUNT(*) FROM scheduled_sms")
    suspend fun getScheduledSmsCount(): Int

    @Query("SELECT DISTINCT category FROM scheduled_sms WHERE category != '' ORDER BY category ASC")
    fun getCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scheduledSms: ScheduledSmsEntity): Long

    @Update
    suspend fun update(scheduledSms: ScheduledSmsEntity)

    @Update
    suspend fun updateAll(scheduledSmsList: List<ScheduledSmsEntity>)

    @Delete
    suspend fun delete(scheduledSms: ScheduledSmsEntity)
}

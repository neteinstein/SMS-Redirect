package com.neteinstein.smsredirect.domain.repository

import com.neteinstein.smsredirect.domain.model.ScheduledSms
import kotlinx.coroutines.flow.Flow

interface ScheduledSmsRepository {
    fun getScheduledSmsList(): Flow<List<ScheduledSms>>

    suspend fun getScheduledSms(id: Long): ScheduledSms?

    suspend fun getScheduledSmsSnapshot(): List<ScheduledSms>

    suspend fun getScheduledSmsCount(): Int

    fun getCategories(): Flow<List<String>>

    /** Inserts [scheduledSms] and returns the generated row id. */
    suspend fun insert(scheduledSms: ScheduledSms): Long

    suspend fun update(scheduledSms: ScheduledSms)

    suspend fun updateAll(scheduledSmsList: List<ScheduledSms>)

    suspend fun delete(scheduledSms: ScheduledSms)
}

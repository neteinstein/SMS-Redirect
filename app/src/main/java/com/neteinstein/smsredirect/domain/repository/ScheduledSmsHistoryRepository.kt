package com.neteinstein.smsredirect.domain.repository

import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory
import kotlinx.coroutines.flow.Flow

interface ScheduledSmsHistoryRepository {
    fun observeRecent(limit: Int = 50): Flow<List<ScheduledSmsHistory>>

    suspend fun insert(entry: ScheduledSmsHistory)

    suspend fun trimToLatest(limit: Int = 50)

    suspend fun deleteAll()
}

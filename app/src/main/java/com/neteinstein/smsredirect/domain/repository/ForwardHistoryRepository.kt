package com.neteinstein.smsredirect.domain.repository

import com.neteinstein.smsredirect.domain.model.ForwardHistory
import kotlinx.coroutines.flow.Flow

interface ForwardHistoryRepository {
    fun observeRecent(limit: Int = 50): Flow<List<ForwardHistory>>
    suspend fun insert(entry: ForwardHistory)
    suspend fun trimToLatest(limit: Int = 50)
}

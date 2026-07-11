package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.ScheduledSmsHistoryDao
import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScheduledSmsHistoryRepositoryImpl(
    private val dao: ScheduledSmsHistoryDao,
) : ScheduledSmsHistoryRepository {
    override fun observeRecent(limit: Int): Flow<List<ScheduledSmsHistory>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun insert(entry: ScheduledSmsHistory) {
        dao.insert(entry.toEntity())
    }

    override suspend fun trimToLatest(limit: Int) {
        dao.trimToLatest(limit)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}

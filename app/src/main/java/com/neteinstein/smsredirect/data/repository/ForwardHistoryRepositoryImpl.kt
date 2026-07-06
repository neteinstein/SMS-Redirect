package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.ForwardHistoryDao
import com.neteinstein.smsredirect.domain.model.ForwardHistory
import com.neteinstein.smsredirect.domain.repository.ForwardHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ForwardHistoryRepositoryImpl(
    private val dao: ForwardHistoryDao,
) : ForwardHistoryRepository {
    override fun observeRecent(limit: Int): Flow<List<ForwardHistory>> = dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun insert(entry: ForwardHistory) {
        dao.insert(entry.toEntity())
    }

    override suspend fun trimToLatest(limit: Int) {
        dao.trimToLatest(limit)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}

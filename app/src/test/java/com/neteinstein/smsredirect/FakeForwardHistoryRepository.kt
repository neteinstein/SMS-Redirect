package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.ForwardHistory
import com.neteinstein.smsredirect.domain.repository.ForwardHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeForwardHistoryRepository : ForwardHistoryRepository {
    private val entries = MutableStateFlow<List<ForwardHistory>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<ForwardHistory>> =
        entries.map { it.sortedByDescending { e -> e.forwardedAtEpochMs }.take(limit) }

    override suspend fun insert(entry: ForwardHistory) {
        val nextId = (entries.value.maxOfOrNull { it.id } ?: 0L) + 1L
        entries.value = entries.value + entry.copy(id = nextId)
    }

    override suspend fun trimToLatest(limit: Int) {
        entries.value =
            entries.value
                .sortedByDescending { it.forwardedAtEpochMs }
                .take(limit)
    }

    override suspend fun deleteAll() {
        entries.value = emptyList()
    }

    fun getAll(): List<ForwardHistory> = entries.value
}

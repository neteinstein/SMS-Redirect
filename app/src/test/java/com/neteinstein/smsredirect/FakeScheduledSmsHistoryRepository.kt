package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeScheduledSmsHistoryRepository : ScheduledSmsHistoryRepository {
    private val entries = MutableStateFlow<List<ScheduledSmsHistory>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<ScheduledSmsHistory>> =
        entries.map { it.sortedByDescending { e -> e.sentAtEpochMs }.take(limit) }

    override suspend fun insert(entry: ScheduledSmsHistory) {
        val nextId = (entries.value.maxOfOrNull { it.id } ?: 0L) + 1L
        entries.value = entries.value + entry.copy(id = nextId)
    }

    override suspend fun trimToLatest(limit: Int) {
        entries.value =
            entries.value
                .sortedByDescending { it.sentAtEpochMs }
                .take(limit)
    }

    override suspend fun deleteAll() {
        entries.value = emptyList()
    }

    fun getAll(): List<ScheduledSmsHistory> = entries.value
}

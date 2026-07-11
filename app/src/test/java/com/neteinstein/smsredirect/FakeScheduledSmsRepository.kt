package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeScheduledSmsRepository(
    initial: List<ScheduledSms> = emptyList(),
) : ScheduledSmsRepository {
    private val scheduledSmsList = MutableStateFlow(initial)

    override fun getScheduledSmsList(): Flow<List<ScheduledSms>> = scheduledSmsList

    override suspend fun getScheduledSms(id: Long): ScheduledSms? = scheduledSmsList.value.firstOrNull { it.id == id }

    override suspend fun getScheduledSmsSnapshot(): List<ScheduledSms> = scheduledSmsList.value

    override suspend fun getScheduledSmsCount(): Int = scheduledSmsList.value.size

    override fun getCategories(): Flow<List<String>> =
        scheduledSmsList.map { list ->
            list
                .map { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }

    override suspend fun insert(scheduledSms: ScheduledSms): Long {
        val nextId = (scheduledSmsList.value.maxOfOrNull { it.id } ?: 0L) + 1L
        scheduledSmsList.update { listOf(scheduledSms.copy(id = nextId)) + it }
        return nextId
    }

    override suspend fun update(scheduledSms: ScheduledSms) {
        scheduledSmsList.update { list -> list.map { if (it.id == scheduledSms.id) scheduledSms else it } }
    }

    override suspend fun updateAll(scheduledSmsList: List<ScheduledSms>) {
        this.scheduledSmsList.value = scheduledSmsList
    }

    override suspend fun delete(scheduledSms: ScheduledSms) {
        scheduledSmsList.update { list -> list.filterNot { it.id == scheduledSms.id } }
    }
}

package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.ScheduledSmsDao
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScheduledSmsRepositoryImpl(
    private val dao: ScheduledSmsDao,
) : ScheduledSmsRepository {
    override fun getScheduledSmsList(): Flow<List<ScheduledSms>> = dao.getScheduledSmsList().map { list -> list.map { it.toDomain() } }

    override suspend fun getScheduledSms(id: Long): ScheduledSms? = dao.getScheduledSms(id)?.toDomain()

    override suspend fun getScheduledSmsSnapshot(): List<ScheduledSms> = dao.getScheduledSmsSnapshot().map { it.toDomain() }

    override suspend fun getScheduledSmsCount(): Int = dao.getScheduledSmsCount()

    override fun getCategories(): Flow<List<String>> = dao.getCategories()

    override suspend fun insert(scheduledSms: ScheduledSms): Long = dao.insert(scheduledSms.toEntity())

    override suspend fun update(scheduledSms: ScheduledSms) {
        dao.update(scheduledSms.toEntity())
    }

    override suspend fun updateAll(scheduledSmsList: List<ScheduledSms>) {
        dao.updateAll(scheduledSmsList.map { it.toEntity() })
    }

    override suspend fun delete(scheduledSms: ScheduledSms) {
        dao.delete(scheduledSms.toEntity())
    }
}

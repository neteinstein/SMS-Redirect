package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.AppSettingsDao
import com.neteinstein.smsredirect.data.local.AppSettingsEntity
import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val dao: AppSettingsDao
) : SettingsRepository {
    override fun observeSendWhileRoaming(): Flow<Boolean> =
        dao.observeSettings().map { it?.sendWhileRoaming ?: false }

    override suspend fun getSendWhileRoaming(): Boolean = dao.getSettings()?.sendWhileRoaming ?: false

    override suspend fun setSendWhileRoaming(enabled: Boolean) {
        dao.upsert(AppSettingsEntity(sendWhileRoaming = enabled))
    }
}

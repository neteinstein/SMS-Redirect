package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.AppSettingsDao
import com.neteinstein.smsredirect.data.local.AppSettingsEntity
import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val dao: AppSettingsDao,
) : SettingsRepository {
    override fun observeSendWhileRoaming(): Flow<Boolean> = dao.observeSettings().map { it?.sendWhileRoaming ?: false }

    override suspend fun getSendWhileRoaming(): Boolean = dao.getSettings()?.sendWhileRoaming ?: false

    override suspend fun setSendWhileRoaming(enabled: Boolean) {
        val current = dao.getSettings() ?: AppSettingsEntity()
        dao.upsert(current.copy(sendWhileRoaming = enabled))
    }

    override fun observeKeepHistory(): Flow<Boolean> = dao.observeSettings().map { it?.keepHistory ?: true }

    override suspend fun getKeepHistory(): Boolean = dao.getSettings()?.keepHistory ?: true

    override suspend fun setKeepHistory(enabled: Boolean) {
        val current = dao.getSettings() ?: AppSettingsEntity()
        dao.upsert(current.copy(keepHistory = enabled))
    }

    override fun observeDefaultPrefix(): Flow<String> = dao.observeSettings().map { it?.defaultPrefix ?: "+351" }

    override suspend fun getDefaultPrefix(): String = dao.getSettings()?.defaultPrefix ?: "+351"

    override suspend fun setDefaultPrefix(prefix: String) {
        val current = dao.getSettings() ?: AppSettingsEntity()
        dao.upsert(current.copy(defaultPrefix = prefix))
    }
}

package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.AppSettingsDao
import com.neteinstein.smsredirect.data.local.AppSettingsEntity
import com.neteinstein.smsredirect.domain.model.LandingScreen
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

    override fun observeForwardPrefix(): Flow<String> = dao.observeSettings().map { it?.forwardPrefix ?: "SMS Toolbox:" }

    override suspend fun getForwardPrefix(): String = dao.getSettings()?.forwardPrefix ?: "SMS Toolbox:"

    override suspend fun setForwardPrefix(prefix: String) {
        val current = dao.getSettings() ?: AppSettingsEntity()
        dao.upsert(current.copy(forwardPrefix = prefix))
    }

    override fun observeShowNotifications(): Flow<Boolean> = dao.observeSettings().map { it?.showNotifications ?: false }

    override suspend fun getShowNotifications(): Boolean = dao.getSettings()?.showNotifications ?: false

    override suspend fun setShowNotifications(enabled: Boolean) {
        val current = dao.getSettings() ?: AppSettingsEntity()
        dao.upsert(current.copy(showNotifications = enabled))
    }

    override fun observeKeepScheduleHistory(): Flow<Boolean> = dao.observeSettings().map { it?.keepScheduleHistory ?: true }

    override suspend fun getKeepScheduleHistory(): Boolean = dao.getSettings()?.keepScheduleHistory ?: true

    override suspend fun setKeepScheduleHistory(enabled: Boolean) {
        val current = dao.getSettings() ?: AppSettingsEntity()
        dao.upsert(current.copy(keepScheduleHistory = enabled))
    }

    override fun observeLandingScreen(): Flow<LandingScreen> = dao.observeSettings().map { it?.landingScreen.toLandingScreen() }

    override suspend fun getLandingScreen(): LandingScreen = dao.getSettings()?.landingScreen.toLandingScreen()

    override suspend fun setLandingScreen(screen: LandingScreen) {
        val current = dao.getSettings() ?: AppSettingsEntity()
        dao.upsert(current.copy(landingScreen = screen.name))
    }

    // A restored backup or a stale row from before this column existed could carry anything (or
    // nothing); fall back to the same BACKUP default the column's own SQL default uses, rather
    // than throwing on an unrecognized name.
    private fun String?.toLandingScreen(): LandingScreen =
        this?.let { name -> runCatching { LandingScreen.valueOf(name) }.getOrNull() } ?: LandingScreen.BACKUP
}

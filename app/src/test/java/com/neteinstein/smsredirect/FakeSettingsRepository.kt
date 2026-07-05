package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(
    initial: Boolean = false,
) : SettingsRepository {
    private val sendWhileRoamingState = MutableStateFlow(initial)
    private val keepHistoryState = MutableStateFlow(true)
    private val defaultPrefixState = MutableStateFlow("+351")
    private val forwardPrefixState = MutableStateFlow("SMS Redirect:")
    private val showNotificationsState = MutableStateFlow(true)

    override fun observeSendWhileRoaming(): Flow<Boolean> = sendWhileRoamingState

    override suspend fun getSendWhileRoaming(): Boolean = sendWhileRoamingState.value

    override suspend fun setSendWhileRoaming(enabled: Boolean) {
        sendWhileRoamingState.value = enabled
    }

    override fun observeKeepHistory(): Flow<Boolean> = keepHistoryState

    override suspend fun getKeepHistory(): Boolean = keepHistoryState.value

    override suspend fun setKeepHistory(enabled: Boolean) {
        keepHistoryState.value = enabled
    }

    override fun observeDefaultPrefix(): Flow<String> = defaultPrefixState

    override suspend fun getDefaultPrefix(): String = defaultPrefixState.value

    override suspend fun setDefaultPrefix(prefix: String) {
        defaultPrefixState.value = prefix
    }

    override fun observeForwardPrefix(): Flow<String> = forwardPrefixState

    override suspend fun getForwardPrefix(): String = forwardPrefixState.value

    override suspend fun setForwardPrefix(prefix: String) {
        forwardPrefixState.value = prefix
    }

    override fun observeShowNotifications(): Flow<Boolean> = showNotificationsState

    override suspend fun getShowNotifications(): Boolean = showNotificationsState.value

    override suspend fun setShowNotifications(enabled: Boolean) {
        showNotificationsState.value = enabled
    }
}

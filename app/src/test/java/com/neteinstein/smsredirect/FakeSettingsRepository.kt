package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(initial: Boolean = false) : SettingsRepository {
    private val sendWhileRoamingState = MutableStateFlow(initial)
    private val keepHistoryState = MutableStateFlow(true)

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
}

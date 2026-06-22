package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(initial: Boolean = false) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observeSendWhileRoaming(): Flow<Boolean> = state

    override suspend fun getSendWhileRoaming(): Boolean = state.value

    override suspend fun setSendWhileRoaming(enabled: Boolean) {
        state.value = enabled
    }
}

package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.MessagingSettings
import com.neteinstein.smsredirect.domain.repository.MessagingSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMessagingSettingsRepository(
    initial: MessagingSettings = MessagingSettings(),
) : MessagingSettingsRepository {
    val state = MutableStateFlow(initial)

    override fun observe(): Flow<MessagingSettings> = state

    override suspend fun get(): MessagingSettings = state.value

    override suspend fun set(settings: MessagingSettings) {
        state.value = settings
    }
}

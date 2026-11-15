package com.neteinstein.smsredirect.domain.repository

import com.neteinstein.smsredirect.domain.model.MessagingSettings
import kotlinx.coroutines.flow.Flow

interface MessagingSettingsRepository {
    fun observe(): Flow<MessagingSettings>

    suspend fun get(): MessagingSettings

    suspend fun set(settings: MessagingSettings)
}

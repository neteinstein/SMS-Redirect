package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.repository.SmsBackupSettingsRepository
import kotlinx.coroutines.flow.Flow

class GetSmsBackupSettingsUseCase(
    private val repository: SmsBackupSettingsRepository,
) {
    operator fun invoke(): Flow<SmsBackupSettings> = repository.observeSettings()
}

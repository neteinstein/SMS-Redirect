package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.SettingsRepository

class GetDefaultPrefixNowUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): String = repository.getDefaultPrefix()
}

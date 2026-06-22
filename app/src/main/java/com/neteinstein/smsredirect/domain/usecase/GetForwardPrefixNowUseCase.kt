package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.SettingsRepository

class GetForwardPrefixNowUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): String = repository.getForwardPrefix()
}

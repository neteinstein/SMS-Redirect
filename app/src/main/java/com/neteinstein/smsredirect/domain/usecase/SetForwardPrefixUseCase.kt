package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.SettingsRepository

class SetForwardPrefixUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(prefix: String) = repository.setForwardPrefix(prefix)
}

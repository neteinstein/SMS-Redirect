package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.SettingsRepository

class CanForwardSmsUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(isRoaming: Boolean): Boolean {
        if (!isRoaming) return true
        return settingsRepository.getSendWhileRoaming()
    }
}

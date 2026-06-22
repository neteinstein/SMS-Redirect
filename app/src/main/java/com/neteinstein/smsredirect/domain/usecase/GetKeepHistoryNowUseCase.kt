package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.SettingsRepository

class GetKeepHistoryNowUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): Boolean = repository.getKeepHistory()
}

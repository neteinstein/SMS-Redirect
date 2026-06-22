package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetForwardPrefixUseCase(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<String> = repository.observeForwardPrefix()
}

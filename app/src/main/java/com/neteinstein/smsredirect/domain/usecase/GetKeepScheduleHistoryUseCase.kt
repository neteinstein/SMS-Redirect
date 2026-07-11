package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetKeepScheduleHistoryUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeKeepScheduleHistory()
}

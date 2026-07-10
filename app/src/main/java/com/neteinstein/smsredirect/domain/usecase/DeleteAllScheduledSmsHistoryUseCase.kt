package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.ScheduledSmsHistoryRepository

class DeleteAllScheduledSmsHistoryUseCase(
    private val repository: ScheduledSmsHistoryRepository,
) {
    suspend operator fun invoke() {
        repository.deleteAll()
    }
}

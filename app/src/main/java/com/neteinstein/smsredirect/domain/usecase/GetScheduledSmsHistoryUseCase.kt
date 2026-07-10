package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsHistoryRepository
import kotlinx.coroutines.flow.Flow

class GetScheduledSmsHistoryUseCase(
    private val repository: ScheduledSmsHistoryRepository,
) {
    operator fun invoke(limit: Int = 50): Flow<List<ScheduledSmsHistory>> = repository.observeRecent(limit)
}

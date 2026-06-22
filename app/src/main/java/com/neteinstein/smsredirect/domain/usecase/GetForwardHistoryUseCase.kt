package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ForwardHistory
import com.neteinstein.smsredirect.domain.repository.ForwardHistoryRepository
import kotlinx.coroutines.flow.Flow

class GetForwardHistoryUseCase(
    private val repository: ForwardHistoryRepository
) {
    operator fun invoke(limit: Int = 50): Flow<List<ForwardHistory>> = repository.observeRecent(limit)
}

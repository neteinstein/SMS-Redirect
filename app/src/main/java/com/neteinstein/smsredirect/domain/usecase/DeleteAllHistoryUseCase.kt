package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.ForwardHistoryRepository

class DeleteAllHistoryUseCase(
    private val repository: ForwardHistoryRepository,
) {
    suspend operator fun invoke() {
        repository.deleteAll()
    }
}

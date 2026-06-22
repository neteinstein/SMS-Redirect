package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ForwardHistory
import com.neteinstein.smsredirect.domain.repository.ForwardHistoryRepository

class RecordForwardHistoryUseCase(
    private val repository: ForwardHistoryRepository
) {
    suspend operator fun invoke(
        sender: String,
        targetNumber: String,
        messageBody: String,
        ruleName: String
    ) {
        repository.insert(
            ForwardHistory(
                sender = sender,
                targetNumber = targetNumber,
                messageBody = messageBody,
                ruleName = ruleName,
                forwardedAtEpochMs = System.currentTimeMillis()
            )
        )
        repository.trimToLatest(50)
    }
}

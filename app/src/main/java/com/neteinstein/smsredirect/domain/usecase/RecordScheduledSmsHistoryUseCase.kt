package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsHistoryRepository

class RecordScheduledSmsHistoryUseCase(
    private val repository: ScheduledSmsHistoryRepository,
) {
    suspend operator fun invoke(
        scheduledSmsId: Long,
        scheduledSmsName: String,
        contactNumber: String,
        messageBody: String,
    ) {
        repository.insert(
            ScheduledSmsHistory(
                scheduledSmsId = scheduledSmsId,
                scheduledSmsName = scheduledSmsName,
                contactNumber = contactNumber,
                messageBody = messageBody,
                sentAtEpochMs = System.currentTimeMillis(),
            ),
        )
        repository.trimToLatest(50)
    }
}

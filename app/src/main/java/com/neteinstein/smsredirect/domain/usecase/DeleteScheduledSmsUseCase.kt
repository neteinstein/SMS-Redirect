package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler

class DeleteScheduledSmsUseCase(
    private val repository: ScheduledSmsRepository,
    private val scheduler: ScheduledSmsScheduler,
) {
    suspend operator fun invoke(scheduledSms: ScheduledSms) {
        repository.delete(scheduledSms)
        scheduler.cancel(scheduledSms.id)
    }
}

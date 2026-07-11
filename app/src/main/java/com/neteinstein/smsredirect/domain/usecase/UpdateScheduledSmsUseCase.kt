package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler

class UpdateScheduledSmsUseCase(
    private val repository: ScheduledSmsRepository,
    private val scheduler: ScheduledSmsScheduler,
) {
    suspend operator fun invoke(scheduledSms: ScheduledSms): Result<Unit> {
        if (!scheduledSms.isValid()) {
            return Result.failure(
                IllegalArgumentException("Contact, message and date are required"),
            )
        }
        repository.update(scheduledSms)
        // Editing may change the date, contact, message or repeat pattern, so cancel any pending
        // work and reschedule fresh, re-anchored to the (possibly new) date.
        scheduler.cancel(scheduledSms.id)
        scheduler.scheduleInitial(scheduledSms)
        return Result.success(Unit)
    }
}

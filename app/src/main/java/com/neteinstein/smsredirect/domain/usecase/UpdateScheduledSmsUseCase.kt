package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler
import com.neteinstein.smsredirect.domain.scheduling.scheduleUpcoming

class UpdateScheduledSmsUseCase(
    private val repository: ScheduledSmsRepository,
    private val scheduler: ScheduledSmsScheduler,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(scheduledSms: ScheduledSms): Result<Unit> {
        if (!scheduledSms.isValid()) {
            return Result.failure(
                IllegalArgumentException("Contact, message and date are required"),
            )
        }
        repository.update(scheduledSms)
        // Editing may change the date, contact, message or repeat pattern, so cancel any pending
        // work and arm the next send from the edited values. A date that has already passed was
        // sent (or missed) already: arming it again would send the SMS at once, so a repeating
        // schedule moves on to its next occurrence after now and a one-off is left with nothing.
        scheduler.cancel(scheduledSms.id)
        scheduler.scheduleUpcoming(scheduledSms, now())
        analyticsTracker.logEvent(
            AnalyticsEvents.SCHEDULED_SMS_UPDATED,
            mapOf(AnalyticsEvents.Param.REPEAT_TYPE to scheduledSms.repeatType.name),
        )
        return Result.success(Unit)
    }
}

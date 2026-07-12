package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler

class AddScheduledSmsUseCase(
    private val repository: ScheduledSmsRepository,
    private val scheduler: ScheduledSmsScheduler,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    suspend operator fun invoke(scheduledSms: ScheduledSms): Result<Unit> {
        if (!scheduledSms.isValid()) {
            return Result.failure(
                IllegalArgumentException("Contact, message and date are required"),
            )
        }
        val id = repository.insert(scheduledSms)
        scheduler.scheduleInitial(scheduledSms.copy(id = id))
        analyticsTracker.logEvent(
            AnalyticsEvents.SCHEDULED_SMS_CREATED,
            mapOf(AnalyticsEvents.Param.REPEAT_TYPE to scheduledSms.repeatType.name),
        )
        return Result.success(Unit)
    }
}

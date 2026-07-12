package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler

class DeleteScheduledSmsUseCase(
    private val repository: ScheduledSmsRepository,
    private val scheduler: ScheduledSmsScheduler,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    suspend operator fun invoke(scheduledSms: ScheduledSms) {
        repository.delete(scheduledSms)
        scheduler.cancel(scheduledSms.id)
        analyticsTracker.logEvent(AnalyticsEvents.SCHEDULED_SMS_DELETED)
    }
}

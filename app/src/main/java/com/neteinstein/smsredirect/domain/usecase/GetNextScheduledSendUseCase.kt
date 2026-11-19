package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.scheduling.RecurrenceCalculator

/**
 * When a scheduled SMS will next be sent, as of now, or null when nothing is left to send. This is
 * the same time the scheduler arms, so the list shows the real next send rather than the date the
 * schedule started from.
 */
class GetNextScheduledSendUseCase(
    private val now: () -> Long = System::currentTimeMillis,
) {
    operator fun invoke(scheduledSms: ScheduledSms): Long? = RecurrenceCalculator.upcomingSend(scheduledSms, now())
}

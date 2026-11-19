package com.neteinstein.smsredirect.domain.scheduling

import com.neteinstein.smsredirect.domain.model.ScheduledSms

/**
 * Abstracts the platform mechanism used to wake up and fire scheduled SMS sends, keeping
 * Android/WorkManager types out of the domain layer.
 * Mirrors [com.neteinstein.smsredirect.domain.contacts.ContactNameResolver]'s domain/data split.
 */
interface ScheduledSmsScheduler {
    /** Schedules the very first send for [scheduledSms], to fire at its [ScheduledSms.dateEpochMs]. */
    fun scheduleInitial(scheduledSms: ScheduledSms)

    /** Schedules the next repeat occurrence for the scheduled SMS with [scheduledSmsId], at [triggerAtEpochMs]. */
    fun scheduleNext(
        scheduledSmsId: Long,
        triggerAtEpochMs: Long,
    )

    /** Cancels any pending work for the scheduled SMS with [scheduledSmsId]. */
    fun cancel(scheduledSmsId: Long)
}

/**
 * Arms the send of [scheduledSms] that is still ahead of [nowEpochMs] (see
 * [RecurrenceCalculator.upcomingSend]) and returns its time, or arms nothing and returns null when no
 * send is left. Use this, not [ScheduledSmsScheduler.scheduleInitial], for a schedule that may already
 * have sent: `scheduleInitial` with a date in the past fires at once and sends the SMS again.
 */
fun ScheduledSmsScheduler.scheduleUpcoming(
    scheduledSms: ScheduledSms,
    nowEpochMs: Long,
): Long? {
    val sendAt = RecurrenceCalculator.upcomingSend(scheduledSms, nowEpochMs) ?: return null
    if (sendAt == scheduledSms.dateEpochMs) {
        scheduleInitial(scheduledSms)
    } else {
        scheduleNext(scheduledSms.id, sendAt)
    }
    return sendAt
}

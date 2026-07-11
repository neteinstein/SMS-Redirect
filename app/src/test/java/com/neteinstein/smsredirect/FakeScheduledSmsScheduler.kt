package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler

/**
 * Records invocations instead of touching WorkManager, so use cases can be verified without
 * pulling Android dependencies into domain-layer tests.
 */
class FakeScheduledSmsScheduler : ScheduledSmsScheduler {
    val scheduleInitialCalls = mutableListOf<ScheduledSms>()
    val scheduleNextCalls = mutableListOf<Pair<Long, Long>>()
    val cancelCalls = mutableListOf<Long>()

    override fun scheduleInitial(scheduledSms: ScheduledSms) {
        scheduleInitialCalls += scheduledSms
    }

    override fun scheduleNext(
        scheduledSmsId: Long,
        triggerAtEpochMs: Long,
    ) {
        scheduleNextCalls += scheduledSmsId to triggerAtEpochMs
    }

    override fun cancel(scheduledSmsId: Long) {
        cancelCalls += scheduledSmsId
    }
}

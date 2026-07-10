package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.ScheduledSmsHistoryEntity
import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory

internal fun ScheduledSmsHistoryEntity.toDomain(): ScheduledSmsHistory =
    ScheduledSmsHistory(
        id = id,
        scheduledSmsId = scheduledSmsId,
        scheduledSmsName = scheduledSmsName,
        contactNumber = contactNumber,
        messageBody = messageBody,
        sentAtEpochMs = sentAtEpochMs,
    )

internal fun ScheduledSmsHistory.toEntity(): ScheduledSmsHistoryEntity =
    ScheduledSmsHistoryEntity(
        id = id,
        scheduledSmsId = scheduledSmsId,
        scheduledSmsName = scheduledSmsName,
        contactNumber = contactNumber,
        messageBody = messageBody,
        sentAtEpochMs = sentAtEpochMs,
    )

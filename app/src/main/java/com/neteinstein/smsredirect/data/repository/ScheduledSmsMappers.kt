package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.ScheduledSmsEntity
import com.neteinstein.smsredirect.domain.model.RepeatType
import com.neteinstein.smsredirect.domain.model.ScheduledSms

internal fun ScheduledSmsEntity.toDomain(): ScheduledSms =
    ScheduledSms(
        id = id,
        name = name,
        category = category,
        contactNumber = contactNumber,
        message = message,
        dateEpochMs = dateEpochMs,
        repeatType = repeatType.toRepeatTypeOrNone(),
        stopAtEpochMs = stopAtEpochMs,
        sortOrder = sortOrder,
    )

internal fun ScheduledSms.toEntity(): ScheduledSmsEntity =
    ScheduledSmsEntity(
        id = id,
        name = name,
        category = category,
        contactNumber = contactNumber,
        message = message,
        dateEpochMs = dateEpochMs,
        repeatType = repeatType.name,
        stopAtEpochMs = stopAtEpochMs,
        sortOrder = sortOrder,
    )

/** Falls back to [RepeatType.NONE] for unrecognized/corrupt stored values instead of crashing. */
private fun String.toRepeatTypeOrNone(): RepeatType = runCatching { RepeatType.valueOf(this) }.getOrDefault(RepeatType.NONE)

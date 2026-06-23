package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.ForwardHistoryEntity
import com.neteinstein.smsredirect.domain.model.ForwardHistory

internal fun ForwardHistoryEntity.toDomain(): ForwardHistory =
    ForwardHistory(
        id = id,
        sender = sender,
        targetNumber = targetNumber,
        messageBody = messageBody,
        ruleName = ruleName,
        forwardedAtEpochMs = forwardedAtEpochMs,
    )

internal fun ForwardHistory.toEntity(): ForwardHistoryEntity =
    ForwardHistoryEntity(
        id = id,
        sender = sender,
        targetNumber = targetNumber,
        messageBody = messageBody,
        ruleName = ruleName,
        forwardedAtEpochMs = forwardedAtEpochMs,
    )

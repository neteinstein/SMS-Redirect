package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.RuleEntity
import com.neteinstein.smsredirect.domain.model.Rule

internal fun RuleEntity.toDomain(): Rule =
    Rule(
        id = id,
        name = name,
        senderText = senderText,
        phoneNumber = phoneNumber,
        targetNumber = targetNumber,
        sortOrder = sortOrder,
        category = category,
    )

internal fun Rule.toEntity(): RuleEntity =
    RuleEntity(
        id = id,
        name = name,
        senderText = senderText,
        phoneNumber = phoneNumber,
        targetNumber = targetNumber,
        sortOrder = sortOrder,
        category = category,
    )

package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.RuleEntity
import com.neteinstein.smsredirect.domain.model.Rule

internal fun RuleEntity.toDomain(): Rule = Rule(
    id = id,
    senderText = senderText,
    phoneNumber = phoneNumber
)

internal fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    senderText = senderText,
    phoneNumber = phoneNumber
)

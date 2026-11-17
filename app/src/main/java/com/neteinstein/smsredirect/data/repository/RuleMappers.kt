package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.RuleEntity
import com.neteinstein.smsredirect.domain.model.PhoneAction
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.model.RuleType

internal fun RuleEntity.toDomain(): Rule =
    Rule(
        id = id,
        name = name,
        senderText = senderText,
        phoneNumber = phoneNumber,
        targetNumber = targetNumber,
        sortOrder = sortOrder,
        category = category,
        // An unknown type (a newer build's rule, read by an older one) must not crash the list.
        type = RuleType.entries.firstOrNull { it.name == ruleType } ?: RuleType.REDIRECT,
        messageText = messageText,
        phoneAction = PhoneAction.decode(phoneAction, phoneActionValue, phoneActionLabel),
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
        ruleType = type.name,
        messageText = messageText,
        phoneAction = phoneAction?.kind.orEmpty(),
        phoneActionValue = phoneAction?.value.orEmpty(),
        phoneActionLabel = phoneAction?.label.orEmpty(),
    )

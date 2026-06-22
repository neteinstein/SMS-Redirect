package com.neteinstein.smsredirect.domain.model

data class ForwardHistory(
    val id: Long = 0,
    val sender: String = "",
    val targetNumber: String = "",
    val messageBody: String = "",
    val ruleName: String = "",
    val forwardedAtEpochMs: Long = 0
)

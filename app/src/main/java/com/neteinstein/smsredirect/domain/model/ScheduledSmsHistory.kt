package com.neteinstein.smsredirect.domain.model

data class ScheduledSmsHistory(
    val id: Long = 0,
    val scheduledSmsId: Long = 0,
    val scheduledSmsName: String = "",
    val contactNumber: String = "",
    val messageBody: String = "",
    val sentAtEpochMs: Long = 0,
)

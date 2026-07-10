package com.neteinstein.smsredirect.domain.model

data class ScheduledSms(
    val id: Long = 0,
    val name: String = "",
    val category: String = "",
    val contactNumber: String = "",
    val message: String = "",
    val dateEpochMs: Long = 0,
    val repeatType: RepeatType = RepeatType.NONE,
    val stopAtEpochMs: Long? = null,
    val sortOrder: Int = 0,
) {
    fun isValid(): Boolean = contactNumber.isNotBlank() && message.isNotBlank() && dateEpochMs > 0
}

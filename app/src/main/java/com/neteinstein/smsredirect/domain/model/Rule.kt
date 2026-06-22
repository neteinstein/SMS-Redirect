package com.neteinstein.smsredirect.domain.model

data class Rule(
    val id: Long = 0,
    val senderText: String = "",
    val phoneNumber: String = "",
    val targetNumber: String = ""
) {
    fun isValid(): Boolean = targetNumber.isNotBlank() && (senderText.isNotBlank() || phoneNumber.isNotBlank())
}

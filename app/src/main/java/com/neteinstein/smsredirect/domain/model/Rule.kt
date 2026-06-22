package com.neteinstein.smsredirect.domain.model

data class Rule(
    val id: Long = 0,
    val senderText: String = "",
    val phoneNumber: String = ""
) {
    fun isValid(): Boolean = senderText.isNotBlank() || phoneNumber.isNotBlank()
}

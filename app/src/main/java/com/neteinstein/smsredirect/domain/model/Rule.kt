package com.neteinstein.smsredirect.domain.model

data class Rule(
    val id: Long = 0,
    val name: String = "",
    val senderText: String = "",
    val phoneNumber: String = "",
    val targetNumber: String = "",
    val sortOrder: Int = 0,
    val category: String = "",
) {
    fun isValid(): Boolean = targetNumber.isNotBlank() && (senderText.isNotBlank() || phoneNumber.isNotBlank())
}

package com.neteinstein.smsredirect.domain.model

/** Drives the "Backup ongoing X of Y" notification and the Backup screen's progress row. */
data class SmsBackupProgress(
    val done: Int,
    val total: Int,
) {
    val isComplete: Boolean get() = total == 0 || done >= total
}

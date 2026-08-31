package com.neteinstein.smsredirect.domain.model

/**
 * Backup configuration as the UI sees it.
 *
 * Carries [hasAppPassword] rather than the password itself: the plaintext is only ever needed by
 * the mail transport and should never re-enter the presentation layer.
 */
data class SmsBackupSettings(
    val gmailAddress: String = "",
    val hasAppPassword: Boolean = false,
    val subjectTemplate: String = DEFAULT_SUBJECT_TEMPLATE,
    val gmailLabel: String = DEFAULT_LABEL,
    val backupPreviousEnabled: Boolean = false,
    val backupEnabled: Boolean = false,
    val backupStartedAtMillis: Long = 0L,
) {
    val hasCredentials: Boolean get() = gmailAddress.isNotBlank() && hasAppPassword

    companion object {
        const val PLACEHOLDER_CONTACT_NAME = "\$CONTACT_NAME"
        const val PLACEHOLDER_PHONE_NUMBER = "\$PHONE_NUMBER"
        const val DEFAULT_SUBJECT_TEMPLATE = "SMS with $PLACEHOLDER_CONTACT_NAME ($PLACEHOLDER_PHONE_NUMBER)"
        const val DEFAULT_LABEL = "SMS"
    }
}

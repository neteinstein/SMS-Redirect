package com.neteinstein.smsredirect.domain.model

/**
 * A Gmail address plus a 16-character app password.
 *
 * The app password grants full IMAP access to the entire mailbox, not just the backup label, so
 * [toString] is overridden to redact it. An un-redacted data class would eventually surface the
 * secret in a Crashlytics breadcrumb or a stray Log call.
 */
data class GmailCredentials(
    val address: String,
    val appPassword: String,
) {
    override fun toString(): String = "GmailCredentials(address=$address, appPassword=REDACTED)"
}

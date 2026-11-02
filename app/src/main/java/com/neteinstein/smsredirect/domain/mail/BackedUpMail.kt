package com.neteinstein.smsredirect.domain.mail

/**
 * A mail read back from the backup label, as raw as the data layer can hand it over. Turning it
 * into an SMS is [SmsMailParser]'s job, so that logic stays JVM-testable and `javax.mail` never
 * leaves the data layer.
 */
data class BackedUpMail(
    /** IMAP UID. Increases with every append, which is what lets a restore resume by cursor. */
    val uid: Long,
    val messageId: String?,
    /** Decoded header values keyed by lower-cased header name. First value wins on repeats. */
    val headers: Map<String, String>,
    /** The `Date:` header, second precision. Null when missing or unparseable. */
    val sentDateEpochMs: Long?,
    /** The `text/plain` body, or null when the mail has none. */
    val bodyText: String?,
    /** Bare address of the first `From:` mailbox, or null when missing or unparseable. */
    val fromAddress: String? = null,
    /**
     * Whether any `Received:` header records a hop *from* another host -- i.e. the mail travelled
     * through SMTP. Backups are IMAP APPENDs straight into the mailbox and never do.
     */
    val arrivedOverSmtp: Boolean = false,
) {
    fun header(name: String): String? = headers[name.lowercase()]
}

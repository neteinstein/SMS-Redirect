package com.neteinstein.smsredirect.domain.mail

import com.neteinstein.smsredirect.domain.model.GmailCredentials

/**
 * Sends mail to an IMAP store. The only abstraction over JavaMail.
 *
 * This seam is the point of the design: `SmsForwarder` is untested today precisely because it
 * reaches straight for `SmsManager` and a raw `Context`. Everything that decides *what* to send
 * lives in use cases behind this interface and is covered by plain JUnit tests with
 * `FakeMailTransport`; the real implementation is thin enough to review by eye.
 */
interface MailTransport {
    suspend fun verify(credentials: GmailCredentials): MailVerificationResult

    /**
     * Opens one folder, runs [block] against it, and closes it.
     *
     * The session shape is deliberate. Gmail caps IMAP at roughly 15 concurrent connections per
     * account and throttles rapid connect/disconnect cycles, so a backfill must append a whole
     * batch over a single connection rather than reconnecting per message.
     */
    suspend fun <T> withFolder(
        credentials: GmailCredentials,
        folderName: String,
        block: suspend (MailFolder) -> T,
    ): T
}

/** An open, writable IMAP folder. Gmail maps folders onto labels. */
interface MailFolder {
    suspend fun append(mail: OutgoingMail): MailAppendResult
}

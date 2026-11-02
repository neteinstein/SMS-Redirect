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
     *
     * With [readOnly] the folder is opened for reading only and is never created: a restore must
     * not conjure up an empty label, and reports [MailConnectException.FolderNotFound] instead.
     */
    suspend fun <T> withFolder(
        credentials: GmailCredentials,
        folderName: String,
        readOnly: Boolean = false,
        block: suspend (MailFolder) -> T,
    ): T
}

/** An open IMAP folder. Gmail maps folders onto labels. */
interface MailFolder {
    suspend fun append(mail: OutgoingMail): MailAppendResult

    /** Number of mails in the folder, for the restore's "X of Y" denominator. */
    suspend fun messageCount(): Int

    /**
     * Reads up to [limit] mails whose UID is strictly greater than [afterUid], in UID order.
     * An empty list means the end of the folder. Throws [MailConnectException] on failure --
     * a restore that cannot read has nothing to fall back on, so there is no per-mail result.
     */
    suspend fun readAfter(
        afterUid: Long,
        limit: Int,
    ): List<BackedUpMail>
}

/**
 * Thrown by [MailTransport.withFolder] when the connection or folder open fails, before [block]
 * ever runs. A connect-time failure can't be reported through the generic return type [T], so an
 * exception is the only channel available to signal it to the caller.
 */
sealed class MailConnectException(
    message: String,
) : Exception(message) {
    class AuthFailed : MailConnectException("AuthenticationFailedException")

    class Transient(
        reason: String,
    ) : MailConnectException(reason)

    /** Only from a read-only open: the label does not exist, so there is nothing to read. */
    class FolderNotFound : MailConnectException("FolderNotFound")
}

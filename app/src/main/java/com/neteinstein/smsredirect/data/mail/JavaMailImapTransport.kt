package com.neteinstein.smsredirect.data.mail

import com.neteinstein.smsredirect.domain.mail.MailAppendResult
import com.neteinstein.smsredirect.domain.mail.MailConnectException
import com.neteinstein.smsredirect.domain.mail.MailFolder
import com.neteinstein.smsredirect.domain.mail.MailTransport
import com.neteinstein.smsredirect.domain.mail.MailVerificationResult
import com.neteinstein.smsredirect.domain.mail.OutgoingMail
import com.neteinstein.smsredirect.domain.model.GmailCredentials
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Date
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Gmail IMAP transport.
 *
 * IMAP APPEND rather than SMTP, deliberately. A Gmail label *is* an IMAP folder, so APPEND can
 * file messages under the user's chosen label and create it on first use; SMTP can only deliver
 * to the inbox. APPEND also preserves each message's original timestamp and is not subject to
 * Gmail's ~500/day sending cap, which a full-history backfill would blow through immediately.
 *
 * Uses the `javax.mail` namespace from `com.sun.mail:android-mail`. Not `jakarta.mail` -- that
 * line targets Jakarta EE and pulls JDK APIs Android does not ship.
 */
class JavaMailImapTransport(
    private val host: String = GMAIL_IMAP_HOST,
    private val port: Int = GMAIL_IMAP_PORT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MailTransport {
    override suspend fun verify(credentials: GmailCredentials): MailVerificationResult =
        withContext(dispatcher) {
            var store: Store? = null
            try {
                store = connect(credentials)
                MailVerificationResult.Success
            } catch (e: AuthenticationFailedException) {
                MailVerificationResult.InvalidCredentials
            } catch (e: MessagingException) {
                if (e.isTransient()) MailVerificationResult.NetworkError else MailVerificationResult.Unknown(e.reason())
            } finally {
                store?.closeQuietly()
            }
        }

    override suspend fun <T> withFolder(
        credentials: GmailCredentials,
        folderName: String,
        block: suspend (MailFolder) -> T,
    ): T =
        withContext(dispatcher) {
            var store: Store? = null
            var folder: Folder? = null
            try {
                val openedFolder =
                    try {
                        val connectedStore = connect(credentials)
                        store = connectedStore
                        connectedStore.getFolder(folderName.ifBlank { DEFAULT_FOLDER }).also {
                            // Gmail creates the matching label the first time the folder is created.
                            if (!it.exists()) it.create(Folder.HOLDS_MESSAGES)
                            it.open(Folder.READ_WRITE)
                        }
                    } catch (e: AuthenticationFailedException) {
                        throw MailConnectException.AuthFailed()
                    } catch (e: MessagingException) {
                        throw MailConnectException.Transient(e.reason())
                    }
                folder = openedFolder
                block(JavaMailFolder(openedFolder, credentials.address))
            } finally {
                runCatching { folder?.close(false) }
                store?.closeQuietly()
            }
        }

    private fun connect(credentials: GmailCredentials): Store {
        val session = Session.getInstance(imapProperties())
        return session.getStore("imaps").apply {
            connect(host, port, credentials.address, credentials.appPassword)
        }
    }

    private fun imapProperties(): Properties =
        Properties().apply {
            setProperty("mail.store.protocol", "imaps")
            setProperty("mail.imaps.host", host)
            setProperty("mail.imaps.port", port.toString())
            setProperty("mail.imaps.ssl.enable", "true")
            setProperty("mail.imaps.ssl.checkserveridentity", "true")
            // JavaMail defaults every timeout to infinite, which would hang a worker until
            // WorkManager kills it at ten minutes with no diagnostic.
            setProperty("mail.imaps.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
            setProperty("mail.imaps.timeout", READ_TIMEOUT_MS.toString())
            setProperty("mail.imaps.writetimeout", WRITE_TIMEOUT_MS.toString())
        }

    private class JavaMailFolder(
        private val folder: Folder,
        private val accountAddress: String,
    ) : MailFolder {
        override suspend fun append(mail: OutgoingMail): MailAppendResult =
            try {
                folder.appendMessages(arrayOf(buildMimeMessage(mail)))
                MailAppendResult.Success
            } catch (e: AuthenticationFailedException) {
                MailAppendResult.AuthFailed
            } catch (e: MessagingException) {
                // An unrecognized failure is classified as transient rather than permanent: the
                // cost of retrying something unretryable is a wasted attempt, but the cost of the
                // reverse -- treating a recoverable failure as permanent -- is silent data loss.
                if (e.isPermanent()) {
                    MailAppendResult.Permanent(e.reason())
                } else {
                    MailAppendResult.Transient(e.reason())
                }
            }

        private fun buildMimeMessage(mail: OutgoingMail): MimeMessage {
            val sentDate = Date(mail.dateEpochMs)
            // Folder.appendMessages() does not expose IMAP's INTERNALDATE argument directly --
            // it derives it from getReceivedDate(). Gmail sorts by INTERNALDATE, not the Date:
            // header, so without this override a backfill of years of SMS all sorts as "today".
            val message =
                object : MimeMessage(Session.getInstance(Properties())) {
                    override fun getReceivedDate(): Date = sentDate
                }

            message.setFrom(InternetAddress(mail.from, mail.fromDisplayName, CHARSET))
            message.setRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(mail.to))
            message.setSubject(mail.subject, CHARSET)
            message.setText(mail.body, CHARSET)
            message.sentDate = sentDate
            // saveChanges() regenerates Message-ID (and other headers) from scratch, so the
            // custom headers must be set after it runs or it would clobber them.
            message.saveChanges()
            message.setHeader("Message-ID", mail.messageId)
            mail.inReplyTo?.let { message.setHeader("In-Reply-To", it) }
            if (mail.references.isNotEmpty()) {
                message.setHeader("References", mail.references.joinToString(" "))
            }
            // Appended as read: a backfill should not add thousands to the unread count.
            message.setFlag(Flags.Flag.SEEN, true)
            return message
        }

        private companion object {
            const val CHARSET = "UTF-8"
        }
    }

    companion object {
        const val GMAIL_IMAP_HOST = "imap.gmail.com"
        const val GMAIL_IMAP_PORT = 993
        private const val DEFAULT_FOLDER = "SMS"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val WRITE_TIMEOUT_MS = 30_000

        /**
         * Gmail signals throttling and connection limits through ordinary MessagingExceptions, so
         * they have to be recognised by response text. Treating them as permanent would abandon a
         * backfill that only needed to wait.
         */
        private val TRANSIENT_MARKERS =
            listOf(
                "too many simultaneous connections",
                "[overquota]",
                "[unavailable]",
                "[inuse]",
                "[throttled]",
                "try again",
                "temporary",
            )

        private fun MessagingException.isTransient(): Boolean {
            var cause: Throwable? = this
            while (cause != null) {
                if (cause is IOException) return true
                cause = cause.cause
            }
            val text = message?.lowercase().orEmpty()
            return TRANSIENT_MARKERS.any { it in text }
        }

        /**
         * A deliberately small, conservative set of failures that are about the message itself
         * rather than the connection, so retrying can never help. Everything else defaults to
         * transient: misclassifying an unrecoverable failure as transient costs a wasted retry,
         * but the reverse -- marking a message "done" when it never reached Gmail -- is silent
         * data loss.
         */
        private val PERMANENT_MARKERS =
            listOf(
                // RFC 4469's literal APPEND-too-big response code, plus Gmail's own wording for it.
                "toobig",
                "message too large",
            )

        private fun MessagingException.isPermanent(): Boolean {
            val text = message?.lowercase().orEmpty()
            return PERMANENT_MARKERS.any { it in text }
        }

        /**
         * JavaMail exception text can echo the IMAP command line, which for a failed LOGIN would
         * include the app password. Only the exception class name is ever surfaced.
         */
        private fun MessagingException.reason(): String = this::class.java.simpleName

        private fun Store.closeQuietly() {
            runCatching { if (isConnected) close() }
        }
    }
}

package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.mail.MailAppendResult
import com.neteinstein.smsredirect.domain.mail.MailFolder
import com.neteinstein.smsredirect.domain.mail.SmsBackupOutcome
import com.neteinstein.smsredirect.domain.mail.SmsMailFactory
import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup
import com.neteinstein.smsredirect.domain.repository.SmsBackupStateRepository

/**
 * Appends a single SMS to the open Gmail folder, resolving the contact name and recording the
 * result so the message is never sent twice.
 *
 * Takes an already-open [MailFolder] rather than opening one itself: a backfill appends a whole
 * batch over one IMAP connection, because Gmail throttles rapid connect/disconnect cycles.
 */
class BackupSmsMessageUseCase(
    private val mailFactory: SmsMailFactory,
    private val backupStateRepository: SmsBackupStateRepository,
    private val contactNameResolver: ContactNameResolver?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        folder: MailFolder,
        accountAddress: String,
        subjectTemplate: String,
        message: SmsMessageToBackup,
    ): SmsBackupOutcome {
        if (backupStateRepository.filterAlreadyBackedUp(listOf(message.stableKey)).isNotEmpty()) {
            return SmsBackupOutcome.Skipped
        }

        // A missing contact name is not a failure -- the subject falls back to the raw number.
        val contactName = runCatching { contactNameResolver?.lookupNameByNumber(message.rawAddress) }.getOrNull()

        val mail = mailFactory.build(accountAddress, subjectTemplate, message, contactName)

        return when (val result = folder.append(mail)) {
            is MailAppendResult.Success -> {
                backupStateRepository.markBackedUp(listOf(message.stableKey), now())
                SmsBackupOutcome.Success
            }

            is MailAppendResult.AuthFailed -> {
                SmsBackupOutcome.AuthFailed
            }

            is MailAppendResult.Transient -> {
                SmsBackupOutcome.Transient(result.message)
            }

            is MailAppendResult.Permanent -> {
                // Recorded as resolved even though it never reached Gmail. Retrying cannot help,
                // and leaving it unrecorded would make every later run pick it up again -- with
                // the run re-enqueueing itself while work remains, that is an endless loop.
                // The state table therefore means "dealt with", not strictly "in Gmail".
                backupStateRepository.markBackedUp(listOf(message.stableKey), now())
                SmsBackupOutcome.Permanent(result.message)
            }
        }
    }
}

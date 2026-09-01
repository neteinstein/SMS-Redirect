package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.mail.MailTransport
import com.neteinstein.smsredirect.domain.mail.MailVerificationResult
import com.neteinstein.smsredirect.domain.model.GmailCredentials
import com.neteinstein.smsredirect.domain.repository.SmsBackupSettingsRepository
import com.neteinstein.smsredirect.domain.repository.SmsBackupStateRepository

/**
 * Checks a Gmail address and app password against the IMAP server, persisting them only if the
 * server accepts them. This is what backs the Settings "Save" button.
 */
class VerifyGmailCredentialsUseCase(
    private val mailTransport: MailTransport,
    private val repository: SmsBackupSettingsRepository,
    private val backupStateRepository: SmsBackupStateRepository,
) {
    suspend operator fun invoke(
        address: String,
        appPassword: String,
    ): MailVerificationResult {
        val trimmedAddress = address.trim()
        // Gmail shows app passwords in groups of four; users paste them with the spaces intact.
        val normalizedPassword = appPassword.filterNot { it.isWhitespace() }
        if (trimmedAddress.isBlank() || normalizedPassword.isBlank()) {
            return MailVerificationResult.InvalidCredentials
        }

        val result = mailTransport.verify(GmailCredentials(trimmedAddress, normalizedPassword))
        if (result is MailVerificationResult.Success) {
            val previousAddress = repository.getSettings().gmailAddress
            // The "already backed up" bookkeeping is scoped to whichever mailbox it was recorded
            // against. Switching accounts makes it stale -- keeping it would make every message
            // look already sent to a mailbox that has never seen any of them.
            if (previousAddress.isNotBlank() && !previousAddress.equals(trimmedAddress, ignoreCase = true)) {
                backupStateRepository.clear()
            }
            repository.setCredentials(trimmedAddress, normalizedPassword)
        }
        return result
    }
}

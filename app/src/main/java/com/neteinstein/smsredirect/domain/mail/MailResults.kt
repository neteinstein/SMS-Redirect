package com.neteinstein.smsredirect.domain.mail

/** Outcome of checking a Gmail address and app password against the IMAP server. */
sealed interface MailVerificationResult {
    data object Success : MailVerificationResult

    /**
     * The server rejected the credentials. Usually a wrong app password, but also what Google
     * returns when the account has no 2-Step Verification (and so cannot have app passwords at
     * all), or when policy blocks app passwords outright.
     */
    data object InvalidCredentials : MailVerificationResult

    data object NetworkError : MailVerificationResult

    data class Unknown(
        val message: String,
    ) : MailVerificationResult
}

/** Outcome of appending one message. */
sealed interface MailAppendResult {
    data object Success : MailAppendResult

    /** Credentials stopped working mid-run; the caller should disable backup and tell the user. */
    data object AuthFailed : MailAppendResult

    /** Worth retrying: timeouts, throttling, connection limits. */
    data class Transient(
        val message: String,
    ) : MailAppendResult

    /** Retrying will not help; the message is marked failed and the run moves on. */
    data class Permanent(
        val message: String,
    ) : MailAppendResult
}

/** What [com.neteinstein.smsredirect.domain.usecase.BackupSmsMessageUseCase] did with a message. */
sealed interface SmsBackupOutcome {
    data object Success : SmsBackupOutcome

    /** Already backed up. Not an error -- this is how resume-after-kill stays idempotent. */
    data object Skipped : SmsBackupOutcome

    data object AuthFailed : SmsBackupOutcome

    data class Transient(
        val message: String,
    ) : SmsBackupOutcome

    data class Permanent(
        val message: String,
    ) : SmsBackupOutcome
}

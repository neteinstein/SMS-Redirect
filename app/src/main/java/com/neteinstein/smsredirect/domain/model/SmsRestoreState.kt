package com.neteinstein.smsredirect.domain.model

/**
 * Where a multi-chunk restore has got to. Carried from one bounded worker run to the next, so a
 * restore of thousands of messages survives WorkManager's ten-minute ceiling without a
 * foreground service -- the same reasoning as the backup side.
 */
data class SmsRestoreCursor(
    /** IMAP UID of the last mail examined; the next run starts strictly after it. */
    val afterUid: Long = 0L,
    val processed: Int = 0,
    val restored: Int = 0,
    val alreadyOnDevice: Int = 0,
    val failed: Int = 0,
    /** Mail under the label that is not one of our backups (or is a forgery of one): skipped. */
    val notBackups: Int = 0,
)

data class SmsRestoreProgress(
    val processed: Int,
    val total: Int,
    val restored: Int,
)

enum class SmsRestoreFailure {
    /** The user switched the default SMS app away mid-restore; Android then refuses every write. */
    NOT_DEFAULT_SMS_APP,
    AUTH_FAILED,

    /** The configured Gmail label does not exist, so there is nothing to restore from. */
    LABEL_NOT_FOUND,

    /** Kept retrying on network/throttling errors and gave up. */
    NETWORK,

    /** No usable Gmail credentials on this device. */
    NOT_CONFIGURED,
}

/** What the Backup screen shows for the restore feature. */
sealed interface SmsRestoreState {
    data object Idle : SmsRestoreState

    /** [progress] is null while starting up, between chunks, or while waiting for a network. */
    data class Running(
        val progress: SmsRestoreProgress?,
    ) : SmsRestoreState

    data class Finished(
        val restored: Int,
        val alreadyOnDevice: Int,
        val failed: Int,
        val notBackups: Int = 0,
    ) : SmsRestoreState

    data class Failed(
        val reason: SmsRestoreFailure,
    ) : SmsRestoreState
}

package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.mail.MailConnectException
import com.neteinstein.smsredirect.domain.mail.MailTransport
import com.neteinstein.smsredirect.domain.mail.SmsBackupOutcome
import com.neteinstein.smsredirect.domain.model.SmsBackupProgress
import com.neteinstein.smsredirect.domain.repository.SmsBackupSettingsRepository
import com.neteinstein.smsredirect.domain.repository.SmsBackupStateRepository
import com.neteinstein.smsredirect.domain.sms.SmsMessageSource
import kotlinx.coroutines.delay

/** What a single bounded run of [RunSmsBackupUseCase] concluded. */
sealed interface SmsBackupRunOutcome {
    /** Nothing left to back up. */
    data object Completed : SmsBackupRunOutcome

    /** Hit the per-run budget; the caller should re-enqueue to continue. */
    data class ChunkDone(
        val done: Int,
    ) : SmsBackupRunOutcome

    /** Backup is off or no usable credentials are stored. Not an error. */
    data object NotConfigured : SmsBackupRunOutcome

    data object AuthFailed : SmsBackupRunOutcome

    data class Transient(
        val message: String,
    ) : SmsBackupRunOutcome
}

/**
 * Backs up every SMS not yet in Gmail, in one bounded chunk.
 *
 * Bounded on purpose. A run stops after [maxMessages] and re-enqueues itself, which keeps every
 * execution inside WorkManager's ten-minute ceiling without needing a foreground service — and
 * a foreground service would mean re-adding FOREGROUND_SERVICE (deliberately removed from this
 * manifest) plus a separate Play Console declaration.
 *
 * Progress is durable because it lives in the backup-state table rather than in memory: a run
 * killed mid-chunk simply re-reads the same messages next time and skips the ones already sent.
 */
class RunSmsBackupUseCase(
    private val settingsRepository: SmsBackupSettingsRepository,
    private val backupStateRepository: SmsBackupStateRepository,
    private val smsMessageSource: SmsMessageSource,
    private val mailTransport: MailTransport,
    private val backupSmsMessageUseCase: BackupSmsMessageUseCase,
    private val interMessageDelayMs: Long = DEFAULT_INTER_MESSAGE_DELAY_MS,
    private val elapsedNowMs: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        maxMessages: Int = DEFAULT_MAX_MESSAGES,
        deadlineMs: Long = DEFAULT_DEADLINE_MS,
        onProgress: suspend (SmsBackupProgress) -> Unit = {},
        isStopped: () -> Boolean = { false },
    ): SmsBackupRunOutcome {
        val settings = settingsRepository.getSettings()
        if (!settings.backupEnabled) return SmsBackupRunOutcome.NotConfigured
        val credentials = settingsRepository.getCredentials() ?: return SmsBackupRunOutcome.NotConfigured

        // Zero watermark means "everything"; otherwise only messages from when backup was enabled.
        val since = settings.backupStartedAtMillis

        // `alreadyDone` counts every message ever backed up, `total` only those the current
        // watermark makes eligible -- the two are on different scopes and can't be compared to
        // decide completion. Whether anything is actually pending is answered by scanning, below.
        val alreadyDone = backupStateRepository.count()
        val total = smsMessageSource.count(since)

        var done = alreadyDone
        var sentThisRun = 0
        var cursorDate = 0L
        var cursorId = 0L
        var outcome: SmsBackupRunOutcome = SmsBackupRunOutcome.Completed
        val startElapsedMs = elapsedNowMs()

        try {
            mailTransport.withFolder(credentials, settings.gmailLabel) { folder ->
                loop@ while (true) {
                    // Bounded on both message count and wall-clock time: a run of tiny messages
                    // could otherwise stay under maxMessages while still running past
                    // WorkManager's ten-minute execution ceiling.
                    if (isStopped() ||
                        sentThisRun >= maxMessages ||
                        elapsedNowMs() - startElapsedMs >= deadlineMs
                    ) {
                        outcome = SmsBackupRunOutcome.ChunkDone(done)
                        break@loop
                    }

                    val batch = smsMessageSource.readBatch(since, cursorDate, cursorId, BATCH_SIZE)
                    if (batch.isEmpty()) {
                        outcome = SmsBackupRunOutcome.Completed
                        break@loop
                    }

                    // Advance the cursor across the whole batch, including messages we skip, so
                    // a fully-backed-up batch cannot make the scan loop on itself forever.
                    batch.last().let { last ->
                        cursorDate = last.dateEpochMs
                        cursorId = last.providerId
                    }

                    val alreadyBackedUp = backupStateRepository.filterAlreadyBackedUp(batch.map { it.stableKey })
                    for (message in batch) {
                        // Already in Gmail, and already counted in `alreadyDone`.
                        if (message.stableKey in alreadyBackedUp) continue
                        if (isStopped() ||
                            sentThisRun >= maxMessages ||
                            elapsedNowMs() - startElapsedMs >= deadlineMs
                        ) {
                            outcome = SmsBackupRunOutcome.ChunkDone(done)
                            break@loop
                        }

                        when (
                            val result =
                                backupSmsMessageUseCase(
                                    folder,
                                    credentials.address,
                                    settings.subjectTemplate,
                                    message,
                                )
                        ) {
                            is SmsBackupOutcome.Success -> {
                                done++
                                sentThisRun++
                                onProgress(SmsBackupProgress(done, total))
                                // Paced to stay clear of Gmail's IMAP throttling.
                                if (interMessageDelayMs > 0) delay(interMessageDelayMs)
                            }

                            // Raced with another run; it is already counted in `alreadyDone`.
                            is SmsBackupOutcome.Skipped -> {
                                Unit
                            }

                            // Recorded as resolved by the use case, so the run can finish rather
                            // than re-enqueueing itself forever over a message Gmail will never
                            // accept. Still counted against the run's budget and reported, like a
                            // success, since the use case did resolve it one way or another.
                            is SmsBackupOutcome.Permanent -> {
                                done++
                                sentThisRun++
                                onProgress(SmsBackupProgress(done, total))
                            }

                            is SmsBackupOutcome.AuthFailed -> {
                                outcome = SmsBackupRunOutcome.AuthFailed
                                break@loop
                            }

                            is SmsBackupOutcome.Transient -> {
                                outcome = SmsBackupRunOutcome.Transient(result.message)
                                break@loop
                            }
                        }
                    }
                }
            }
        } catch (e: MailConnectException.AuthFailed) {
            outcome = SmsBackupRunOutcome.AuthFailed
        } catch (e: MailConnectException.Transient) {
            outcome = SmsBackupRunOutcome.Transient(e.message.orEmpty())
        }

        // Credentials that stopped working must not leave the UI showing a green "enabled" state.
        if (outcome is SmsBackupRunOutcome.AuthFailed) {
            settingsRepository.setBackupEnabled(enabled = false, startedAtMillis = since)
        }

        return outcome
    }

    companion object {
        const val DEFAULT_MAX_MESSAGES = 400
        const val BATCH_SIZE = 50

        // Comfortably under WorkManager's ten-minute execution ceiling, leaving room for the
        // connect and folder-open time this budget does not otherwise account for.
        const val DEFAULT_DEADLINE_MS = 8 * 60 * 1000L
        private const val DEFAULT_INTER_MESSAGE_DELAY_MS = 100L
    }
}

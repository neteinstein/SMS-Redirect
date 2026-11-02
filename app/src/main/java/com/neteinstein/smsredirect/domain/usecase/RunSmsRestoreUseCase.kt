package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.mail.MailConnectException
import com.neteinstein.smsredirect.domain.mail.MailTransport
import com.neteinstein.smsredirect.domain.mail.SmsMailParser
import com.neteinstein.smsredirect.domain.model.SmsBackupKey
import com.neteinstein.smsredirect.domain.model.SmsMessageToRestore
import com.neteinstein.smsredirect.domain.model.SmsRestoreCursor
import com.neteinstein.smsredirect.domain.model.SmsRestoreProgress
import com.neteinstein.smsredirect.domain.repository.SmsBackupSettingsRepository
import com.neteinstein.smsredirect.domain.repository.SmsBackupStateRepository
import com.neteinstein.smsredirect.domain.sms.SmsMessageWriter

/** What a single bounded run of [RunSmsRestoreUseCase] concluded. */
sealed interface SmsRestoreRunOutcome {
    data class Completed(
        val cursor: SmsRestoreCursor,
    ) : SmsRestoreRunOutcome

    /** Hit the per-run budget; the caller should re-enqueue with [cursor] to continue. */
    data class ChunkDone(
        val cursor: SmsRestoreCursor,
    ) : SmsRestoreRunOutcome

    /** No usable credentials on this device. */
    data object NotConfigured : SmsRestoreRunOutcome

    /** This app is not (or is no longer) the default SMS app, so Android refuses every write. */
    data object NotDefaultSmsApp : SmsRestoreRunOutcome

    data object LabelNotFound : SmsRestoreRunOutcome

    data object AuthFailed : SmsRestoreRunOutcome

    data class Transient(
        val message: String,
        val cursor: SmsRestoreCursor,
    ) : SmsRestoreRunOutcome
}

/**
 * Copies SMS backed up to the Gmail label back into the device's message store, in one bounded
 * chunk -- the restore twin of [RunSmsBackupUseCase].
 *
 * Idempotent by construction: every message is checked against what is already on the device
 * before it is written, so a run killed mid-chunk, a restore run twice, or a restore onto a phone
 * that still has some of its messages never produces duplicates.
 *
 * Every restored message is also recorded in the backup-state table. Without that, the backup
 * scan would find thousands of "new" messages in the inbox right after a restore onto a new device
 * and upload all of them to Gmail a second time.
 */
class RunSmsRestoreUseCase(
    private val settingsRepository: SmsBackupSettingsRepository,
    private val backupStateRepository: SmsBackupStateRepository,
    private val mailTransport: MailTransport,
    private val smsMessageWriter: SmsMessageWriter,
    private val parser: SmsMailParser = SmsMailParser(),
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsedNowMs: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        start: SmsRestoreCursor = SmsRestoreCursor(),
        maxMessages: Int = DEFAULT_MAX_MESSAGES,
        deadlineMs: Long = DEFAULT_DEADLINE_MS,
        onProgress: suspend (SmsRestoreProgress) -> Unit = {},
        isStopped: () -> Boolean = { false },
    ): SmsRestoreRunOutcome {
        val settings = settingsRepository.getSettings()
        val credentials = settingsRepository.getCredentials() ?: return SmsRestoreRunOutcome.NotConfigured
        if (!smsMessageWriter.canWrite()) return SmsRestoreRunOutcome.NotDefaultSmsApp

        var cursor = start
        var examinedThisRun = 0
        val startElapsedMs = elapsedNowMs()

        fun outOfBudget() = isStopped() || examinedThisRun >= maxMessages || elapsedNowMs() - startElapsedMs >= deadlineMs

        var outcome: SmsRestoreRunOutcome = SmsRestoreRunOutcome.Completed(cursor)
        try {
            mailTransport.withFolder(credentials, settings.gmailLabel, readOnly = true) { folder ->
                val total = folder.messageCount()
                loop@ while (true) {
                    if (outOfBudget()) {
                        outcome = SmsRestoreRunOutcome.ChunkDone(cursor)
                        break@loop
                    }
                    // Re-checked per batch: the user can switch the default SMS app away at any
                    // moment, after which every insert would be silently dropped.
                    if (!smsMessageWriter.canWrite()) {
                        outcome = SmsRestoreRunOutcome.NotDefaultSmsApp
                        break@loop
                    }

                    val batch = folder.readAfter(cursor.afterUid, BATCH_SIZE)
                    if (batch.isEmpty()) {
                        outcome = SmsRestoreRunOutcome.Completed(cursor)
                        break@loop
                    }

                    for (mail in batch) {
                        if (outOfBudget()) {
                            outcome = SmsRestoreRunOutcome.ChunkDone(cursor)
                            break@loop
                        }
                        val message = parser.parse(mail)
                        val counted =
                            when {
                                // Not one of ours: other mail filed under the same label.
                                message == null -> {
                                    cursor
                                }

                                isAlreadyOnDevice(message) -> {
                                    cursor.copy(alreadyOnDevice = cursor.alreadyOnDevice + 1)
                                }

                                restore(message) -> {
                                    cursor.copy(restored = cursor.restored + 1)
                                }

                                // The insert was refused because the role was lost, not because
                                // of this message. Stop without advancing past it.
                                !smsMessageWriter.canWrite() -> {
                                    outcome = SmsRestoreRunOutcome.NotDefaultSmsApp
                                    break@loop
                                }

                                else -> {
                                    cursor.copy(failed = cursor.failed + 1)
                                }
                            }
                        cursor = counted.copy(afterUid = maxOf(counted.afterUid, mail.uid), processed = counted.processed + 1)
                        examinedThisRun++
                        onProgress(SmsRestoreProgress(cursor.processed, maxOf(total, cursor.processed), cursor.restored))
                    }
                }
            }
        } catch (e: MailConnectException.AuthFailed) {
            outcome = SmsRestoreRunOutcome.AuthFailed
        } catch (e: MailConnectException.FolderNotFound) {
            outcome = SmsRestoreRunOutcome.LabelNotFound
        } catch (e: MailConnectException.Transient) {
            // Resume from where the failure struck, not from the start of the run.
            outcome = SmsRestoreRunOutcome.Transient(e.message.orEmpty(), cursor)
        }
        return outcome
    }

    private suspend fun isAlreadyOnDevice(message: SmsMessageToRestore): Boolean =
        smsMessageWriter
            .findAround(message.dateEpochMs, DUPLICATE_TOLERANCE_MS)
            .any { it.body == message.body && SmsBackupKey.sameAddress(it.address, message.address) }

    private suspend fun restore(message: SmsMessageToRestore): Boolean {
        if (!smsMessageWriter.insert(message)) return false
        backupStateRepository.markBackedUp(
            listOf(SmsBackupKey.of(message.address, message.body, message.dateEpochMs)),
            now(),
        )
        return true
    }

    companion object {
        const val DEFAULT_MAX_MESSAGES = 2_000
        const val BATCH_SIZE = 100

        /**
         * Legacy backups only carry a second-precision timestamp, so a restored copy can be up to
         * 999ms off the original still on the phone. A second either side covers that, and two
         * genuinely different messages with identical sender and text inside one second are
         * effectively always a duplicate delivery anyway.
         */
        const val DUPLICATE_TOLERANCE_MS = 1_000L

        // Same reasoning as RunSmsBackupUseCase: stay well under WorkManager's ten-minute ceiling.
        const val DEFAULT_DEADLINE_MS = 8 * 60 * 1000L
    }
}

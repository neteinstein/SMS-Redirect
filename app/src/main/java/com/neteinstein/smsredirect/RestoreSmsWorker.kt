package com.neteinstein.smsredirect

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.neteinstein.smsredirect.domain.model.SmsRestoreCursor
import com.neteinstein.smsredirect.domain.model.SmsRestoreFailure
import com.neteinstein.smsredirect.domain.usecase.SmsRestoreRunOutcome
import java.util.concurrent.TimeUnit

/**
 * Restores SMS from the Gmail backup label into the device's message store.
 *
 * Shaped like [BackupSmsWorker]: each run is bounded and re-enqueues itself with its cursor while
 * work remains, keeping every execution inside WorkManager's ten-minute ceiling without a
 * foreground service.
 *
 * Unlike the backup, the cursor travels in the work input rather than in a table: a restore is a
 * one-off the user starts by hand, and the duplicate check makes starting over always safe.
 */
class RestoreSmsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SmsRedirectApplication).appContainer
        val start = inputData.toCursor()
        // A transient failure re-enqueues a fresh request (to keep the cursor), which resets
        // WorkManager's own runAttemptCount -- so consecutive failures are counted here instead.
        val attempt = inputData.getInt(KEY_ATTEMPT, 0) + runAttemptCount

        return try {
            val outcome =
                container.runSmsRestoreUseCase(
                    start = start,
                    onProgress = { progress ->
                        BackupNotifications.showRestoreProgress(applicationContext, progress.processed, progress.total)
                        setProgress(
                            workDataOf(
                                KEY_PROCESSED to progress.processed,
                                KEY_TOTAL to progress.total,
                                KEY_RESTORED to progress.restored,
                            ),
                        )
                    },
                    isStopped = { isStopped },
                )

            when (outcome) {
                is SmsRestoreRunOutcome.Completed -> {
                    BackupNotifications.showRestoreComplete(applicationContext, outcome.cursor.restored)
                    Result.success(outcome.cursor.toData().finished())
                }

                is SmsRestoreRunOutcome.ChunkDone -> {
                    // APPEND_OR_REPLACE chains the continuation after this run, which is about to
                    // succeed. REPLACE would cancel this run first and flash a "cancelled" state
                    // at the Backup screen between chunks.
                    enqueue(applicationContext, outcome.cursor, ExistingWorkPolicy.APPEND_OR_REPLACE)
                    Result.success()
                }

                is SmsRestoreRunOutcome.Transient -> {
                    if (attempt >= MAX_ATTEMPTS) {
                        fail(SmsRestoreFailure.NETWORK)
                    } else {
                        // Resume from the cursor that was reached, not from this run's start.
                        BackupNotifications.cancelRestoreProgress(applicationContext)
                        enqueue(
                            applicationContext,
                            outcome.cursor,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            attempt = attempt + 1,
                            initialDelaySeconds = RETRY_BASE_DELAY_SECONDS shl attempt.coerceAtMost(4),
                        )
                        Result.success()
                    }
                }

                SmsRestoreRunOutcome.NotDefaultSmsApp -> {
                    fail(SmsRestoreFailure.NOT_DEFAULT_SMS_APP)
                }

                SmsRestoreRunOutcome.LabelNotFound -> {
                    fail(SmsRestoreFailure.LABEL_NOT_FOUND)
                }

                SmsRestoreRunOutcome.AuthFailed -> {
                    fail(SmsRestoreFailure.AUTH_FAILED)
                }

                SmsRestoreRunOutcome.NotConfigured -> {
                    fail(SmsRestoreFailure.NOT_CONFIGURED)
                }
            }
        } catch (e: Exception) {
            // Reported, not swallowed (unlike SmsForwarder), so retry/backoff actually works.
            Log.w(TAG, "Restore run failed", e)
            container.crashReporter.recordException(e)
            BackupNotifications.cancelRestoreProgress(applicationContext)
            if (attempt >= MAX_ATTEMPTS) fail(SmsRestoreFailure.NETWORK) else Result.retry()
        }
    }

    private fun fail(reason: SmsRestoreFailure): Result {
        BackupNotifications.showRestoreFailed(applicationContext)
        return Result.failure(workDataOf(KEY_FAILURE to reason.name))
    }

    companion object {
        private const val TAG = "RestoreSmsWorker"
        const val UNIQUE_WORK_NAME = "sms_restore"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_RESTORED = "restored"
        const val KEY_ALREADY_ON_DEVICE = "already_on_device"
        const val KEY_FAILED = "failed"
        const val KEY_NOT_BACKUPS = "not_backups"
        const val KEY_FINISHED = "finished"
        const val KEY_FAILURE = "failure"
        private const val KEY_AFTER_UID = "after_uid"
        private const val KEY_ATTEMPT = "attempt"
        private const val MAX_ATTEMPTS = 5
        private const val RETRY_BASE_DELAY_SECONDS = 30L

        /** Starts a restore from the beginning, replacing anything queued or finished before. */
        fun start(context: Context) = enqueue(context, SmsRestoreCursor(), ExistingWorkPolicy.REPLACE)

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            BackupNotifications.cancelRestoreProgress(context)
        }

        private fun enqueue(
            context: Context,
            cursor: SmsRestoreCursor,
            policy: ExistingWorkPolicy,
            attempt: Int = 0,
            initialDelaySeconds: Long = 0L,
        ) {
            val input =
                Data
                    .Builder()
                    .putAll(cursor.toData())
                    .putInt(KEY_ATTEMPT, attempt)
                    .build()
            val request =
                OneTimeWorkRequestBuilder<RestoreSmsWorker>()
                    .setInputData(input)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BASE_DELAY_SECONDS, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
        }

        private fun SmsRestoreCursor.toData(): Data =
            workDataOf(
                KEY_AFTER_UID to afterUid,
                KEY_PROCESSED to processed,
                KEY_RESTORED to restored,
                KEY_ALREADY_ON_DEVICE to alreadyOnDevice,
                KEY_FAILED to failed,
                KEY_NOT_BACKUPS to notBackups,
            )

        private fun Data.finished(): Data =
            Data
                .Builder()
                .putAll(this)
                .putBoolean(KEY_FINISHED, true)
                .build()

        private fun Data.toCursor(): SmsRestoreCursor =
            SmsRestoreCursor(
                afterUid = getLong(KEY_AFTER_UID, 0L),
                processed = getInt(KEY_PROCESSED, 0),
                restored = getInt(KEY_RESTORED, 0),
                alreadyOnDevice = getInt(KEY_ALREADY_ON_DEVICE, 0),
                failed = getInt(KEY_FAILED, 0),
                notBackups = getInt(KEY_NOT_BACKUPS, 0),
            )
    }
}

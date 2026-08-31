package com.neteinstein.smsredirect

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.neteinstein.smsredirect.domain.model.SmsBackupProgress
import com.neteinstein.smsredirect.domain.usecase.SmsBackupRunOutcome
import java.util.concurrent.TimeUnit

/**
 * Backs up everything in the SMS inbox that has not reached Gmail yet.
 *
 * One worker serves both paths -- the historical backfill and each newly received message --
 * because both simply scan the content provider for un-backed-up messages. There is no separate
 * "live" payload to keep in sync.
 *
 * Each run is bounded and re-enqueues itself while work remains, which keeps every execution
 * inside WorkManager's ten-minute ceiling. That is why no foreground service is needed:
 * FOREGROUND_SERVICE was deliberately removed from this manifest, and restoring it would also
 * require a FOREGROUND_SERVICE_DATA_SYNC declaration and a Play Console review on top of the
 * SMS permissions one.
 */
class BackupSmsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SmsRedirectApplication).appContainer

        return try {
            var lastProgress: SmsBackupProgress? = null
            val outcome =
                container.runSmsBackupUseCase(
                    onProgress = { progress ->
                        lastProgress = progress
                        BackupNotifications.showProgress(applicationContext, progress.done, progress.total)
                        setProgress(workDataOf(KEY_DONE to progress.done, KEY_TOTAL to progress.total))
                    },
                    isStopped = { isStopped },
                )

            when (outcome) {
                is SmsBackupRunOutcome.Completed -> {
                    lastProgress?.let { BackupNotifications.showComplete(applicationContext, it.done) }
                        ?: BackupNotifications.cancelProgress(applicationContext)
                    Result.success()
                }

                is SmsBackupRunOutcome.ChunkDone -> {
                    // More to do. Re-enqueue rather than run long, so the OS keeps scheduling us.
                    enqueue(applicationContext, replaceExisting = true)
                    Result.success()
                }

                is SmsBackupRunOutcome.NotConfigured -> {
                    BackupNotifications.cancelProgress(applicationContext)
                    Result.success()
                }

                is SmsBackupRunOutcome.AuthFailed -> {
                    // The use case has already switched backup off so the UI stops claiming
                    // it is running. Tell the user, and do not retry -- retrying cannot help.
                    BackupNotifications.showAuthFailed(applicationContext)
                    Result.failure()
                }

                is SmsBackupRunOutcome.Transient -> {
                    BackupNotifications.cancelProgress(applicationContext)
                    if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
                }
            }
        } catch (e: Exception) {
            // Unlike SmsForwarder, this does not swallow the failure: retry/backoff only works
            // if the worker actually reports one.
            Log.w(TAG, "Backup run failed", e)
            container.crashReporter.recordException(e)
            BackupNotifications.cancelProgress(applicationContext)
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val TAG = "BackupSmsWorker"
        const val UNIQUE_WORK_NAME = "sms_backup"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        private const val MAX_ATTEMPTS = 5

        /**
         * Gives the default SMS app time to write the incoming message to the provider before
         * the scan runs -- our receiver and that write race each other.
         */
        private const val LIVE_BACKUP_DELAY_SECONDS = 10L

        fun enqueue(
            context: Context,
            replaceExisting: Boolean = false,
            initialDelaySeconds: Long = 0L,
        ) {
            val request =
                OneTimeWorkRequestBuilder<BackupSmsWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                // KEEP coalesces the burst of triggers from several messages arriving together
                // into one run. A continuation re-enqueue must REPLACE to actually take effect.
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueForIncomingSms(context: Context) =
            enqueue(context, replaceExisting = false, initialDelaySeconds = LIVE_BACKUP_DELAY_SECONDS)

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}

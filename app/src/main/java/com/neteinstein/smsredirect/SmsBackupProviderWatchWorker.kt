package com.neteinstein.smsredirect

import android.content.Context
import android.provider.Telephony
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Starts a backup scan when the SMS provider changes, which is how a *sent* message gets backed
 * up. Nothing is broadcast for an outgoing SMS the way SMS_RECEIVED is for an incoming one.
 *
 * A WorkManager content-URI trigger (JobScheduler underneath) rather than a `ContentObserver` or
 * a periodic scan:
 * - A `ContentObserver` lives only as long as the process does, and keeping the process alive
 *   would need the foreground service this app deliberately does not have.
 * - A periodic scan wakes the device whether or not anything was sent, and still lags by up to
 *   its interval.
 * - The OS holds this trigger while the app is not running, keeps it across reboots, and starts
 *   nothing until the provider actually changes.
 *
 * The trigger is one-shot, so every run re-arms it. It fires on any provider change, including
 * incoming messages and read flags; the backup run checks for pending messages before it opens
 * an IMAP connection, so those cost a local scan and nothing else.
 *
 * Armed while backup is enabled. A run that finds backup switched off does not re-arm, so a stale
 * trigger dies out after at most one firing.
 */
class SmsBackupProviderWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SmsRedirectApplication).appContainer
        if (!container.smsBackupSettingsRepository.getSettings().backupEnabled) return Result.success()

        // Chains onto any run in progress, exactly like an incoming SMS: a message sent mid-run
        // still gets a follow-up scan.
        BackupSmsWorker.enqueue(applicationContext)
        // Last, because REPLACE cancels this very run: nothing after it is guaranteed to execute.
        arm(applicationContext, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sms_backup_provider_watch"

        /**
         * How long the provider must stay quiet before the trigger fires. An outgoing SMS is
         * written as an outbox row and updated to "sent" a few seconds later, once the radio
         * confirms it. Firing on the first write would scan before the row qualifies.
         */
        private const val UPDATE_DELAY_SECONDS = 15L

        /** Upper bound on that wait, so a busy conversation still gets backed up promptly. */
        private const val MAX_DELAY_SECONDS = 2 * 60L

        /** [ExistingWorkPolicy.KEEP] by default: arming an already-armed trigger is a no-op. */
        fun arm(
            context: Context,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
        ) {
            val request =
                OneTimeWorkRequestBuilder<SmsBackupProviderWatchWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .addContentUriTrigger(Telephony.Sms.CONTENT_URI, true)
                            .setTriggerContentUpdateDelay(UPDATE_DELAY_SECONDS, TimeUnit.SECONDS)
                            .setTriggerContentMaxDelay(MAX_DELAY_SECONDS, TimeUnit.SECONDS)
                            .build(),
                    ).build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
        }

        fun disarm(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}

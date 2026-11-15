package com.neteinstein.smsredirect

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Once a month, asks the user to open the app so Android does not treat it as unused and reset
 * its permissions (including the SMS ones). Skipped when the user exempted the app from that.
 *
 * Every app open restarts the countdown ([schedule]), so a user who opens the app regularly is
 * never reminded. The reminder carries no message content and nothing leaves the device.
 */
class KeepAliveReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SmsRedirectApplication).appContainer
        if (container.shouldSendKeepAliveReminderUseCase()) {
            KeepAliveNotifications.showReminder(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "keep_alive_reminder"
        const val INTERVAL_DAYS = 30L

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<KeepAliveReminderWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
                    .setInitialDelay(INTERVAL_DAYS, TimeUnit.DAYS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
        }
    }
}

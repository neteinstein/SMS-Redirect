package com.neteinstein.smsredirect.data.scheduling

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.neteinstein.smsredirect.SendScheduledSmsWorker
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler
import java.util.concurrent.TimeUnit

class WorkManagerScheduledSmsScheduler(
    private val context: Context,
) : ScheduledSmsScheduler {
    override fun scheduleInitial(scheduledSms: ScheduledSms) {
        schedule(scheduledSms.id, scheduledSms.dateEpochMs)
    }

    override fun scheduleNext(
        scheduledSmsId: Long,
        triggerAtEpochMs: Long,
    ) {
        schedule(scheduledSmsId, triggerAtEpochMs)
    }

    override fun cancel(scheduledSmsId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(scheduledSmsId))
    }

    private fun schedule(
        id: Long,
        triggerAtEpochMs: Long,
    ) {
        val delayMs = (triggerAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0)
        val data =
            Data
                .Builder()
                .putLong(SendScheduledSmsWorker.KEY_SCHEDULED_SMS_ID, id)
                .build()

        val request =
            OneTimeWorkRequestBuilder<SendScheduledSmsWorker>()
                .setInputData(data)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(uniqueWorkName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun uniqueWorkName(id: Long): String = "scheduled_sms_$id"
}

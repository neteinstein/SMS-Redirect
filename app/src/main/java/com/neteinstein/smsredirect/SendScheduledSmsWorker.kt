package com.neteinstein.smsredirect

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SendScheduledSmsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "SendScheduledSmsWorker"
        const val KEY_SCHEDULED_SMS_ID = "scheduled_sms_id"
    }

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_SCHEDULED_SMS_ID, -1L)

        Log.d(TAG, "doWork() — id=$id")

        if (id <= 0) {
            Log.w(TAG, "Skipping: invalid scheduled sms id")
            return Result.success()
        }

        val app = applicationContext as SmsRedirectApplication
        // Work enqueued by a build that had scheduling survives an update to one that does not
        // (the database and WorkManager queue are kept). Drop the send rather than
        // fail it and retry forever.
        if (!app.appContainer.features.scheduling) {
            Log.i(TAG, "Skipping: scheduling is not part of this build")
            return Result.success()
        }

        return try {
            app.appContainer.scheduledSmsSender.send(applicationContext, id)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending scheduled SMS id=$id: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }
}

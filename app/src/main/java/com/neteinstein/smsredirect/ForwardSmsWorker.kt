package com.neteinstein.smsredirect

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class ForwardSmsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ForwardSmsWorker"
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"

        fun enqueue(context: Context, sender: String?, body: String) {
            val data = Data.Builder()
                .putString(KEY_SENDER, sender)
                .putString(KEY_BODY, body)
                .build()

            val request = OneTimeWorkRequestBuilder<ForwardSmsWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "forward_${System.currentTimeMillis()}",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    request
                )

            Log.d(TAG, "Enqueued forwarding work for sender='$sender'")
        }
    }

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER)
        val body = inputData.getString(KEY_BODY).orEmpty()

        Log.d(TAG, "doWork() — sender='$sender', bodyLength=${body.length}")

        if (body.isBlank()) {
            Log.w(TAG, "Skipping: body is blank")
            return Result.success()
        }

        return try {
            val app = applicationContext as SmsRedirectApplication
            app.appContainer.smsForwarder.forward(applicationContext, sender, body)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during SMS forwarding", e)
            Result.retry()
        }
    }
}

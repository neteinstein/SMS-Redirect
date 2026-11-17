package com.neteinstein.smsredirect

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.neteinstein.smsredirect.domain.device.PhoneActionOutcome
import com.neteinstein.smsredirect.domain.model.PhoneAction

/**
 * Runs one automation [PhoneAction]. A worker rather than the SMS receiver itself, because a
 * ring lasts up to a minute, far longer than a broadcast may run.
 *
 * Its input is the action only (kind, value, label) and the rule's name, never the message.
 * Failures are reported, not retried: a ring a few minutes late is worse than none.
 */
class PhoneActionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "PhoneActionWorker"
        private const val KEY_KIND = "kind"
        private const val KEY_VALUE = "value"
        private const val KEY_LABEL = "label"
        private const val KEY_RULE_NAME = "rule_name"

        fun enqueue(
            context: Context,
            action: PhoneAction,
            ruleName: String,
        ) {
            val request =
                OneTimeWorkRequestBuilder<PhoneActionWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_KIND to action.kind,
                            KEY_VALUE to action.value,
                            KEY_LABEL to action.label,
                            KEY_RULE_NAME to ruleName,
                        ),
                    ).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val action =
            PhoneAction.decode(
                kind = inputData.getString(KEY_KIND).orEmpty(),
                value = inputData.getString(KEY_VALUE).orEmpty(),
                label = inputData.getString(KEY_LABEL).orEmpty(),
            ) ?: return Result.failure()
        val ruleName = inputData.getString(KEY_RULE_NAME).orEmpty()

        val container = (applicationContext as SmsRedirectApplication).appContainer
        return when (val outcome = container.phoneActionExecutor.execute(action, ruleName)) {
            PhoneActionOutcome.Done -> {
                Result.success()
            }

            is PhoneActionOutcome.Failed -> {
                Log.w(TAG, "${action.kind} failed: ${outcome.reason}")
                Result.failure()
            }
        }
    }
}

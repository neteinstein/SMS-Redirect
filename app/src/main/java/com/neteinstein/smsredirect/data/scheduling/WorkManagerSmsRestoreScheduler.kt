package com.neteinstein.smsredirect.data.scheduling

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.neteinstein.smsredirect.RestoreSmsWorker
import com.neteinstein.smsredirect.domain.model.SmsRestoreFailure
import com.neteinstein.smsredirect.domain.model.SmsRestoreProgress
import com.neteinstein.smsredirect.domain.model.SmsRestoreState
import com.neteinstein.smsredirect.domain.scheduling.SmsRestoreScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Keeps WorkManager out of the ViewModels, mirroring [WorkManagerSmsBackupScheduler]. */
class WorkManagerSmsRestoreScheduler(
    private val context: Context,
) : SmsRestoreScheduler {
    override fun requestRestore() = RestoreSmsWorker.start(context)

    override fun cancelRestore() = RestoreSmsWorker.cancel(context)

    override fun observeState(): Flow<SmsRestoreState> =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWorkFlow(RestoreSmsWorker.UNIQUE_WORK_NAME)
            .map(::toState)

    /**
     * A restore is a chain of bounded runs, so the unique-work list holds every finished chunk
     * plus the one in flight. Anything still pending wins; otherwise the run that ended the chain
     * -- the one flagged finished, or the one that failed -- decides the outcome.
     */
    private fun toState(infos: List<WorkInfo>): SmsRestoreState {
        infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.let { running ->
            val total = running.progress.getInt(RestoreSmsWorker.KEY_TOTAL, 0)
            val progress =
                if (total > 0) {
                    SmsRestoreProgress(
                        processed = running.progress.getInt(RestoreSmsWorker.KEY_PROCESSED, 0),
                        total = total,
                        restored = running.progress.getInt(RestoreSmsWorker.KEY_RESTORED, 0),
                    )
                } else {
                    null
                }
            return SmsRestoreState.Running(progress)
        }
        if (infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) {
            return SmsRestoreState.Running(progress = null)
        }
        infos.firstOrNull { it.state == WorkInfo.State.FAILED }?.let { failed ->
            val reason =
                failed.outputData
                    .getString(RestoreSmsWorker.KEY_FAILURE)
                    ?.let { name -> SmsRestoreFailure.entries.firstOrNull { it.name == name } }
                    ?: SmsRestoreFailure.NETWORK
            return SmsRestoreState.Failed(reason)
        }
        infos
            .firstOrNull { it.state == WorkInfo.State.SUCCEEDED && it.outputData.getBoolean(RestoreSmsWorker.KEY_FINISHED, false) }
            ?.let { done ->
                return SmsRestoreState.Finished(
                    restored = done.outputData.getInt(RestoreSmsWorker.KEY_RESTORED, 0),
                    alreadyOnDevice = done.outputData.getInt(RestoreSmsWorker.KEY_ALREADY_ON_DEVICE, 0),
                    failed = done.outputData.getInt(RestoreSmsWorker.KEY_FAILED, 0),
                    notBackups = done.outputData.getInt(RestoreSmsWorker.KEY_NOT_BACKUPS, 0),
                )
            }
        // Nothing queued, or the user cancelled.
        return SmsRestoreState.Idle
    }
}

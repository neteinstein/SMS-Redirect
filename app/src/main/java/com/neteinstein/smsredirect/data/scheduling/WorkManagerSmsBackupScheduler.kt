package com.neteinstein.smsredirect.data.scheduling

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.neteinstein.smsredirect.BackupSmsWorker
import com.neteinstein.smsredirect.domain.model.SmsBackupProgress
import com.neteinstein.smsredirect.domain.scheduling.SmsBackupScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Keeps WorkManager out of the ViewModels, mirroring [WorkManagerScheduledSmsScheduler].
 */
class WorkManagerSmsBackupScheduler(
    private val context: Context,
) : SmsBackupScheduler {
    override fun requestBackup() = BackupSmsWorker.enqueue(context)

    override fun cancelBackup() = BackupSmsWorker.cancel(context)

    override fun observeProgress(): Flow<SmsBackupProgress?> =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWorkFlow(BackupSmsWorker.UNIQUE_WORK_NAME)
            .map { infos ->
                val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@map null
                val total = running.progress.getInt(BackupSmsWorker.KEY_TOTAL, 0)
                if (total <= 0) return@map null
                SmsBackupProgress(
                    done = running.progress.getInt(BackupSmsWorker.KEY_DONE, 0),
                    total = total,
                )
            }
}

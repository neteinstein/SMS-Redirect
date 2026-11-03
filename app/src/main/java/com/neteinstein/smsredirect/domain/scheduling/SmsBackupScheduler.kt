package com.neteinstein.smsredirect.domain.scheduling

import com.neteinstein.smsredirect.domain.model.SmsBackupProgress
import kotlinx.coroutines.flow.Flow

/**
 * Keeps WorkManager out of the domain and the ViewModels, following the existing
 * [ScheduledSmsScheduler] precedent. Lets `BackupViewModel` be tested with a fake.
 */
interface SmsBackupScheduler {
    /**
     * Enqueues a scan for anything not yet backed up, and starts watching the SMS provider so sent
     * messages are picked up too. Safe to call repeatedly; runs coalesce.
     */
    fun requestBackup()

    fun cancelBackup()

    fun observeProgress(): Flow<SmsBackupProgress?>
}

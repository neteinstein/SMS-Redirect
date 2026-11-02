package com.neteinstein.smsredirect.domain.scheduling

import com.neteinstein.smsredirect.domain.model.SmsRestoreState
import kotlinx.coroutines.flow.Flow

/** Keeps WorkManager out of the domain and the ViewModels; the restore twin of [SmsBackupScheduler]. */
interface SmsRestoreScheduler {
    /** Starts a restore from the beginning of the label, replacing any restore already queued. */
    fun requestRestore()

    fun cancelRestore()

    fun observeState(): Flow<SmsRestoreState>
}

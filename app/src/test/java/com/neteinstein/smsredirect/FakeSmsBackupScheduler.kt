package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.SmsBackupProgress
import com.neteinstein.smsredirect.domain.scheduling.SmsBackupScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSmsBackupScheduler : SmsBackupScheduler {
    var requestBackupCalls = 0
        private set
    var cancelBackupCalls = 0
        private set
    val progress = MutableStateFlow<SmsBackupProgress?>(null)

    override fun requestBackup() {
        requestBackupCalls++
    }

    override fun cancelBackup() {
        cancelBackupCalls++
    }

    override fun observeProgress(): Flow<SmsBackupProgress?> = progress
}

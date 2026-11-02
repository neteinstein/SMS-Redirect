package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.SmsRestoreState
import com.neteinstein.smsredirect.domain.scheduling.SmsRestoreScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSmsRestoreScheduler : SmsRestoreScheduler {
    var requestRestoreCalls = 0
        private set
    var cancelRestoreCalls = 0
        private set
    val state = MutableStateFlow<SmsRestoreState>(SmsRestoreState.Idle)

    override fun requestRestore() {
        requestRestoreCalls++
    }

    override fun cancelRestore() {
        cancelRestoreCalls++
    }

    override fun observeState(): Flow<SmsRestoreState> = state
}

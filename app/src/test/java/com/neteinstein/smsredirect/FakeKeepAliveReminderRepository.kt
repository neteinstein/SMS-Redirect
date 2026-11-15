package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.repository.KeepAliveReminderRepository

class FakeKeepAliveReminderRepository(
    var infoShown: Boolean = false,
) : KeepAliveReminderRepository {
    override suspend fun isUnusedAppInfoShown(): Boolean = infoShown

    override suspend fun markUnusedAppInfoShown() {
        infoShown = true
    }
}

package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppUpdateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppUpdateRepository(
    var lastCheck: Long = 0L,
    latest: AppRelease? = null,
) : AppUpdateRepository {
    val latestState = MutableStateFlow(latest)

    override val latestRelease: Flow<AppRelease?> = latestState

    override suspend fun lastCheckEpochMs(): Long = lastCheck

    override suspend fun saveCheck(
        checkedAtEpochMs: Long,
        latest: AppRelease,
    ) {
        lastCheck = checkedAtEpochMs
        latestState.value = latest
    }
}

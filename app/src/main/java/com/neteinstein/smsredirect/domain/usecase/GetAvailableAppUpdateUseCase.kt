package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppUpdateRepository
import com.neteinstein.smsredirect.domain.update.AppVersions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The stored latest release, only while it is newer than the running app. Once the update is
 * installed the running version catches up and this emits null without another network check.
 */
class GetAvailableAppUpdateUseCase(
    private val repository: AppUpdateRepository,
    private val currentVersionName: String,
) {
    operator fun invoke(): Flow<AppRelease?> =
        repository.latestRelease
            .map { release -> release?.takeIf { AppVersions.isNewer(it.versionName, currentVersionName) } }
            .distinctUntilChanged()
}
